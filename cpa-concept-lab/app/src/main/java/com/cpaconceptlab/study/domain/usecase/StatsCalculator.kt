package com.cpaconceptlab.study.domain.usecase

import com.cpaconceptlab.study.data.repository.ContentSnapshot
import com.cpaconceptlab.study.data.repository.UserSnapshot
import com.cpaconceptlab.study.domain.model.ChapterStatus
import com.cpaconceptlab.study.domain.model.MasteryLevel
import com.cpaconceptlab.study.domain.model.QuizMode
import java.time.LocalDate
import java.time.ZoneId

data class DayCount(val day: LocalDate, val solved: Int, val correct: Int)

data class ChapterStat(
    val chapterId: String,
    val code: String,
    val title: String,
    val conceptCount: Int,
    val studiedConcepts: Int,
    val questionCount: Int,
    val solvedQuestions: Int,
    val masteryRatio: Double,
    val accuracy: Double?,
    val status: ChapterStatus,
)

/** 학습 파트(재고자산·유형자산·차입원가 등) 카드 통계 */
data class PartStat(
    val partTitle: String,
    val chapterIds: List<String>,
    val conceptCount: Int,
    val studiedConcepts: Int,
    val questionCount: Int,
    val solvedQuestions: Int,
    val accuracy: Double?,
    val wrongCount: Int,
    val masteryRate: Double,
)

data class ConceptWeakness(val conceptId: String, val name: String, val wrong: Int, val attempts: Int) {
    val wrongRate: Double get() = if (attempts == 0) 0.0 else wrong.toDouble() / attempts
}

data class Overview(
    val totalConcepts: Int,
    val studiedConcepts: Int,
    val unstudiedConcepts: Int,
    val totalQuestions: Int,
    val solvedQuestions: Int,
    val solvedBasic: Int,
    val solvedAdvanced: Int,
    val totalAttempts: Int,
    val overallAccuracy: Double?,
    val basicAccuracy: Double?,
    val advancedAccuracy: Double?,
    val reviewNeededConcepts: Int,
    val dueQuestions: Int,
    val unresolvedWrong: Int,
    val studyDays: Int,
    val streak: Int,
    /** 오늘 제출 횟수(같은 문항 반복 포함) */
    val todaySolved: Int,
    val todayCorrect: Int,
    /** v1.0.7: 오늘 실제로 제출한 고유 문항 수(오늘의 목표 집계 기준 — 같은 문항 반복 제출로 부풀지 않는다) */
    val todayUniqueSolved: Int = 0,
    val last7: List<DayCount>,
    val last7Total: Int,
    val last30Total: Int,
    val chapters: List<ChapterStat>,
    val mostWrong: List<ConceptWeakness>,
    val weakest: List<ConceptWeakness>,
    val masteryByConcept: Map<String, MasteryLevel>,
    val masteryCounts: Map<MasteryLevel, Int>,
)

/**
 * 통계 계산기(순수 함수). 분모는 항상 현재 활성화된 개념·문항이며,
 * 비활성 문항이나 현재 콘텐츠에 없는 ID 의 학습기록은 집계에서 제외된다(기록 자체는 보존).
 */
object StatsCalculator {

    fun masteryMap(content: ContentSnapshot, user: UserSnapshot, now: Long): Map<String, MasteryLevel> =
        content.concepts.associate { c ->
            val qids = content.questionsByPrimary[c.id].orEmpty().map { it.id }
            val viewed = (user.conceptProgressMap[c.id]?.viewCount ?: 0) > 0
            c.id to MasteryCalculator.compute(qids, user.progressMap, viewed, now)
        }

    fun overview(content: ContentSnapshot, user: UserSnapshot, now: Long, zone: ZoneId = ZoneId.systemDefault()): Overview {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val activeIds = content.questions.map { it.id }.toSet()
        val rows = user.questionProgress.filter { it.questionId in activeIds }
        val basicRows = rows.filter { it.mode == QuizMode.BASIC.name }
        val advRows = rows.filter { it.mode == QuizMode.ADVANCED.name }

        fun acc(list: List<com.cpaconceptlab.study.data.local.QuestionProgressEntity>): Double? {
            val t = list.sumOf { it.totalAttempts }
            return if (t == 0) null else list.sumOf { it.correctAttempts }.toDouble() / t
        }

        val mastery = masteryMap(content, user, now)
        val studied = mastery.count { it.value != MasteryLevel.NOT_STARTED }
        val days = user.studyDays.associateBy { it.epochDay }
        val last7 = (6 downTo 0).map { off ->
            val d = today.minusDays(off.toLong())
            val e = days[d.toEpochDay()]
            DayCount(d, e?.solvedCount ?: 0, e?.correctCount ?: 0)
        }
        val last30 = (0..29).sumOf { days[today.minusDays(it.toLong()).toEpochDay()]?.solvedCount ?: 0 }

        // 연속 학습일: 오늘 또는 어제까지 이어진 연속 일수
        var streak = 0
        var cursor = if ((days[today.toEpochDay()]?.solvedCount ?: 0) > 0) today else today.minusDays(1)
        while ((days[cursor.toEpochDay()]?.solvedCount ?: 0) > 0) { streak++; cursor = cursor.minusDays(1) }

        val chapterStats = content.chapters.sortedBy { it.sortOrder }.map { ch ->
            val cs = content.conceptsOfChapter(ch.id)
            val qs = content.questions.filter { it.chapterId == ch.id }
            val qids = qs.map { it.id }.toSet()
            val chRows = rows.filter { it.questionId in qids }
            val levels = cs.map { mastery[it.id] ?: MasteryLevel.NOT_STARTED }
            val ratio = if (levels.isEmpty()) 0.0 else levels.sumOf { it.score } / levels.size
            val status = when {
                chRows.isEmpty() -> ChapterStatus.NOT_STARTED
                levels.any { it == MasteryLevel.REVIEW_NEEDED } -> ChapterStatus.REVIEW
                ratio >= 0.8 && levels.none { it == MasteryLevel.NOT_STARTED } -> ChapterStatus.MASTERED
                else -> ChapterStatus.LEARNING
            }
            ChapterStat(
                chapterId = ch.id, code = ch.code, title = ch.title,
                conceptCount = cs.size,
                studiedConcepts = levels.count { it != MasteryLevel.NOT_STARTED },
                questionCount = qs.size,
                solvedQuestions = chRows.map { it.questionId }.distinct().size,
                masteryRatio = ratio,
                accuracy = acc(chRows),
                status = status,
            )
        }

        val weakness = content.concepts.mapNotNull { c ->
            val qids = content.questionsByPrimary[c.id].orEmpty().map { it.id }.toSet()
            val r = rows.filter { it.questionId in qids }
            val attempts = r.sumOf { it.totalAttempts }
            if (attempts == 0) null else ConceptWeakness(c.id, c.koreanName, r.sumOf { it.wrongAttempts }, attempts)
        }

        val todayRow = days[today.toEpochDay()]
        return Overview(
            totalConcepts = content.concepts.size,
            studiedConcepts = studied,
            unstudiedConcepts = content.concepts.size - studied,
            totalQuestions = content.questions.size,
            solvedQuestions = rows.map { it.questionId }.distinct().size,
            solvedBasic = basicRows.size,
            solvedAdvanced = advRows.size,
            totalAttempts = rows.sumOf { it.totalAttempts },
            overallAccuracy = acc(rows),
            basicAccuracy = acc(basicRows),
            advancedAccuracy = acc(advRows),
            reviewNeededConcepts = mastery.count { it.value == MasteryLevel.REVIEW_NEEDED },
            dueQuestions = rows.count { it.nextReviewAt < endOfToday },
            unresolvedWrong = user.wrongNotes.count { !it.isResolved && it.questionId in activeIds },
            studyDays = user.studyDays.count { it.solvedCount > 0 },
            streak = streak,
            todaySolved = todayRow?.solvedCount ?: 0,
            todayCorrect = todayRow?.correctCount ?: 0,
            todayUniqueSolved = user.questionProgress.filter { it.lastAnsweredAt >= startOfToday && it.lastAnsweredAt < endOfToday }
                .map { it.questionId }.distinct().size,
            last7 = last7,
            last7Total = last7.sumOf { it.solved },
            last30Total = last30,
            chapters = chapterStats,
            mostWrong = weakness.filter { it.wrong > 0 }.sortedByDescending { it.wrong }.take(10),
            weakest = weakness.filter { it.attempts >= 2 && it.wrong > 0 }.sortedByDescending { it.wrongRate }.take(10),
            masteryByConcept = mastery,
            masteryCounts = MasteryLevel.entries.associateWith { lvl -> mastery.count { it.value == lvl } },
        )
    }

    /**
     * 파트별 통계. 정답률은 두 모드 합산 시도 기준, 오답 수는 미해결 오답노트 문항 수,
     * 숙달률은 '기본문제 숙달' 이상 단계에 도달한 개념의 비율이다.
     */
    fun partStats(content: ContentSnapshot, user: UserSnapshot, mastery: Map<String, MasteryLevel>): List<PartStat> {
        val activeIds = content.questions.map { it.id }.toSet()
        val mastered = setOf(MasteryLevel.BASIC_MASTERED, MasteryLevel.ADVANCED_MASTERED, MasteryLevel.FULL_MASTERY)
        return content.chapters.sortedBy { it.sortOrder }.groupBy { it.partTitle.ifBlank { "기타" } }.map { (part, chs) ->
            val chIds = chs.map { it.id }.toSet()
            val cs = content.concepts.filter { it.chapterId in chIds }
            val qs = content.questions.filter { it.chapterId in chIds }
            val qids = qs.map { it.id }.toSet()
            val rows = user.questionProgress.filter { it.questionId in qids && it.questionId in activeIds }
            val total = rows.sumOf { it.totalAttempts }
            PartStat(
                partTitle = part,
                chapterIds = chs.map { it.id },
                conceptCount = cs.size,
                studiedConcepts = cs.count { (mastery[it.id] ?: MasteryLevel.NOT_STARTED) != MasteryLevel.NOT_STARTED },
                questionCount = qs.size,
                solvedQuestions = rows.map { it.questionId }.distinct().size,
                accuracy = if (total == 0) null else rows.sumOf { it.correctAttempts }.toDouble() / total,
                wrongCount = user.wrongNotes.count { !it.isResolved && it.questionId in qids },
                masteryRate = if (cs.isEmpty()) 0.0 else cs.count { mastery[it.id] in mastered }.toDouble() / cs.size,
            )
        }
    }
}

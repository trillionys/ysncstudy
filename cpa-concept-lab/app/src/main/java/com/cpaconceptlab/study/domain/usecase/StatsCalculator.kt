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

data class ConceptWeakness(val conceptId: String, val name: String, val wrong: Int, val attempts: Int) {
    val wrongRate: Double get() = if (attempts == 0) 0.0 else wrong.toDouble() / attempts
}

data class ExamScopeStat(
    val number: String,
    val title: String,
    val conceptIds: List<String>,
    val studiedConcepts: Int,
    val masteredConcepts: Int,
    val questionCount: Int,
    val solvedQuestions: Int,
    val accuracy: Double?,
)

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
    val todaySolved: Int,
    val todayCorrect: Int,
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

    fun examScopes(content: ContentSnapshot, user: UserSnapshot, mastery: Map<String, MasteryLevel>): List<ExamScopeStat> {
        val activeIds = content.questions.map { it.id }.toSet()
        return content.examScopes.sortedBy { it.number }.map { s ->
            val cids = s.conceptIds.filter { content.conceptById[it]?.isActive == true }
            val qs = content.questions.filter { q -> q.conceptIds.any { it in cids } }
            val qids = qs.map { it.id }.toSet()
            val rows = user.questionProgress.filter { it.questionId in qids && it.questionId in activeIds }
            val total = rows.sumOf { it.totalAttempts }
            ExamScopeStat(
                number = s.number,
                title = s.title,
                conceptIds = cids,
                studiedConcepts = cids.count { (mastery[it] ?: MasteryLevel.NOT_STARTED) != MasteryLevel.NOT_STARTED },
                masteredConcepts = cids.count {
                    mastery[it] in setOf(MasteryLevel.BASIC_MASTERED, MasteryLevel.ADVANCED_MASTERED, MasteryLevel.FULL_MASTERY)
                },
                questionCount = qs.size,
                solvedQuestions = rows.map { it.questionId }.distinct().size,
                accuracy = if (total == 0) null else rows.sumOf { it.correctAttempts }.toDouble() / total,
            )
        }
    }
}

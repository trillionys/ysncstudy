package com.cpaconceptlab.study.domain.usecase

import com.cpaconceptlab.study.data.local.ConceptProgressEntity
import com.cpaconceptlab.study.data.repository.ContentSnapshot
import com.cpaconceptlab.study.data.repository.UserSnapshot
import com.cpaconceptlab.study.domain.model.MasteryLevel

/**
 * 공식 시험과목(회계학·경영학·경제원론·기업법·세법개론)별 학습현황.
 *
 * 세 지표를 구별한다.
 *  - 개념 진도율 = 학습 완료 개념 수 / 활성 개념 수
 *  - 문제 정답률 = 정답 제출 수 / 유효 제출 수(기본·심화 합산)
 *  - 문제 학습률 = 1회 이상 푼 고유 문항 수 / 활성 문항 수
 * 그리고 사용자 진도와 별개로 콘텐츠 구축률(출제범위 마스터 목차 중 실제 학습 가능한 항목 비율)을 둔다.
 *
 * 상위 과목 수치는 하위 팩의 평균이 아니라 실제 개념·문항을 합산(개념 ID 기준 중복 제거)해서 계산한다.
 * 분모가 0이면 ratio 는 null 이며 화면에는 '콘텐츠 없음'으로 표시한다.
 */
object ExamSubjectProgress {

    data class ExamSubject(val id: String, val name: String, val points: Int)

    val EXAM_SUBJECTS = listOf(
        ExamSubject("ACC", "회계학", 150),
        ExamSubject("BUS", "경영학", 80),
        ExamSubject("ECO", "경제원론", 80),
        ExamSubject("LAW", "기업법", 100),
        ExamSubject("TAX", "세법개론", 100),
    )

    /** examSubject 컬럼이 비어 있는 이전 데이터용 기본 매핑 */
    private val FALLBACK = mapOf(
        "FA" to "ACC", "IA" to "ACC", "AA" to "ACC", "CM" to "ACC", "GA" to "ACC",
        "FM" to "BUS", "HR" to "BUS", "EC" to "ECO", "CPA-LAW" to "LAW", "TX" to "TAX",
    )

    /** 팩 안에서 파트(partTitle)별로 세부 진도를 보여줄 과목 */
    private val PART_DETAIL = setOf("CPA-LAW", "EC", "TX")

    data class Stat(
        val id: String,
        val name: String,
        val completedConcepts: Int,
        val totalConcepts: Int,
        val solvedQuestions: Int,
        val totalQuestions: Int,
        val correct: Int,
        val attempts: Int,
        val reviewNeeded: Int,
        val curriculumCovered: Int,
        val curriculumTotal: Int,
        val basicCorrect: Int = 0,
        val basicAttempts: Int = 0,
        val advancedCorrect: Int = 0,
        val advancedAttempts: Int = 0,
        val calcCorrect: Int = 0,
        val calcAttempts: Int = 0,
        /** 가장 최근 풀이가 오답인 문항이 하나라도 있는 개념 수 */
        val wrongConcepts: Int = 0,
    ) {
        val basicAccuracy: Double? get() = if (basicAttempts == 0) null else basicCorrect.toDouble() / basicAttempts
        val advancedAccuracy: Double? get() = if (advancedAttempts == 0) null else advancedCorrect.toDouble() / advancedAttempts
        val calcAccuracy: Double? get() = if (calcAttempts == 0) null else calcCorrect.toDouble() / calcAttempts
        val conceptRatio: Double? get() = if (totalConcepts == 0) null else completedConcepts.toDouble() / totalConcepts
        val accuracy: Double? get() = if (attempts == 0) null else correct.toDouble() / attempts
        val questionRatio: Double? get() = if (totalQuestions == 0) null else solvedQuestions.toDouble() / totalQuestions
        val buildRatio: Double? get() = if (curriculumTotal == 0) null else curriculumCovered.toDouble() / curriculumTotal
        val hasContent: Boolean get() = totalConcepts > 0
        val complete: Boolean get() = totalConcepts > 0 && completedConcepts == totalConcepts
    }

    data class PackStat(val stat: Stat, val active: Boolean, val parts: List<Stat>)

    data class SubjectDetail(val subject: Stat, val packs: List<PackStat>)

    fun examSubjectOf(subjectId: String, stored: String): String = stored.ifBlank { FALLBACK[subjectId].orEmpty() }

    /**
     * 개념 학습 완료: 개념 설명 확인 + 기본 문제 정답 + 심화 문제 1회 이상 정답.
     * 기존(1.0.3 이하) 기록 호환: 심화 숙달 이상으로 판정된 개념은 기본·심화 정답 이력이 있으므로 완료로 본다.
     */
    fun isCompleted(cp: ConceptProgressEntity?): Boolean {
        if (cp == null) return false
        val legacy = cp.masteryLevel == MasteryLevel.ADVANCED_MASTERED.name || cp.masteryLevel == MasteryLevel.FULL_MASTERY.name
        return (cp.viewCount > 0 && cp.basicCorrectCount > 0 && cp.advancedCorrectCount > 0) || legacy
    }

    private fun stat(id: String, name: String, conceptIds: Set<String>, content: ContentSnapshot, user: UserSnapshot,
                     curriculum: Pair<Int, Int>): Stat {
        val concepts = conceptIds.filter { content.conceptById[it]?.isActive == true }
        val qs = content.questions.filter { q -> q.primaryConceptId in conceptIds }
        val rows = qs.flatMap { q -> listOfNotNull(user.progressMap[q.id to "BASIC"], user.progressMap[q.id to "ADVANCED"]) }
        val solved = qs.count { q -> user.progressMap[q.id to "BASIC"] != null || user.progressMap[q.id to "ADVANCED"] != null }
        val basic = qs.mapNotNull { user.progressMap[it.id to "BASIC"] }
        val adv = qs.mapNotNull { user.progressMap[it.id to "ADVANCED"] }
        val calc = qs.filter { it.calcFormulaId != null }
            .flatMap { q -> listOfNotNull(user.progressMap[q.id to "BASIC"], user.progressMap[q.id to "ADVANCED"]) }
        val wrong = qs.filter { q ->
            listOfNotNull(user.progressMap[q.id to "BASIC"], user.progressMap[q.id to "ADVANCED"])
                .maxByOrNull { it.lastAnsweredAt }?.lastCorrect == false
        }.map { it.primaryConceptId }.toSet()
        return Stat(
            id = id, name = name,
            completedConcepts = concepts.count { isCompleted(user.conceptProgressMap[it]) },
            totalConcepts = concepts.size,
            solvedQuestions = solved, totalQuestions = qs.size,
            correct = rows.sumOf { it.correctAttempts }, attempts = rows.sumOf { it.totalAttempts },
            reviewNeeded = concepts.count { user.conceptProgressMap[it]?.masteryLevel == MasteryLevel.REVIEW_NEEDED.name },
            curriculumCovered = curriculum.first, curriculumTotal = curriculum.second,
            basicCorrect = basic.sumOf { it.correctAttempts }, basicAttempts = basic.sumOf { it.totalAttempts },
            advancedCorrect = adv.sumOf { it.correctAttempts }, advancedAttempts = adv.sumOf { it.totalAttempts },
            calcCorrect = calc.sumOf { it.correctAttempts }, calcAttempts = calc.sumOf { it.totalAttempts },
            wrongConcepts = concepts.count { it in wrong },
        )
    }

    /** 팩에 속한 개념: 자기 과목 개념 + 출제범위 목차가 연결한 다른 팩의 개념(예: 중급회계 ← 재무회계 재고자산) */
    private fun packConcepts(subjectId: String, content: ContentSnapshot): Set<String> =
        (content.concepts.filter { it.subjectId == subjectId }.map { it.id } +
            content.curriculumItems.filter { it.subjectId == subjectId }.flatMap { it.conceptIds }).toSet()

    private fun curriculumOf(subjectIds: Collection<String>, content: ContentSnapshot): Pair<Int, Int> {
        val items = content.curriculumItems.filter { it.subjectId in subjectIds }
        return items.count { it.covered } to items.size
    }

    private fun packsOf(examId: String, content: ContentSnapshot) =
        content.subjects.filter { examSubjectOf(it.id, it.examSubject) == examId }.sortedBy { it.sortOrder }

    fun summary(content: ContentSnapshot, user: UserSnapshot): List<Stat> = EXAM_SUBJECTS.map { ex ->
        val packs = packsOf(ex.id, content)
        val ids = packs.flatMap { packConcepts(it.id, content) }.toSet()
        stat(ex.id, ex.name, ids, content, user, curriculumOf(packs.map { it.id }, content))
    }

    fun detail(examId: String, content: ContentSnapshot, user: UserSnapshot): SubjectDetail {
        val ex = EXAM_SUBJECTS.first { it.id == examId }
        val packs = packsOf(examId, content)
        val all = packs.flatMap { packConcepts(it.id, content) }.toSet()
        val packStats = packs.map { sub ->
            val ids = packConcepts(sub.id, content)
            val parts = if (sub.id in PART_DETAIL) {
                content.chaptersOf(sub.id).groupBy { it.partTitle.ifBlank { it.title } }.map { (part, chs) ->
                    val chIds = chs.map { it.id }.toSet()
                    stat("${sub.id}|$part", part, content.concepts.filter { it.chapterId in chIds }.map { it.id }.toSet(), content, user, 0 to 0)
                }
            } else emptyList()
            PackStat(stat(sub.id, sub.name, ids, content, user, curriculumOf(listOf(sub.id), content)), sub.isActive && ids.isNotEmpty(), parts)
        }
        return SubjectDetail(stat(ex.id, ex.name, all, content, user, curriculumOf(packs.map { it.id }, content)), packStats)
    }
}

package com.cpaconceptlab.study.domain.usecase

import com.cpaconceptlab.study.data.local.QuestionEntity
import com.cpaconceptlab.study.data.local.QuestionProgressEntity
import com.cpaconceptlab.study.data.local.WrongNoteEntity
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.model.ScopeType
import kotlin.random.Random

/**
 * 커버리지 인식(Coverage-aware) 출제 엔진.
 *
 * 단순 무작위 추출은 일부 개념만 반복되고 일부 개념은 한 번도 출제되지 않는 문제가 있다.
 * 이 엔진은 개념 단위로 우선순위를 매긴 뒤, 대단원별 비중을 맞추며 개념당 1문항씩 순환 출제한다.
 *
 * 개념 우선순위(앞쪽이 먼저):
 *  1. 해당 모드에서 아직 한 번도 출제되지 않은 개념
 *  2. 학습 진도(맞힌 문항 비율)가 낮은 개념
 *  3. 틀린 횟수가 많은 개념
 *  4. 복습 예정일이 지난(오래 지난) 개념
 *  5. 최근에 학습하지 않은 개념
 *  6. 이미 충분히 숙달한 개념(가장 뒤)
 *
 * 반복 제한: 한 세션에서 후보 개념을 모두 한 번씩 출제하기 전에는 같은 개념을 다시 출제하지 않는다.
 * 세션 간에도 '미출제 개념 우선' 규칙 때문에 전체 범위의 모든 개념이 한 번씩 출제된 뒤에야 반복된다.
 * 오답 복습(WRONG)은 오답 개념을 우선하고, 복습 예정(DUE)은 연체 기간이 긴 순서로 출제한다.
 */
class QuestionSelector(private val random: Random = Random.Default) {

    data class ConceptPriority(
        val conceptId: String,
        val chapterId: String,
        val neverSeen: Boolean,
        val progressRatio: Double,
        val wrongCount: Int,
        val overdueMs: Long,
        val lastStudiedAt: Long,
        val mastered: Boolean,
        val tieBreak: Double,
    )

    fun select(
        candidates: List<QuestionEntity>,
        mode: QuizMode,
        scopeType: ScopeType,
        size: Int,
        progress: Map<Pair<String, String>, QuestionProgressEntity>,
        wrongNotes: List<WrongNoteEntity>,
        now: Long,
        recentlyAnswered: Set<String> = emptySet(),
    ): List<QuestionEntity> {
        if (candidates.isEmpty() || size <= 0) return emptyList()
        return when (scopeType) {
            ScopeType.WRONG -> selectWrong(candidates, mode, size, wrongNotes)
            ScopeType.DUE -> selectDue(candidates, mode, size, progress, now)
            ScopeType.MOCK -> selectMock(candidates, size)
            else -> selectBalanced(candidates, mode, size, progress, now, recentlyAnswered)
        }
    }

    /** 개념 우선순위 계산(테스트에서도 사용). */
    fun conceptPriorities(
        candidates: List<QuestionEntity>,
        mode: QuizMode,
        progress: Map<Pair<String, String>, QuestionProgressEntity>,
        now: Long,
    ): List<ConceptPriority> {
        val byConcept = candidates.groupBy { it.primaryConceptId }
        return byConcept.map { (conceptId, qs) ->
            val rows = qs.mapNotNull { progress[it.id to mode.name] }
            val correctOnce = rows.count { it.correctAttempts > 0 }
            val overdue = rows.filter { it.nextReviewAt <= now }.maxOfOrNull { now - it.nextReviewAt } ?: -1L
            ConceptPriority(
                conceptId = conceptId,
                chapterId = qs.first().chapterId,
                neverSeen = rows.isEmpty(),
                progressRatio = correctOnce.toDouble() / qs.size,
                wrongCount = rows.sumOf { it.wrongAttempts },
                overdueMs = overdue,
                lastStudiedAt = rows.maxOfOrNull { it.lastAnsweredAt } ?: 0L,
                mastered = rows.size == qs.size && rows.all { it.lastCorrect && it.stage >= 2 },
                tieBreak = random.nextDouble(),
            )
        }.sortedWith(PRIORITY_ORDER)
    }

    private fun selectBalanced(
        candidates: List<QuestionEntity>,
        mode: QuizMode,
        size: Int,
        progress: Map<Pair<String, String>, QuestionProgressEntity>,
        now: Long,
        recentlyAnswered: Set<String>,
    ): List<QuestionEntity> {
        val byConcept = candidates.groupBy { it.primaryConceptId }
        val allPriorities = conceptPriorities(candidates, mode, progress, now)
        val result = mutableListOf<QuestionEntity>()
        val usedQuestions = mutableSetOf<String>()
        // 우선순위 1(미출제 개념)을 모두 소진하기 전에는 이미 출제된 개념을 섞지 않는다.
        // 각 단계(tier) 안에서 대단원 비중을 맞춘다.
        val tiers = listOf(allPriorities.filter { it.neverSeen }, allPriorities.filter { !it.neverSeen }).filter { it.isNotEmpty() }
        for (tier in tiers) {
            if (result.size >= size) break
            fillBalanced(tier, byConcept, mode, size, progress, recentlyAnswered, result, usedQuestions, singleRound = tier !== tiers.last())
        }
        return result
    }

    private fun fillBalanced(
        priorities: List<ConceptPriority>,
        byConcept: Map<String, List<QuestionEntity>>,
        mode: QuizMode,
        size: Int,
        progress: Map<Pair<String, String>, QuestionProgressEntity>,
        recentlyAnswered: Set<String>,
        result: MutableList<QuestionEntity>,
        usedQuestions: MutableSet<String>,
        singleRound: Boolean,
    ) {
        val totalConcepts = priorities.size
        // 대단원별 목표 비중 = 그 단원의 후보 개념 수 비율
        val chapterShare = priorities.groupingBy { it.chapterId }.eachCount()
            .mapValues { it.value.toDouble() / totalConcepts }
        var rounds = 0
        while (result.size < size && !(singleRound && rounds >= 1)) {
            rounds++
            // 한 '라운드' = 각 개념을 최대 1회 출제
            val queues = priorities.groupBy { it.chapterId }
                .mapValues { (_, list) -> ArrayDeque(list.filter { p -> byConcept[p.conceptId]!!.any { it.id !in usedQuestions } }) }
                .filterValues { it.isNotEmpty() }
                .toMutableMap()
            if (queues.isEmpty()) break
            val pickedPerChapter = mutableMapOf<String, Int>()
            var pickedInRound = 0
            while (result.size < size && queues.isNotEmpty()) {
                // 목표 비중 대비 가장 덜 뽑힌 대단원을 선택(동률이면 우선순위가 높은 개념을 가진 단원)
                val chapter = queues.keys.minWith(
                    compareBy<String> { ch ->
                        (pickedPerChapter[ch] ?: 0) / (chapterShare[ch] ?: 1.0)
                    }.thenComparator { a, b -> PRIORITY_ORDER.compare(queues[a]!!.first(), queues[b]!!.first()) },
                )
                val queue = queues[chapter]!!
                val concept = queue.removeFirst()
                if (queue.isEmpty()) queues.remove(chapter)
                val q = pickQuestion(byConcept[concept.conceptId]!!, mode, progress, usedQuestions, recentlyAnswered) ?: continue
                result += q
                usedQuestions += q.id
                pickedPerChapter[chapter] = (pickedPerChapter[chapter] ?: 0) + 1
                pickedInRound++
            }
            if (pickedInRound == 0) break
        }
    }

    /** 개념 내 문항 선택: 미출제 → 오답 많은 문항 → 오래전에 푼 문항. 최근 푼 문항은 뒤로 미룬다. */
    private fun pickQuestion(
        questions: List<QuestionEntity>,
        mode: QuizMode,
        progress: Map<Pair<String, String>, QuestionProgressEntity>,
        used: Set<String>,
        recentlyAnswered: Set<String>,
    ): QuestionEntity? = questions.filter { it.id !in used }
        .sortedWith(
            compareBy<QuestionEntity> { it.id in recentlyAnswered }
                .thenBy { progress[it.id to mode.name] != null }
                .thenByDescending { progress[it.id to mode.name]?.wrongAttempts ?: 0 }
                .thenBy { progress[it.id to mode.name]?.lastAnsweredAt ?: 0L }
                .thenBy { random.nextDouble() },
        ).firstOrNull()

    private fun selectWrong(
        candidates: List<QuestionEntity>,
        mode: QuizMode,
        size: Int,
        wrongNotes: List<WrongNoteEntity>,
    ): List<QuestionEntity> {
        val notes = wrongNotes.filter { !it.isResolved && it.mode == mode.name }.associateBy { it.questionId }
        return candidates.filter { it.id in notes }
            .sortedWith(
                compareByDescending<QuestionEntity> { notes[it.id]!!.wrongCount }
                    .thenBy { notes[it.id]!!.lastWrongAt },
            ).take(size)
    }

    private fun selectDue(
        candidates: List<QuestionEntity>,
        mode: QuizMode,
        size: Int,
        progress: Map<Pair<String, String>, QuestionProgressEntity>,
        now: Long,
    ): List<QuestionEntity> = candidates
        .filter { (progress[it.id to mode.name]?.nextReviewAt ?: Long.MAX_VALUE) <= now }
        .sortedBy { progress[it.id to mode.name]!!.nextReviewAt }
        .take(size)

    /** 랜덤 모의테스트: 대단원 비중을 유지하며 무작위 추출(같은 개념 중복 최소화). */
    private fun selectMock(candidates: List<QuestionEntity>, size: Int): List<QuestionEntity> {
        val shuffled = candidates.shuffled(random)
        val firstPerConcept = shuffled.distinctBy { it.primaryConceptId }
        val byChapter = firstPerConcept.groupBy { it.chapterId }.mapValues { ArrayDeque(it.value) }.toMutableMap()
        val total = firstPerConcept.size.toDouble()
        val share = byChapter.mapValues { it.value.size / total }
        val picked = mutableListOf<QuestionEntity>()
        val count = mutableMapOf<String, Int>()
        while (picked.size < size && byChapter.isNotEmpty()) {
            val ch = byChapter.keys.minBy { (count[it] ?: 0) / share.getValue(it) + random.nextDouble() * 1e-6 }
            val dq = byChapter.getValue(ch)
            picked += dq.removeFirst()
            count[ch] = (count[ch] ?: 0) + 1
            if (dq.isEmpty()) byChapter.remove(ch)
        }
        if (picked.size < size) {
            picked += shuffled.filter { it !in picked }.take(size - picked.size)
        }
        return picked.shuffled(random)
    }

    companion object {
        val PRIORITY_ORDER: Comparator<ConceptPriority> =
            compareByDescending<ConceptPriority> { it.neverSeen }
                .thenBy { it.mastered }
                .thenBy { it.progressRatio }
                .thenByDescending { it.wrongCount }
                .thenByDescending { it.overdueMs }
                .thenBy { it.lastStudiedAt }
                .thenBy { it.tieBreak }
    }
}

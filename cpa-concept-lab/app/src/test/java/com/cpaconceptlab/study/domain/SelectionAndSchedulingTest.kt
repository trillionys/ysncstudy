package com.cpaconceptlab.study.domain

import com.cpaconceptlab.study.TestContent
import com.cpaconceptlab.study.data.local.QuestionProgressEntity
import com.cpaconceptlab.study.data.local.WrongNoteEntity
import com.cpaconceptlab.study.domain.model.MasteryLevel
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.model.ScopeType
import com.cpaconceptlab.study.domain.model.StandardStatus
import com.cpaconceptlab.study.domain.model.StandardStatusResolver
import com.cpaconceptlab.study.domain.usecase.MasteryCalculator
import com.cpaconceptlab.study.domain.usecase.QuestionSelector
import com.cpaconceptlab.study.domain.usecase.ReviewScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.random.Random

class QuestionSelectorTest {
    private val content = TestContent.snapshot
    private val questions = content.questions
    private val conceptCount = questions.map { it.primaryConceptId }.distinct().size
    private val day = TimeUnit.DAYS.toMillis(1)

    private fun progressFor(qid: String, correct: Boolean, at: Long, stage: Int = if (correct) 1 else 0) =
        QuestionProgressEntity(qid, QuizMode.BASIC.name, at, 1, if (correct) 1 else 0, if (correct) 0 else 1,
            if (correct) 1 else 0, at, correct, 1000, stage, at + day, 0.5)

    // 전체 범위 한 세션에서 모든 개념이 정확히 한 번씩 출제
    @Test fun fullScopeSessionCoversEveryConceptOnce() {
        val sel = QuestionSelector(Random(1)).select(questions, QuizMode.BASIC, ScopeType.ALL, conceptCount, emptyMap(), emptyList(), 0L)
        assertEquals(conceptCount, sel.size)
        assertEquals(conceptCount, sel.map { it.primaryConceptId }.toSet().size)
    }

    // 여러 세션에 걸쳐 모든 개념을 한 번 이상 학습하기 전에는 반복 없음
    @Test fun noRepeatsUntilAllConceptsCoveredAcrossSessions() {
        val progress = mutableMapOf<Pair<String, String>, QuestionProgressEntity>()
        val seenConcepts = mutableListOf<String>()
        var now = 1_000_000L
        val sessions = (conceptCount + 19) / 20
        repeat(sessions) {
            val sel = QuestionSelector(Random(it)).select(questions, QuizMode.BASIC, ScopeType.ALL, 20, progress, emptyList(), now)
            sel.forEach { q -> progress[q.id to QuizMode.BASIC.name] = progressFor(q.id, correct = true, at = now); now += 1000 }
            seenConcepts += sel.map { it.primaryConceptId }
        }
        val firstRound = seenConcepts.take(conceptCount)
        assertEquals("첫 순환에서 중복 출제가 있으면 안 됨", conceptCount, firstRound.toSet().size)
    }

    // 대단원별 출제 비중이 개념 수 비율에 맞게 균등
    @Test fun chapterDistributionIsProportional() {
        val size = 60
        val sel = QuestionSelector(Random(7)).select(questions, QuizMode.BASIC, ScopeType.ALL, size, emptyMap(), emptyList(), 0L)
        val perChapterConcepts = questions.groupBy { it.chapterId }.mapValues { e -> e.value.map { it.primaryConceptId }.distinct().size }
        val picked = sel.groupingBy { it.chapterId }.eachCount()
        perChapterConcepts.forEach { (ch, n) ->
            val expected = size.toDouble() * n / conceptCount
            val actual = picked[ch] ?: 0
            assertTrue("$ch 기대 ${"%.1f".format(expected)} 실제 $actual", abs(actual - expected) <= 1.5)
        }
    }

    // 우선순위 - 미출제 개념 먼저, 숙달 개념은 마지막
    @Test fun neverSeenFirstMasteredLast() {
        val now = 100 * day
        val progress = mutableMapOf<Pair<String, String>, QuestionProgressEntity>()
        // A 장 개념은 모두 숙달 상태로 만든다
        questions.filter { it.chapterId == "FA-A" }.forEach { progress[it.id to "BASIC"] = progressFor(it.id, true, now - day, stage = 4).copy(nextReviewAt = now + 30 * day) }
        val pr = QuestionSelector(Random(3)).conceptPriorities(questions, QuizMode.BASIC, progress, now)
        val firstMastered = pr.indexOfFirst { it.chapterId == "FA-A" }
        assertTrue(pr.take(firstMastered).all { it.neverSeen })
        assertTrue(pr.drop(firstMastered).all { it.chapterId == "FA-A" })
    }

    // 우선순위 - 오답 많은 개념과 복습일이 지난 개념이 앞선다
    @Test fun moreWrongAndOverdueConceptsComeFirst() {
        val now = 100 * day
        val all = questions.filter { it.chapterId == "FA-D" }
        val progress = all.associate { (it.id to "BASIC") to progressFor(it.id, true, now - 10 * day, stage = 3).copy(nextReviewAt = now + 5 * day) }.toMutableMap()
        val wrongQ = all.first { it.primaryConceptId == "D07" }
        progress[wrongQ.id to "BASIC"] = progressFor(wrongQ.id, false, now - day).copy(wrongAttempts = 5, totalAttempts = 6, correctAttempts = 1)
        val dueQ = all.first { it.primaryConceptId == "D25" }
        progress[dueQ.id to "BASIC"] = progress[dueQ.id to "BASIC"]!!.copy(nextReviewAt = now - 3 * day, lastCorrect = true, stage = 1)
        val pr = QuestionSelector(Random(5)).conceptPriorities(all, QuizMode.BASIC, progress, now).map { it.conceptId }
        assertTrue(pr.indexOf("D07") < pr.indexOf("D01"))
        assertTrue(pr.indexOf("D25") < pr.indexOf("D01"))
    }

    // 오답 복습 모드는 미해결 오답만 오답 횟수 순으로
    @Test fun wrongReviewUsesUnresolvedNotesByCount() {
        val qs = questions.take(10)
        val notes = listOf(
            WrongNoteEntity(qs[0].id, "BASIC", "x", 1, 1, 10, false, null),
            WrongNoteEntity(qs[1].id, "BASIC", "x", 4, 1, 20, false, null),
            WrongNoteEntity(qs[2].id, "BASIC", "x", 9, 1, 30, true, 40),     // 해결됨 → 제외
            WrongNoteEntity(qs[3].id, "ADVANCED", "x", 7, 1, 30, false, null), // 다른 모드 → 제외
        )
        val sel = QuestionSelector().select(questions, QuizMode.BASIC, ScopeType.WRONG, 10, emptyMap(), notes, 0)
        assertEquals(listOf(qs[1].id, qs[0].id), sel.map { it.id })
    }

    // 복습 예정 모드는 예정일이 지난 문항만
    @Test fun dueModeOnlyOverdueQuestions() {
        val now = 50 * day
        val p = mapOf(
            (questions[0].id to "BASIC") to progressFor(questions[0].id, true, 0).copy(nextReviewAt = now - day),
            (questions[1].id to "BASIC") to progressFor(questions[1].id, true, 0).copy(nextReviewAt = now + day),
        )
        val sel = QuestionSelector().select(questions, QuizMode.BASIC, ScopeType.DUE, 10, p, emptyList(), now)
        assertEquals(listOf(questions[0].id), sel.map { it.id })
    }

    // 랜덤 모의테스트는 요청 수만큼 서로 다른 문항
    @Test fun mockTestReturnsDistinctQuestions() {
        val sel = QuestionSelector(Random(11)).select(questions, QuizMode.ADVANCED, ScopeType.MOCK, 40, emptyMap(), emptyList(), 0)
        assertEquals(40, sel.size)
        assertEquals(40, sel.map { it.id }.toSet().size)
        assertTrue(sel.map { it.chapterId }.toSet().size >= 8)
    }
}

class ReviewSchedulerTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val s = ReviewScheduler()

    // 정답 시 1-3-7-14-30 일 간격으로 진행
    @Test fun correctAnswersFollow1_3_7_14_30() {
        var prev: QuestionProgressEntity? = null
        val expected = listOf(1, 3, 7, 14, 30, 30)
        var now = 0L
        expected.forEach { d ->
            val o = s.schedule(prev, true, 3000, now)
            assertEquals(d.toLong() * day, o.nextReviewAt - now)
            prev = QuestionProgressEntity("q", "BASIC", 0, (prev?.totalAttempts ?: 0) + 1, (prev?.correctAttempts ?: 0) + 1, 0, 0, now, true, 0, o.stage, o.nextReviewAt, o.mastery)
            now = o.nextReviewAt
        }
    }

    // 오답 시 단계 초기화, 느린 정답은 단계 유지, 사용자 간격 적용
    @Test fun wrongResetsSlowKeepsStageCustomIntervals() {
        val p = QuestionProgressEntity("q", "BASIC", 0, 3, 3, 0, 3, 0, true, 0, 3, 0, 0.8)
        assertEquals(0, s.schedule(p, false, 1000, 0).stage)
        assertEquals(1 * day, s.schedule(p, false, 1000, 0).nextReviewAt)
        assertEquals(3, s.schedule(p, true, 120_000, 0).stage)
        val custom = ReviewScheduler(ReviewScheduler.parse("2, 5, 10"))
        assertEquals(listOf(2, 5, 10), custom.intervals)
        assertEquals(2 * day, custom.schedule(null, true, 1000, 0).nextReviewAt)
    }
}

class MasteryAndStandardTest {
    private fun row(q: String, mode: String, total: Int, correct: Int, last: Boolean, stage: Int, next: Long) =
        QuestionProgressEntity(q, mode, 0, total, correct, total - correct, 0, 0, last, 0, stage, next, 0.0)

    // 숙달 단계 전이
    @Test fun masteryLevelTransitions() {
        val ids = listOf("q1", "q2")
        val far = Long.MAX_VALUE / 2
        assertEquals(MasteryLevel.NOT_STARTED, MasteryCalculator.compute(ids, emptyMap(), false, 0))
        assertEquals(MasteryLevel.STARTED, MasteryCalculator.compute(ids, emptyMap(), true, 0))
        val basic1 = mapOf(("q1" to "BASIC") to row("q1", "BASIC", 1, 1, true, 1, far))
        assertEquals(MasteryLevel.BASIC_PRACTICING, MasteryCalculator.compute(ids, basic1, true, 0))
        val basicAll = basic1 + (("q2" to "BASIC") to row("q2", "BASIC", 1, 1, true, 1, far))
        assertEquals(MasteryLevel.BASIC_MASTERED, MasteryCalculator.compute(ids, basicAll, true, 0))
        val adv1 = basicAll + (("q1" to "ADVANCED") to row("q1", "ADVANCED", 1, 1, true, 1, far))
        assertEquals(MasteryLevel.ADVANCED_PRACTICING, MasteryCalculator.compute(ids, adv1, true, 0))
        val advAll = adv1 + (("q2" to "ADVANCED") to row("q2", "ADVANCED", 1, 1, true, 1, far))
        assertEquals(MasteryLevel.ADVANCED_MASTERED, MasteryCalculator.compute(ids, advAll, true, 0))
        val full = advAll.mapValues { if (it.key.second == "ADVANCED") it.value.copy(stage = 3) else it.value }
        assertEquals(MasteryLevel.FULL_MASTERY, MasteryCalculator.compute(ids, full, true, 0))
        val due = full.mapValues { it.value.copy(nextReviewAt = 0) }
        assertEquals(MasteryLevel.REVIEW_NEEDED, MasteryCalculator.compute(ids, due, true, 10))
    }

    // 기준 상태 판정 - 현재·시행예정·과거
    @Test fun standardStatusCurrentUpcomingHistorical() {
        val d = LocalDate.of(2026, 11, 22)
        assertEquals(StandardStatus.UPCOMING, StandardStatusResolver.resolve("2027-01-01", null, false, d))
        assertEquals(StandardStatus.CURRENT, StandardStatusResolver.resolve("2027-01-01", null, false, LocalDate.of(2027, 1, 1)))
        assertEquals(StandardStatus.CURRENT, StandardStatusResolver.resolve(null, null, false, d))
        assertEquals(StandardStatus.HISTORICAL, StandardStatusResolver.resolve(null, "2026-12-31", false, LocalDate.of(2027, 1, 2)))
        assertEquals(StandardStatus.HISTORICAL, StandardStatusResolver.resolve(null, null, true, d))
    }
}

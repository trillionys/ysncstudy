package com.cpaconceptlab.study.domain

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cpaconceptlab.study.TestContent
import com.cpaconceptlab.study.data.importer.detailSectionList
import com.cpaconceptlab.study.data.local.AppDatabase
import com.cpaconceptlab.study.data.local.QuestionProgressEntity
import com.cpaconceptlab.study.data.local.StudyDayEntity
import com.cpaconceptlab.study.data.local.WrongNoteEntity
import com.cpaconceptlab.study.data.repository.DailyGoal
import com.cpaconceptlab.study.data.repository.ReviewPreset
import com.cpaconceptlab.study.data.repository.StudyRepository
import com.cpaconceptlab.study.data.repository.UserSnapshot
import com.cpaconceptlab.study.domain.model.Difficulty
import com.cpaconceptlab.study.domain.model.QuestionKind
import com.cpaconceptlab.study.domain.model.QuizScope
import com.cpaconceptlab.study.domain.model.ScopeType
import com.cpaconceptlab.study.domain.usecase.ReviewScheduler
import com.cpaconceptlab.study.domain.usecase.StatsCalculator
import com.cpaconceptlab.study.ui.quiz.QuizViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** v1.0.7: 재분류 구조, 간단 요약·상세 학습 데이터, 단원 난이도·유형 필터, 오늘의 목표, 복습 주기 */
@RunWith(RobolectricTestRunner::class)
class V7FeaturesTest {
    private val content = TestContent.snapshot

    // ── 재분류·콘텐츠 구조 ──
    @Test fun noStandaloneFinancialAccountingPackAndAccountingPacksHaveContent() {
        assertTrue(content.concepts.none { it.subjectId == "FA" })
        assertTrue(content.questions.none { it.subjectId == "FA" })
        assertEquals(462, content.concepts.count { it.legacyPackId == "FA" })
        listOf("IA", "AA", "GA", "CM").forEach { id ->
            assertTrue(id, content.concepts.count { it.subjectId == id } >= 10)
            assertTrue(id, content.subjects.single { it.id == id }.isActive)
        }
        // 정부회계에 기업의 정부보조금(K-IFRS 1020호) 개념이 섞이지 않는다
        assertTrue(content.concepts.filter { it.subjectId == "GA" }.none { it.standardReference.contains("1020") })
        assertEquals("IA", content.conceptById["P025"]!!.subjectId)   // 정부보조금 → 중급회계
        // 진도 집계 기준(primaryPackId) = 소속 과목, 한 개념은 한 팩에만 속한다
        assertTrue(content.concepts.all { it.primaryPackId == it.subjectId })
    }

    @Test fun everyConceptHasPreservedSummaryAndDetailSections() {
        content.concepts.forEach { c ->
            assertTrue(c.id, c.shortSummary!!.contains(c.definition.trim()))   // 기존 설명 보존
            val secs = c.detailSectionList()
            assertTrue(c.id, secs.isNotEmpty() && secs.first().type == "DEFINITION")
        }
        // 직접 작성 보강(계산 예제·분개): 감가상각
        val p037 = content.conceptById["P037"]!!.detailSectionList()
        val ex = p037.first { it.source == "AUTHORED_V7" && it.type == "WORKED_EXAMPLE" }
        assertTrue(ex.body.contains("1,800,000") && ex.formulas.isNotEmpty() && ex.journal.single().amount == "1,800,000")
        // 세법 개념은 법률 용어로 표시(measurementRules = 법률효과)
        assertTrue(content.conceptById["TA007"]!!.detailSectionList().any { it.title == "법률상 정확한 의미" })
    }

    // ── 단원별 난이도·유형 ──
    @Test fun unitScopeWithDifficultyAndKindStaysInsideTheUnit() {
        val sec = content.conceptById["P037"]!!.sectionId
        val all = QuizViewModel.candidatesFor(QuizScope(ScopeType.SECTION, sec), content, UserSnapshot.EMPTY, 0)
        assertTrue(all.isNotEmpty())
        Difficulty.entries.drop(1).forEach { d ->
            val f = QuizViewModel.candidatesFor(QuizScope(ScopeType.SECTION, sec, d.min, d.max), content, UserSnapshot.EMPTY, 0)
            assertTrue(f.all { content.conceptById[it.primaryConceptId]!!.sectionId == sec && it.difficulty in d.min..d.max })
        }
        // 대단원·파트에서도 같은 방식
        val hard = QuizViewModel.candidatesFor(QuizScope(ScopeType.PART, "유형자산", 3, 3), content, UserSnapshot.EMPTY, 0)
        assertTrue(hard.isNotEmpty() && hard.all { it.chapterId == "FA-P" && it.difficulty == 3 })
        val choice = QuizViewModel.candidatesFor(QuizScope(ScopeType.CHAPTER, "FA-P", kind = QuestionKind.CHOICE), content, UserSnapshot.EMPTY, 0)
        assertTrue(choice.all { it.inputType == "choice" && it.chapterId == "FA-P" })
        val order = QuizViewModel.candidatesFor(QuizScope(ScopeType.SUBJECT, "TX", kind = QuestionKind.ORDER), content, UserSnapshot.EMPTY, 0)
        assertTrue(order.isNotEmpty() && order.all { it.questionType == "order" })
        // 조건에 맞는 문제가 없으면 빈 목록(다른 단원으로 조용히 넘어가지 않는다)
        val none = QuizViewModel.candidatesFor(QuizScope(ScopeType.CONCEPT, "A01", 5, 5), content, UserSnapshot.EMPTY, 0)
        assertTrue(none.isEmpty())
        assertEquals("이 단원에는 선택한 난이도의 문제가 아직 없습니다.", QuizViewModel.UNIT_EMPTY)
        // 스코프 인코딩 왕복(설정 기억)
        val sc = QuizScope(ScopeType.SECTION, sec, 4, 5, QuestionKind.CALC)
        assertEquals(sc, QuizScope.decode(sc.encode()))
    }

    // ── 오늘의 문제 목표 ──
    @Test fun dailyGoalHasNoFixedCapAndValidatesInput() {
        listOf(200, 300, 500, 1000, 2500).forEach { assertEquals(it, DailyGoal.parse("$it").getOrNull()) }
        assertEquals(1500, DailyGoal.parse("1,500").getOrNull())
        listOf("0", "-3", "abc", "", "99999999999999").forEach { assertTrue(it, DailyGoal.parse(it).isFailure) }
        assertTrue(DailyGoal.QUICK.containsAll(listOf(20, 50, 100, 150, 200, 300, 500, 1000)))
    }

    @Test fun todayCountsUniqueQuestionsNotRepeatedSubmissions() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = java.time.ZonedDateTime.of(2026, 11, 25, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        val yesterday = now - TimeUnit.DAYS.toMillis(1)
        val qs = content.questions.take(3)
        fun qp(id: String, mode: String, at: Long) = QuestionProgressEntity(id, mode, at, 5, 3, 2, 1, at, true, 1000, 1, now + 1, 0.5)
        val user = UserSnapshot(
            questionProgress = listOf(
                qp(qs[0].id, "BASIC", now - 1000), qp(qs[0].id, "ADVANCED", now - 500),   // 같은 문항 두 모드 → 1개
                qp(qs[1].id, "BASIC", now - 2000), qp(qs[2].id, "BASIC", yesterday),         // 어제 푼 문항은 제외
            ),
            conceptProgress = emptyList(), wrongNotes = emptyList(),
            studyDays = listOf(StudyDayEntity(java.time.LocalDate.of(2026, 11, 25).toEpochDay(), 12, 8, 0)),
        )
        val ov = StatsCalculator.overview(content, user, now, zone)
        assertEquals(2, ov.todayUniqueSolved)
        assertEquals(12, ov.todaySolved)   // 제출 횟수는 따로 집계
    }

    // ── 복습 주기 ──
    @Test fun reviewPresetsValidationAndPreview() {
        assertEquals(listOf(1, 2, 4, 7, 14, 30), ReviewPreset.FOCUS.intervals)
        assertEquals(listOf(1, 3, 7, 14, 30, 60), ReviewPreset.STANDARD.intervals)
        assertEquals(listOf(2, 5, 10, 21, 45, 90), ReviewPreset.RELAXED.intervals)
        // 기존 사용자 설정 유지: 기본값이면 표준, 직접 바꾼 값이면 직접 설정
        assertEquals(ReviewPreset.STANDARD, ReviewPreset.detect(listOf(1, 3, 7, 14, 30, 60)))
        assertEquals(ReviewPreset.CUSTOM, ReviewPreset.detect(listOf(1, 2, 5)))
        assertNull(ReviewScheduler.validate(listOf(1, 3, 7)))
        assertNotNull(ReviewScheduler.validate(listOf(3, 2)))          // 줄어드는 간격
        assertNotNull(ReviewScheduler.validate(listOf(0, 2)))          // 0일
        assertNotNull(ReviewScheduler.validate(listOf(1, null)))       // 빈 칸
        assertNotNull(ReviewScheduler.validate(emptyList()))
        assertNotNull(ReviewScheduler.validate(listOf(1, 5000)))       // 상한 초과
        assertEquals(listOf(1, 4, 11, 25, 55, 115), ReviewScheduler.previewOffsets(ReviewPreset.STANDARD.intervals))
        // 오답 → 다음날(첫 간격), 정답 반복 → 간격 증가, 오답이어도 누적 기록은 유지
        val s = ReviewScheduler(ReviewPreset.FOCUS.intervals)
        val day = TimeUnit.DAYS.toMillis(1)
        val wrong = s.schedule(null, isCorrect = false, responseMs = 1000, now = 0)
        assertEquals(0, wrong.stage); assertEquals(day, wrong.nextReviewAt)
        val p1 = QuestionProgressEntity("q", "BASIC", 0, 3, 3, 0, 3, 0, true, 0, 2, 0, 0.9)
        assertEquals(3, s.schedule(p1, true, 1000, 0).stage)
        assertEquals(4 * day, s.schedule(p1, true, 1000, 0).nextReviewAt)
    }

    @Test fun rescheduleKeepsRecordsAndDueItems() = runBlocking<Unit> {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val pd = db.progressDao()
        val now = 100 * TimeUnit.DAYS.toMillis(1)
        val due = QuestionProgressEntity("q1", "BASIC", 0, 4, 3, 1, 1, now - TimeUnit.DAYS.toMillis(5), true, 0, 2, now - 1000, 0.7)
        val upcoming = QuestionProgressEntity("q2", "BASIC", 0, 6, 5, 1, 2, now - TimeUnit.DAYS.toMillis(1), true, 0, 3, now + TimeUnit.DAYS.toMillis(10), 0.8)
        pd.upsertQuestionProgress(listOf(due, upcoming))
        pd.upsertWrongNote(WrongNoteEntity("q1", "BASIC", "x", 1, 1, 1, false, null))
        val n = StudyRepository(db).rescheduleUpcoming(ReviewPreset.RELAXED.intervals, now)
        assertEquals(1, n)
        assertEquals(due, pd.questionProgress("q1", "BASIC"))                     // 도래한 복습은 그대로
        val after = pd.questionProgress("q2", "BASIC")!!
        assertEquals(upcoming.copy(nextReviewAt = upcoming.lastAnsweredAt + TimeUnit.DAYS.toMillis(10)), after) // 3단계 → 여유 10일
        assertEquals(1, pd.allWrongNotes().size)
        db.close()
    }
}

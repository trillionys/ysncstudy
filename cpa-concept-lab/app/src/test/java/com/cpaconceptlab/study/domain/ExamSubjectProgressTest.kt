package com.cpaconceptlab.study.domain

import com.cpaconceptlab.study.TestContent
import com.cpaconceptlab.study.data.local.ConceptProgressEntity
import com.cpaconceptlab.study.data.local.QuestionProgressEntity
import com.cpaconceptlab.study.data.repository.UserSnapshot
import com.cpaconceptlab.study.domain.model.MasteryLevel
import com.cpaconceptlab.study.domain.usecase.ExamSubjectProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 홈 '전체 학습현황'(공식 시험과목 5개) 진도 계산 */
class ExamSubjectProgressTest {
    private val content = TestContent.snapshot

    private fun cp(id: String, view: Int, basic: Int, adv: Int, lvl: MasteryLevel = MasteryLevel.STARTED) = ConceptProgressEntity(
        conceptId = id, firstStudiedAt = 1, lastStudiedAt = 2, viewCount = view, basicAttemptCount = basic, basicCorrectCount = basic,
        advancedAttemptCount = adv, advancedCorrectCount = adv, masteryLevel = lvl.name, nextReviewAt = null, isBookmarked = false,
    )

    private fun qp(id: String, mode: String, total: Int, correct: Int) = QuestionProgressEntity(
        questionId = id, mode = mode, firstAnsweredAt = 1, totalAttempts = total, correctAttempts = correct, wrongAttempts = total - correct,
        streak = 1, lastAnsweredAt = 5, lastCorrect = true, totalResponseMs = 1000, stage = 1, nextReviewAt = 0, mastery = 0.0,
    )

    @Test fun fiveOfficialSubjectsWithPacksGrouped() {
        val s = ExamSubjectProgress.summary(content, UserSnapshot.EMPTY)
        assertEquals(listOf("회계학", "경영학", "경제원론", "기업법", "세법개론"), s.map { it.name })
        // 학습 전: 콘텐츠가 있으면 0%, 없으면 null('콘텐츠 없음')
        s.forEach { st -> if (st.totalConcepts > 0) assertEquals(0.0, st.conceptRatio!!, 0.0) else assertNull(st.conceptRatio) }
        val tax = s.single { it.id == "TAX" }
        // v6: 세법개론 콘텐츠 구축(출제범위 목차 161개 중 개념이 실제로 연결된 항목만 반영)
        assertTrue("세법개론 콘텐츠", tax.hasContent && tax.totalConcepts >= 140)
        // v8: 출제범위 목차 전 항목 구축(검증 기준은 ContentCoverageTest.everyCurriculumItemComplete)
        assertTrue(tax.curriculumTotal == 161 && tax.curriculumCovered == 161)
        // 상위 과목 분모 = 하위 팩 개념의 합집합(중복 제거): 중급회계 목차가 재무회계 개념을 연결해도 두 번 세지 않는다
        val acc = s.single { it.id == "ACC" }
        val accIds = content.concepts.filter { it.subjectId in setOf("IA", "AA", "CM", "GA") }.map { it.id }.toSet() +
            content.curriculumItems.filter { it.subjectId in setOf("IA", "AA", "CM", "GA") }.flatMap { it.conceptIds }
        assertEquals(accIds.toSet().size, acc.totalConcepts)
        assertEquals(content.concepts.count { it.subjectId in setOf("IA", "AA", "CM", "GA") }, acc.totalConcepts)
        val d = ExamSubjectProgress.detail("ACC", content, UserSnapshot.EMPTY)
        // v7: 재무회계 독립 팩 없음 — 회계학 = 중급·고급·원가관리·정부회계
        assertEquals(listOf("중급회계", "고급회계", "원가관리회계", "정부회계"), d.packs.map { it.stat.name })
        assertTrue(d.packs.all { it.active && it.stat.totalConcepts > 0 })
        val law = ExamSubjectProgress.detail("LAW", content, UserSnapshot.EMPTY)
        // 기업법은 파트(상법총칙·상행위 / 회사법 / 공인회계사법 / 외부감사법 / 종합)별 세부 진도를 보여준다
        assertTrue(law.packs.single().parts.map { it.name }.containsAll(listOf("회사법", "공인회계사법", "외부감사법")))
    }

    @Test fun completionRuleAndAccuracy() {
        val fa = content.concepts.filter { it.legacyPackId == "FA" }.take(3).map { it.id }
        val q = content.questionsByPrimary[fa[0]]!!.first()
        val user = UserSnapshot(
            questionProgress = listOf(
                qp(q.id, "BASIC", total = 4, correct = 3),
                qp(q.id, "ADVANCED", total = 2, correct = 1),
            ),
            conceptProgress = listOf(
                cp(fa[0], view = 1, basic = 1, adv = 1),                         // 완료
                cp(fa[1], view = 1, basic = 2, adv = 0),                         // 심화 정답 없음 → 미완료
                cp(fa[2], view = 0, basic = 0, adv = 0, lvl = MasteryLevel.FULL_MASTERY), // 이전 버전 완전숙달 기록 → 완료 유지
            ),
            wrongNotes = emptyList(), studyDays = emptyList(),
        )
        val acc = ExamSubjectProgress.summary(content, user).single { it.id == "ACC" }
        assertEquals(2, acc.completedConcepts)
        assertEquals(4, acc.correct); assertEquals(6, acc.attempts)
        assertEquals(1, acc.solvedQuestions)
        assertTrue(acc.conceptRatio!! > 0 && acc.conceptRatio!! < 0.01)
    }
}

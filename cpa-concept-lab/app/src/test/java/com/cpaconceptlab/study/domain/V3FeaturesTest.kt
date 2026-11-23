package com.cpaconceptlab.study.domain

import com.cpaconceptlab.study.TestContent
import com.cpaconceptlab.study.data.importer.ContentCoverageValidator
import com.cpaconceptlab.study.data.importer.OxDetector
import com.cpaconceptlab.study.data.importer.PackValidator
import com.cpaconceptlab.study.data.model.INPUT_CHOICE
import com.cpaconceptlab.study.data.model.INPUT_SLOTS
import com.cpaconceptlab.study.data.model.INPUT_TEXT
import com.cpaconceptlab.study.data.repository.UserSnapshot
import com.cpaconceptlab.study.domain.model.QuestionKind
import com.cpaconceptlab.study.domain.model.QuizScope
import com.cpaconceptlab.study.domain.model.ScopeType
import com.cpaconceptlab.study.domain.usecase.StatsCalculator
import com.cpaconceptlab.study.ui.quiz.QuizViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v3(재고자산·유형자산·차입원가) 기능 검증: 새 문항 유형 채점, OX 금지, 범위·난이도 필터, 파트 통계, 콘텐츠 규칙 */
class V3FeaturesTest {
    private val packs = TestContent.packs
    private val concepts = packs.flatMap { it.concepts }
    private val questions = packs.flatMap { it.questions }
    private val newChapters = setOf("FA-L", "FA-M", "FA-N", "FA-P", "FA-Q", "FA-R")

    /* ── 채점 ── */
    @Test fun choiceGradingRequiresExactOption() {
        assertTrue(AnswerGrader.gradeChoice("후입선출법", "후입선출법").isCorrect)
        assertFalse(AnswerGrader.gradeChoice("선입선출법", "후입선출법").isCorrect)
        assertFalse(AnswerGrader.gradeChoice(null, "후입선출법").isCorrect)
        assertFalse("부분 일치 불인정", AnswerGrader.gradeChoice("후입선출", "후입선출법").isCorrect)
    }

    @Test fun slotGradingRequiresEverySlot() {
        val answers = listOf("생물자산", "수확물", "수확 후 가공품")
        assertTrue(AnswerGrader.gradeSlots(listOf("생물자산", "수확물", "수확 후 가공품"), answers).isCorrect)
        assertFalse(AnswerGrader.gradeSlots(listOf("생물자산", "수확 후 가공품", "수확물"), answers).isCorrect)
        assertFalse("빈 칸이 있으면 오답", AnswerGrader.gradeSlots(listOf("생물자산", null, "수확물"), answers).isCorrect)
        assertEquals(listOf(true, false, false), AnswerGrader.slotHits(listOf("생물자산", "수확 후 가공품", "수확물"), answers))
        // 슬롯 답안 인코딩은 콘텐츠 정답 표기와 같다
        val q = questions.first { it.inputType == INPUT_SLOTS }
        assertEquals(q.blankAnswer, AnswerGrader.encodeSlots(q.slots.map { it.label }, q.slots.map { it.answer }))
    }

    @Test fun everyOptionQuestionIsGradableByItsOwnAnswer() {
        questions.filter { it.inputType == INPUT_CHOICE }.forEach { q ->
            assertTrue(q.id, AnswerGrader.gradeChoice(q.blankAnswer, q.blankAnswer).isCorrect)
            assertEquals(q.id, 4, q.choices.size)
        }
        questions.filter { it.inputType == INPUT_SLOTS }.forEach { q ->
            assertTrue(q.id, AnswerGrader.gradeSlots(q.slots.map { it.answer }, q.slots.map { it.answer }).isCorrect)
            q.slots.forEach { assertTrue("${q.id} ${it.answer}", it.answer in q.choices) }
        }
    }

    /* ── OX 금지 ── */
    @Test fun noOxQuestionsInContent() {
        val ox = questions.filter { OxDetector.isOxLike(it) }.map { it.id }
        assertTrue("OX형 문항: $ox", ox.isEmpty())
    }

    @Test fun oxDetectorRejectsYesNoForms() {
        val base = questions.first { it.inputType == INPUT_CHOICE }
        assertTrue(OxDetector.isOxLike(base.copy(choices = listOf("예", "아니요"), blankAnswer = "예")))
        assertTrue(OxDetector.isOxLike(base.copy(choices = listOf("적격자산", "적격자산 아님"))))
        assertTrue(OxDetector.isOxLike(base.copy(questionText = "다음 설명이 맞으면 O, 틀리면 X를 고르시오.")))
        assertFalse(OxDetector.isOxLike(base))
        // 가져오기 팩에서도 거부한다
        val pack = packs.first().copy(
            questions = listOf(base.copy(id = "OX-Q01", choices = listOf("참", "거짓", "맞음", "틀림"), blankAnswer = "참")),
            concepts = emptyList(), subjects = emptyList(), chapters = emptyList(), sections = emptyList(), examScopes = emptyList(),
            problemSources = emptyList(), scopeItems = emptyList(),
        )
        val plan = PackValidator.validate(
            listOf(pack), concepts.associate { it.id to it.revision }, emptyMap(),
            packs.flatMap { it.chapters }.map { it.id }.toSet(), packs.flatMap { it.sections }.map { it.id }.toSet(), setOf("FA"),
        )
        assertFalse(plan.ok)
    }

    /* ── 콘텐츠 규칙(Part 11) ── */
    @Test fun everyNewConceptHasBasicAdvancedAndApplicationQuestions() {
        val application = setOf("choice", "matching", "classification", "comparison", "situation", "exception", "order",
            "journal", "calculation", "treatment", "correction")
        val byPrimary = questions.filter { it.isActive }.groupBy { it.primaryConceptId }
        val missing = concepts.filter { it.chapterId in newChapters }.mapNotNull { c ->
            val qs = byPrimary[c.id].orEmpty()
            val lacks = buildList {
                if (qs.none { it.difficulty <= 2 }) add("기본")
                if (qs.none { it.difficulty >= 3 }) add("심화")
                if (qs.none { it.questionType in application }) add("구별·응용")
            }
            if (lacks.isEmpty()) null else "${c.id} $lacks"
        }
        assertTrue("누락: $missing", missing.isEmpty())
    }

    @Test fun newContentIsNotMarkedVerifiedAndSourcesAreHonest() {
        assertTrue(concepts.none { it.verificationStatus == "VERIFIED" })
        val sources = packs.flatMap { it.problemSources }
        assertEquals((1..42).map { "NEW-INV-%03d".format(it) }, sources.map { it.sourceId })
        // 원본 이미지가 제공되지 않았으므로 모두 미검증·검토 필요 상태여야 한다
        assertTrue(sources.all { it.verificationStatus != "VERIFIED" && it.reviewRequired && it.readingConfidence == "NONE" })
        // 38~42 는 농림어업 단원과 연결
        sources.filter { it.originalNumber!!.toInt() >= 38 }.forEach { s -> assertTrue(s.conceptIds.all { it.startsWith("R") } && s.conceptIds.isNotEmpty()) }
        val scope = packs.flatMap { it.scopeItems }
        assertTrue(scope.size >= 700)
        assertTrue("미반영 학습범위: ${scope.filter { !it.covered }.map { it.item }}", scope.all { it.covered && it.conceptId != null })
    }

    @Test fun existingConceptsAndExamScopesAreKept() {
        val required = TestContent.manifest.requiredConceptIds
        assertEquals(285, required.size)
        assertTrue(required.all { id -> concepts.any { it.id == id && it.isActive } })
        val scopes = packs.flatMap { it.examScopes }
        assertEquals((1..46).map { "%02d".format(it) }, scopes.map { it.number }.sorted())
        val report = ContentCoverageValidator.validate(concepts, questions, scopes, required)
        assertEquals(0, report.oxQuestions)
        assertEquals(report.calcQuestions, report.calcVerified)
        assertTrue(report.errors.toString(), report.passed)
    }

    /* ── 범위·난이도·형식 필터 ── */
    @Test fun scopeEncodingRoundTripsWithFilters() {
        val s = QuizScope(ScopeType.PART, "유형자산", 3, 4, QuestionKind.CALC)
        assertEquals(s, QuizScope.decode(s.encode()))
        // 기존 형식(필터 없음)은 그대로 해석된다
        assertEquals(QuizScope(ScopeType.CHAPTER, "FA-D"), QuizScope.decode("CHAPTER:FA-D"))
        assertEquals("CHAPTER:FA-D", QuizScope(ScopeType.CHAPTER, "FA-D").encode())
    }

    @Test fun candidateFiltersApplyPartLevelAndKind() {
        val content = TestContent.snapshot
        val user = UserSnapshot.EMPTY
        val calc = QuizViewModel.candidatesFor(QuizScope(ScopeType.PART, "차입원가", kind = QuestionKind.CALC), content, user, 0)
        assertTrue(calc.isNotEmpty())
        assertTrue(calc.all { it.chapterId == "FA-Q" && it.questionType == "calculation" })
        val lv1 = QuizViewModel.candidatesFor(QuizScope(ScopeType.ALL, minLevel = 1, maxLevel = 1), content, user, 0)
        assertTrue(lv1.isNotEmpty() && lv1.all { it.difficulty == 1 })
        val blank = QuizViewModel.candidatesFor(QuizScope(ScopeType.CHAPTER, "FA-P", kind = QuestionKind.BLANK), content, user, 0)
        assertTrue(blank.all { it.inputType == INPUT_TEXT && it.questionType != "calculation" })
        val options = QuizViewModel.candidatesFor(QuizScope(ScopeType.ALL, kind = QuestionKind.OPTIONS), content, user, 0)
        assertTrue(options.isNotEmpty() && options.all { it.inputType != INPUT_TEXT })
        val recent = QuizViewModel.candidatesFor(QuizScope(ScopeType.RECENT), content, user, 0)
        assertTrue(recent.isNotEmpty() && recent.all { it.chapterId in newChapters })
    }

    @Test fun partStatsCoverInventoryPpeAndBorrowingCosts() {
        val content = TestContent.snapshot
        val parts = StatsCalculator.partStats(content, UserSnapshot.EMPTY, StatsCalculator.masteryMap(content, UserSnapshot.EMPTY, 0))
        val names = parts.map { it.partTitle }
        assertTrue(names.containsAll(listOf("재고자산", "유형자산", "차입원가")))
        assertEquals(content.concepts.size, parts.sumOf { it.conceptCount })
        assertEquals(content.questions.size, parts.sumOf { it.questionCount })
        assertTrue(parts.all { it.studiedConcepts == 0 && it.accuracy == null && it.wrongCount == 0 })
    }
}

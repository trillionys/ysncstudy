package com.cpaconceptlab.study.domain

import com.cpaconceptlab.study.TestContent
import com.cpaconceptlab.study.data.importer.OxDetector
import com.cpaconceptlab.study.data.importer.structureErrors
import com.cpaconceptlab.study.data.importer.toDto
import com.cpaconceptlab.study.data.model.INPUT_SLOTS
import com.cpaconceptlab.study.data.model.INPUT_TEXT
import com.cpaconceptlab.study.data.repository.ContentSnapshot
import com.cpaconceptlab.study.data.repository.UserSnapshot
import com.cpaconceptlab.study.domain.model.ArticleStatus
import com.cpaconceptlab.study.domain.model.LawApplicability
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.model.QuizScope
import com.cpaconceptlab.study.domain.model.ScopeType
import com.cpaconceptlab.study.domain.usecase.LawCoverage
import com.cpaconceptlab.study.domain.usecase.QuestionSelector
import com.cpaconceptlab.study.ui.quiz.QuizViewModel
import com.cpaconceptlab.study.utils.Chosung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/** v4 기업법(CPA-LAW) 콘텐츠·출제·채점 검증 */
class LawContentTest {
    private val content = TestContent.snapshot
    private val law = "CPA-LAW"
    private val lawConcepts = content.concepts.filter { it.subjectId == law }
    private val lawQuestions = content.questions.filter { it.subjectId == law }

    private fun norm(s: String) = s.replace(Regex("[\\s·ㆍ,]"), "")

    // ── 커버리지 ──
    @Test fun everyArticleHasAKnownStatusAndCoverageIsReportedPerLaw() {
        assertTrue(content.lawArticles.size > 1300)
        content.lawArticles.forEach { assertTrue(it.id, ArticleStatus.entries.any { s -> s.name == it.status }) }
        val rows = LawCoverage.rows(content.lawArticles)
        assertEquals(listOf("상법총칙", "상행위", "회사법", "공인회계사법", "외부감사법"), rows.map { it.group })
        rows.forEach { r ->
            assertEquals(r.group, r.total, r.completed + r.integrated + r.writing + r.review + r.deleted + r.outOfScope)
            assertTrue("${r.group} 개념 연결 조문이 없음", r.linked > 0)
            assertEquals(r.group, 0, r.outOfScope)
        }
        // 공인회계사법·외부감사법은 법률 전체가 시험범위
        assertEquals(83, rows.single { it.group == "공인회계사법" }.total)
        assertEquals(52, rows.single { it.group == "외부감사법" }.total)
        // 학습 완료 조문은 실제로 문항에 인용되어 있다
        content.lawArticles.filter { it.status == "COMPLETED" }.forEach { assertTrue(it.id, it.questionCount > 0) }
        content.lawArticles.filter { it.status == "INTEGRATED" }.forEach { assertTrue(it.id, it.conceptIds.isNotEmpty() && it.questionCount == 0) }
    }

    // ── 시험범위 제외(상법 보험·해상·항공운송편, 어음·수표법) ──
    @Test fun outOfScopeArticlesAreExcludedAndNeverTaught() {
        val out = content.lawArticles.filter { it.status == "OUT_OF_SCOPE" }
        assertTrue(out.isNotEmpty())
        assertTrue(out.all { it.lawShortName == "상법" && (it.part.startsWith("제4편") || it.part.startsWith("제5편") || it.part.startsWith("제6편")) })
        assertTrue(out.all { it.text.isEmpty() && it.conceptIds.isEmpty() && it.questionCount == 0 })
        val inScopeParts = content.lawArticles.filter { it.lawShortName == "상법" && it.inScope }.map { it.part.take(3) }.toSet()
        assertEquals(setOf("제1편", "제2편", "제3편"), inScopeParts)
        // 모든 기업법 개념의 근거 조문은 시험범위 안에 있다
        lawConcepts.forEach { c -> content.articlesOf(c).forEach { a -> assertTrue("${c.id} → ${a.id}", a.inScope) } }
        assertFalse(content.lawArticles.any { it.lawName.contains("어음") || it.lawName.contains("수표") })
    }

    // ── 개정·시행일 판정 ──
    @Test fun effectiveDateClassification() {
        val ref = LocalDate.of(2027, 2, 1)
        assertEquals(LawApplicability.APPLIES_2027, LawApplicability.classify("2026-03-06", null, ref))
        assertEquals(LawApplicability.APPLIES_2027, LawApplicability.classify("2027-01-01", null, ref))
        assertEquals(LawApplicability.NOT_IN_FORCE, LawApplicability.classify("2027-07-01", null, ref))
        assertEquals(LawApplicability.PAST, LawApplicability.classify("2020-01-01", "2026-07-23", ref))
        assertEquals(LawApplicability.CHECK_DATE, LawApplicability.classify(null, null, ref))
        assertEquals(LawApplicability.CHECK_DATE, LawApplicability.classify("2027-01-01", null, ref, verified = false))
        // 콘텐츠: 모든 기업법 개념에 적용 구분·법령 버전·출처·대조일이 있다
        lawConcepts.forEach { c ->
            assertNotNull(c.id, LawApplicability.of(c.examApplicability))
            assertTrue(c.id, !c.lawVersion.isNullOrBlank() && !c.sourceUrl.isNullOrBlank() && c.verifiedAt == "2026-11-23" && c.examYear == 2027)
        }
        // 시행일 확인 필요·미시행 구분은 검토 필요 표시와 함께 나온다
        lawConcepts.filter { it.examApplicability == "CHECK_DATE" || it.examApplicability == "NOT_IN_FORCE" }.forEach { assertTrue(it.id, it.requiresReview) }
        // 2026. 3. 6. 신설 조문(자기주식 신주배정 금지) 반영
        assertTrue(lawConcepts.any { c -> c.articleRefs.any { it.startsWith("상법 제529조의2") } && c.effectiveFrom == "2026-03-06" })
        // 2027. 1. 1. 시행 예정 개정 조문은 '검토 필요'로 표시
        assertEquals("REVIEW_NEEDED", content.lawArticleById["상법|제368조"]!!.status)
    }

    // ── 숫자 규칙: 값이 조문 원문에 있음 ──
    @Test fun numericRuleValuesAppearInStatuteText() {
        assertTrue(content.numericRules.size >= 150)
        content.numericRules.forEach { r ->
            val keys = r.article.split(", ").mapNotNull { ContentSnapshot.articleKey(it) }
            val text = keys.mapNotNull { content.lawArticleById[it] }.joinToString(" ") { it.title + " " + it.text }
            val q = content.questionById[r.questionId]!!
            val ok = (listOf(q.blankAnswer) + q.acceptedAnswers).any { norm(text).contains(norm(it)) }
            assertTrue("${r.ruleId} ${r.value} 가 ${r.article} 원문에 없음", ok)
            assertEquals(r.ruleId, "number", q.questionType)
        }
    }

    // ── 정족수 계산 ──
    @Test fun quorumFormulas() {
        fun ord(t: Int, nv: Int, p: Int) = FormulaEngine.evaluate("res_ordinary_min", mapOf("total" to t.toDouble(), "nonvote" to nv.toDouble(), "present" to p.toDouble())).toInt()
        fun spe(t: Int, nv: Int, p: Int) = FormulaEngine.evaluate("res_special_min", mapOf("total" to t.toDouble(), "nonvote" to nv.toDouble(), "present" to p.toDouble())).toInt()
        // 보통결의: 출석 과반수 & 발행주식총수 1/4
        assertEquals(351, ord(1000, 0, 700))          // 출석 과반수가 더 큼
        assertEquals(250, ord(1000, 0, 400))          // 1/4 이 더 큼
        assertEquals(425, ord(2000, 300, 700))        // 의결권 없는 주식은 발행주식총수에서 제외
        assertEquals(4, ord(10, 0, 7))                // 홀수 출석: 3.5 초과
        // 특별결의: 출석 2/3 & 발행주식총수 1/3
        assertEquals(467, spe(1000, 0, 700))
        assertEquals(334, spe(1000, 0, 450))
        assertTrue(spe(1000, 0, 700) > ord(1000, 0, 700))
        // 콘텐츠의 정족수 계산문항은 앱 공식으로 다시 계산해도 같은 답
        lawQuestions.mapNotNull { q -> q.calcFormulaId?.let { q to it } }.forEach { (q, f) ->
            val v = FormulaEngine.format(FormulaEngine.evaluate(f, com.cpaconceptlab.study.data.importer.decodeParams(q.calcParams)))
            assertEquals(q.id, q.blankAnswer, v)
        }
    }

    // ── 소수주주권 지분율 / 상장·비상장 / 보통·특별결의 ──
    @Test fun minorityRightsListedVsUnlistedAndResolutionTypes() {
        fun art(k: String) = content.lawArticleById[k]!!.text
        assertTrue(art("상법|제403조").contains("100분의 1"))
        assertTrue(art("상법|제366조").contains("100분의 3"))
        assertTrue(art("상법|제520조").contains("100분의 10"))
        assertTrue(art("상법|제542조의6").contains("6개월"))
        val listed = lawConcepts.filter { c -> c.articleRefs.any { it.startsWith("상법 제542조의6") } }
        assertTrue(listed.isNotEmpty())
        assertTrue(lawQuestions.any { q -> q.standardReference.contains("제542조의6") })
        assertTrue(art("상법|제368조").contains("과반수") && art("상법|제368조").contains("4분의 1"))
        assertTrue(art("상법|제434조").contains("3분의 2") && art("상법|제434조").contains("3분의 1"))
        val slot = lawQuestions.first { it.primaryConceptId == "LI006" && it.inputType == INPUT_SLOTS }
        assertTrue(slot.slotAnswers.any { it.contains("4분의 1") } && slot.slotAnswers.any { it.contains("3분의 1") })
    }

    // ── 초성·채점·매칭 ──
    @Test fun chosungGradingAndMatching() {
        lawQuestions.filter { it.inputType == INPUT_TEXT }.forEach { q ->
            assertTrue(q.id, Chosung.hint(q.blankAnswer).isNotBlank())
            assertTrue(q.id, AnswerGrader.grade(q.blankAnswer, q.blankAnswer, q.acceptedAnswers, q.unit).isCorrect)
            q.acceptedAnswers.forEach { a -> assertTrue("${q.id} $a", AnswerGrader.grade(a, q.blankAnswer, q.acceptedAnswers, q.unit).isCorrect) }
        }
        val n = lawQuestions.first { it.blankAnswer == "100분의 10" && "10%" in it.acceptedAnswers }
        assertTrue(AnswerGrader.grade("10%", n.blankAnswer, n.acceptedAnswers, n.unit).isCorrect)
        assertFalse(AnswerGrader.grade("100분의 3", n.blankAnswer, n.acceptedAnswers, n.unit).isCorrect)
        assertEquals("ㅈㄱㅈㅅ", Chosung.hint("자기주식"))
        lawQuestions.filter { it.inputType != INPUT_TEXT }.forEach { q -> assertTrue(q.id, structureErrors(q.toDto()).isEmpty()) }
        val m = lawQuestions.first { it.inputType == INPUT_SLOTS }
        assertTrue(AnswerGrader.gradeSlots(m.slotAnswers, m.slotAnswers).isCorrect)
        assertFalse(AnswerGrader.gradeSlots(m.slotAnswers.reversed().takeIf { it != m.slotAnswers } ?: listOf("x"), m.slotAnswers).isCorrect)
    }

    // ── 품질: OX 0개, 중복 0개, 정답-조문 대조 상태 ──
    @Test fun noOxNoDuplicatesAndEveryAnswerHasVerificationState() {
        assertEquals(0, lawQuestions.count { OxDetector.isOxLike(it.toDto()) })
        val texts = content.questions.map { norm(it.questionText) }
        assertEquals(texts.size, texts.toSet().size)
        assertTrue(lawQuestions.all { it.answerVerification == "STATUTE_TEXT_MATCHED" || it.answerVerification == "INTERPRETIVE" })
        assertTrue(lawQuestions.count { it.answerVerification == "STATUTE_TEXT_MATCHED" } > lawQuestions.size / 2)
        assertTrue(lawQuestions.all { it.examArea != null })
        // 판례 노트: 확인하지 않은 사건번호를 만들어내지 않는다
        assertTrue(content.caseNotes.isNotEmpty())
        assertTrue(content.caseNotes.all { it.caseNumber == null && it.verificationStatus == "DOCTRINE_ONLY_NOT_VERIFIED" })
        // 각 개념: 기본(1~2)·심화(3~5) 문항, 2개 이상
        lawConcepts.forEach { c ->
            val qs = content.questionsByPrimary[c.id].orEmpty()
            assertTrue(c.id, qs.size >= 2 && qs.any { it.difficulty <= 2 } && qs.any { it.difficulty >= 3 })
        }
    }

    // ── 1회독: 균등학습은 모든 개념을 한 번씩 출제한 뒤 반복 ──
    @Test fun evenStudyCoversEveryLawConceptOnceBeforeRepeating() {
        val candidates = QuizViewModel.candidatesFor(QuizScope(ScopeType.SUBJECT, law), content, UserSnapshot.EMPTY, 0)
        assertTrue(candidates.all { it.subjectId == law })
        val n = lawConcepts.size
        val picked = QuestionSelector(Random(11)).select(candidates, QuizMode.BASIC, ScopeType.SUBJECT, n, emptyMap(), emptyList(), 0L)
        assertEquals(n, picked.size)
        assertEquals(lawConcepts.map { it.id }.toSet(), picked.map { it.primaryConceptId }.toSet())
        // FA 과목 범위에는 기업법 문항이 섞이지 않는다
        assertTrue(QuizViewModel.candidatesFor(QuizScope(ScopeType.SUBJECT, "FA"), content, UserSnapshot.EMPTY, 0).none { it.subjectId == law })
    }

    // ── 실전 비중 모드: 40문항, 공고 비중 ──
    @Test fun weightedMockMatchesNoticeWeights() {
        val quota = QuizViewModel.weightedQuota(content, law)
        assertEquals(mapOf("GENERAL" to 6, "COMPANY" to 24, "CPA_ACT" to 4, "AUDIT_ACT" to 6), quota)
        assertEquals(40, quota.values.sum())
        content.examAreas.forEach { a ->
            val pct = a.mockQuestions * 100.0 / 40
            assertTrue(a.name, pct >= a.minPercent && pct <= a.maxPercent)
        }
        val candidates = QuizViewModel.candidatesFor(QuizScope(ScopeType.WEIGHTED, law), content, UserSnapshot.EMPTY, 0)
        repeat(5) { seed ->
            val picked = QuestionSelector(Random(seed)).select(candidates, QuizMode.BASIC, ScopeType.WEIGHTED, 40, emptyMap(), emptyList(), 0L, areaQuota = quota)
            assertEquals(40, picked.size)
            assertEquals(quota, picked.groupingBy { it.examArea!! }.eachCount())
            assertEquals(40, picked.map { it.primaryConceptId }.distinct().size)
        }
    }
}

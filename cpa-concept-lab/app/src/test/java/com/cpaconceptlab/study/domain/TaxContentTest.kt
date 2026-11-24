package com.cpaconceptlab.study.domain

import com.cpaconceptlab.study.TestContent
import com.cpaconceptlab.study.data.importer.OxDetector
import com.cpaconceptlab.study.data.importer.toDto
import com.cpaconceptlab.study.data.model.INPUT_SLOTS
import com.cpaconceptlab.study.data.repository.UserSnapshot
import com.cpaconceptlab.study.domain.model.LawApplicability
import com.cpaconceptlab.study.domain.model.QuestionKind
import com.cpaconceptlab.study.domain.model.QuizScope
import com.cpaconceptlab.study.domain.model.ScopeType
import com.cpaconceptlab.study.domain.usecase.ExamSubjectProgress
import com.cpaconceptlab.study.domain.usecase.LawCoverage
import com.cpaconceptlab.study.ui.quiz.QuizViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v6 세법개론(TX) 검증.
 *  - 세율표: 법령 원문 수치로 사람이 손계산한 값과 TaxRuleEngine 결과 비교(빌드 도구 calc.py 와는 별도 구현)
 *  - 범위: 7개 법률 단원, 공고 출제비중 40문항 배정
 *  - 문항: OX·사실상 OX 0개, 개념당 3문항 이상, 기본(1~2)·심화(3~5) 모두 존재
 *  - 법령: 모든 세법 개념은 [현재 기준] 표시(2027 기준일 미확정)이며 근거 조문·출처 URL 보유
 */
class TaxContentTest {
    private val content = TestContent.snapshot
    private val tx = "TX"
    private val concepts = content.concepts.filter { it.subjectId == tx }
    private val questions = content.questions.filter { it.subjectId == tx }

    private fun tax(code: Int, base: Long) = TaxRuleEngine.compute(code, base.toDouble())

    // ── 세율표 정확성(손계산) ──
    @Test fun corporateTaxRates2026() {
        // 법인세법 제55조①(2025. 12. 23. 개정, 2026. 1. 1. 이후 개시 사업연도): 10/20/22/25%
        assertEquals(15_000_000.0, tax(1, 150_000_000), 0.0)                 // 1.5억 × 10%
        assertEquals(20_000_000.0, tax(1, 200_000_000), 0.0)                 // 구간 경계
        assertEquals(80_000_000.0, tax(1, 500_000_000), 0.0)                 // 2천만 + 3억 × 20%
        assertEquals(5_080_000_000.0, tax(1, 25_000_000_000), 0.0)           // 39.8억 + 50억 × 22%
        assertEquals(65_580_000_000.0 + 25_000_000_000.0, tax(1, 400_000_000_000), 1.0) // 655.8억 + 1천억 × 25%
    }

    @Test fun incomeTaxBasicRates() {
        // 소득세법 제55조①: 1,400만 6% / 5,000만 15% / 8,800만 24% / 1.5억 35% / 3억 38% / 5억 40% / 10억 42%
        assertEquals(840_000.0, tax(2, 14_000_000), 0.0)
        assertEquals(8_640_000.0, tax(2, 60_000_000), 0.0)                   // 624만 + 1천만 × 24%
        assertEquals(15_360_000.0, tax(2, 88_000_000), 0.0)
        assertEquals(56_060_000.0, tax(2, 200_000_000), 0.0)                 // 3,706만 + 5천만 × 38%
        assertEquals(174_060_000.0, tax(2, 500_000_000), 0.0)                // 9,406만 + 2억 × 40%
        assertEquals(384_060_000.0, tax(2, 1_000_000_000), 0.0)              // 1억7,406만 + 5억 × 42%
    }

    @Test fun inheritanceAndGiftRates() {
        // 상증법 제26조: 1억 10% / 5억 20% / 10억 30% / 30억 40% / 초과 50%
        assertEquals(10_000_000.0, tax(3, 100_000_000), 0.0)
        assertEquals(90_000_000.0, tax(3, 500_000_000), 0.0)
        assertEquals(440_000_000.0, tax(3, 1_500_000_000), 0.0)              // 2.4억 + 5억 × 40%
        assertEquals(1_540_000_000.0, tax(3, 4_000_000_000), 0.0)            // 10.4억 + 10억 × 50%
    }

    @Test fun earnedIncomeDeductionWithCap() {
        // 소득세법 제47조①: 70/40/15/5/2%, 한도 2천만원
        assertEquals(3_500_000.0, tax(4, 5_000_000), 0.0)
        assertEquals(9_750_000.0, tax(4, 30_000_000), 0.0)
        assertEquals(14_750_000.0, tax(4, 100_000_000), 0.0)
        assertEquals(15_750_000.0, tax(4, 150_000_000), 0.0)
        assertEquals(20_000_000.0, tax(4, 400_000_000), 0.0)                 // 2,075만 → 한도 2천만
    }

    @Test fun rateTablesAreDataWithSources() {
        val t = TaxRuleEngine.tables
        assertEquals(listOf("CIT", "PIT", "INH", "EID"), t.map { it.id })
        t.forEach {
            assertTrue(it.id, it.effectiveFrom.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
            assertTrue(it.id, it.evidence.isNotEmpty() && it.verificationStatus == "STATUTE_TEXT_MATCHED")
            assertTrue(it.id, it.brackets.last().upTo == null && it.brackets.dropLast(1).zipWithNext().all { (a, b) -> a.upTo!! < b.upTo!! })
        }
    }

    // ── 범위 ──
    @Test fun allSevenLawUnitsHaveConceptsAndQuestions() {
        val units = content.chaptersOf(tx).map { it.partTitle }
        assertEquals(listOf("국세기본법", "법인세법", "소득세법", "부가가치세법", "상속세 및 증여세법", "지방세법", "조세특례제한법", "종합 계산·비교·응용"), units)
        content.chaptersOf(tx).forEach { ch ->
            assertTrue(ch.title, concepts.count { it.chapterId == ch.id } >= 6)
        }
        assertTrue(concepts.size >= 140)
        assertTrue(questions.size >= 440)
        // 조문 커버리지: 7개 법률 모두 개념이 연결된 조문이 있다
        val rows = LawCoverage.rows(content.lawArticles, tx)
        assertEquals(7, rows.size)
        rows.forEach { r -> assertTrue(r.group, r.linked > 0) }
        // 지방세법은 취득세·등록면허세·재산세(와 총칙)만 범위 안
        val local = content.lawArticles.filter { it.lawShortName == "지방세법" && it.status != "OUT_OF_SCOPE" && !it.deleted }
        assertTrue(local.all { a -> listOf("제1장", "제2장", "제3장", "제9장").any { a.chapter.startsWith(it) } })
    }

    @Test fun weightedMockFollowsOfficialWeights() {
        val areas = content.examAreas.filter { it.subjectId == tx }
        assertEquals(listOf("TAX_BASIC", "CORP", "INCOME", "VAT", "INH_LOCAL"), areas.sortedBy { QuizViewModel.AREA_ORDER.indexOf(it.id) }.map { it.id })
        assertEquals(40, areas.sumOf { it.mockQuestions })
        areas.forEach { a ->
            val pctOf40 = a.mockQuestions * 100.0 / 40
            assertTrue(a.name, pctOf40 >= a.minPercent && pctOf40 <= a.maxPercent)
            assertTrue(a.name, questions.count { it.examArea == a.id } >= a.mockQuestions)
        }
        val quota = QuizViewModel.weightedQuota(content, tx)
        assertEquals(mapOf("TAX_BASIC" to 5, "CORP" to 14, "INCOME" to 10, "VAT" to 8, "INH_LOCAL" to 3), quota)
        val pool = QuizViewModel.candidatesFor(QuizScope(ScopeType.WEIGHTED, tx), content, UserSnapshot.EMPTY, 0)
        assertTrue(pool.all { it.subjectId == tx && it.examArea != null })
    }

    // ── 문항 ──
    @Test fun noOxAndNoEffectivelyBinaryQuestions() {
        assertEquals(0, questions.count { OxDetector.isOxLike(it.toDto()) })
        val binary = questions.filter { it.inputType == INPUT_SLOTS && it.choices.size < 3 }
        assertTrue("보기 2개 분류문항(사실상 OX): ${binary.map { it.id }}", binary.isEmpty())
        assertTrue(questions.filter { it.inputType == "choice" }.all { it.choices.size == 4 })
    }

    @Test fun everyConceptHasThreeQuestionsAcrossLevels() {
        val by = questions.groupBy { it.primaryConceptId }
        concepts.forEach { c ->
            val qs = by[c.id].orEmpty()
            assertTrue("${c.id} 문항 ${qs.size}", qs.size >= 3)
            assertTrue("${c.id} 기본", qs.any { it.difficulty <= 2 })
            assertTrue("${c.id} 심화", qs.any { it.difficulty >= 3 })
        }
        // 계산 전용 모드에 세법 계산문항이 포함된다(세율은 TaxRuleEngine 데이터로 검산)
        val calc = QuizViewModel.candidatesFor(QuizScope(ScopeType.SUBJECT, tx, kind = QuestionKind.CALC), content, UserSnapshot.EMPTY, 0)
        assertTrue(calc.size >= 50)
        assertTrue(calc.any { it.calcFormulaId == "prog_tax" })
    }

    @Test fun statuteVerificationStatusIsRecorded() {
        assertTrue(questions.all { it.answerVerification in setOf("STATUTE_TEXT_MATCHED", "INTERPRETIVE") })
        assertTrue(questions.count { it.answerVerification == "STATUTE_TEXT_MATCHED" } >= 250)
    }

    // ── 법령 적용 기준 ──
    @Test fun taxConceptsAreLabelledCurrentLawNotConfirmed2027() {
        concepts.forEach { c ->
            assertEquals(c.id, LawApplicability.CURRENT_LAW, LawApplicability.of(c.examApplicability))
            assertTrue(c.id, c.articleRefs.isNotEmpty() && !c.sourceUrl.isNullOrBlank() && c.sourceUrl!!.contains("law.go.kr"))
            assertTrue(c.id, c.revisionNote.orEmpty().contains("2027년 시험 법령 적용 기준일"))
        }
    }

    // ── 과목 진도: 단순 평균이 아니라 완료 개념 / 전체 개념 ──
    @Test fun taxSubjectProgressUsesConceptCounts() {
        val d = ExamSubjectProgress.detail("TAX", content, UserSnapshot.EMPTY)
        assertEquals(concepts.count { it.isActive }, d.subject.totalConcepts)
        assertEquals(0, d.subject.completedConcepts)
        val tp = d.packs.single { it.stat.id == tx }
        assertEquals(8, tp.parts.size)
        assertEquals(d.subject.totalConcepts, tp.parts.sumOf { it.totalConcepts })
        assertEquals(null, d.subject.calcAccuracy)
    }
}

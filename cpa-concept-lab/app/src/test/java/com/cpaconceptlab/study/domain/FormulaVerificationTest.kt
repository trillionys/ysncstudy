package com.cpaconceptlab.study.domain

import com.cpaconceptlab.study.TestContent
import com.cpaconceptlab.study.domain.FormulaEngine.evaluate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 계산문항 정답 독립 검산.
 *  1) 콘텐츠의 모든 계산문항(calcSpec 보유)을 앱의 FormulaEngine 으로 다시 계산해 정답 문자열과 비교한다.
 *  2) 대표 공식은 사람이 손으로 계산한 기대값과 비교한다(빌드 도구·앱 두 구현이 함께 틀리는 경우를 막기 위함).
 */
class FormulaVerificationTest {

    private fun v(id: String, vararg p: Pair<String, Number>) = evaluate(id, p.associate { it.first to it.second.toDouble() })

    @Test fun everyCalculationQuestionMatchesIndependentRecalculation() {
        val calc = TestContent.packs.flatMap { it.questions }.filter { it.calcSpec != null }
        assertTrue("계산문항이 있어야 함", calc.size >= 120)
        val failures = calc.mapNotNull { q ->
            val spec = q.calcSpec!!
            val got = runCatching { FormulaEngine.format(evaluate(spec.formulaId, spec.params)) }.getOrElse { "오류: ${it.message}" }
            if (got == q.blankAnswer) null else "${q.id} ${spec.formulaId}: 콘텐츠 ${q.blankAnswer} / 앱 $got"
        }
        File(TestContent.reportDir(), "formula-verification.txt").writeText(
            "계산문항 ${calc.size}개 중 일치 ${calc.size - failures.size}개\n" + failures.joinToString("\n"),
        )
        assertTrue("검산 실패: $failures", failures.isEmpty())
        // 콘텐츠가 쓰는 공식은 모두 앱 엔진에 구현되어 있어야 한다
        val used = calc.map { it.calcSpec!!.formulaId }.toSet()
        assertTrue("미구현 공식: ${used - FormulaEngine.formulaIds}", FormulaEngine.formulaIds.containsAll(used))
    }

    // 계산 공식이 실제로 서로 다른 숫자의 변형문제를 갖는지(계산 개념의 변형 문제 요구)
    @Test fun calculationConceptsHaveNumericVariants() {
        val calc = TestContent.packs.flatMap { it.questions }.filter { it.calcSpec != null }
        val byFormula = calc.groupBy { it.calcSpec!!.formulaId }
        val withVariants = byFormula.count { (_, qs) -> qs.map { it.blankAnswer }.distinct().size >= 2 }
        assertTrue("변형문제가 있는 공식 수 $withVariants", withVariants >= 25)
    }

    /* ── 재고자산 원가흐름 ── 기초 100개@100, 1차 매입 200개@120, 2차 매입 100개@150, 판매 300개 */
    private val flow = arrayOf("bq" to 100, "bp" to 100, "p1q" to 200, "p1p" to 120, "p2q" to 100, "p2p" to 150, "sq" to 300)

    @Test fun fifoEndingAndCogs() {
        // 기말 100개 = 2차 매입 100개@150 = 15,000 / 판매가능 49,000 → 매출원가 34,000
        assertEquals(15_000.0, v("fifo_end", *flow), 0.0)
        assertEquals(34_000.0, v("fifo_cogs", *flow), 0.0)
    }

    @Test fun totalAverage() {
        // 평균단가 49,000 / 400 = 122.5 → 기말 100개 12,250, 매출원가 36,750
        assertEquals(12_250.0, v("tavg_end", *flow), 0.0)
        assertEquals(36_750.0, v("tavg_cogs", *flow), 0.0)
    }

    @Test fun movingAverage() {
        // 기초 100@100 + 매입 100@130 → 평균 115, 판매 150 → 잔량 50@115, 매입 150@135 → 평균 130, 판매 100 → 기말 100@130
        val p = arrayOf("bq" to 100, "bp" to 100, "p1q" to 100, "p1p" to 130, "s1q" to 150, "p2q" to 150, "p2p" to 135, "s2q" to 100)
        assertEquals(150 * 115.0 + 100 * 130.0, v("mavg_cogs", *p), 0.0)
        assertEquals(13_000.0, v("mavg_end", *p), 0.0)
    }

    @Test fun inventoryErrorEffects() {
        // 기말재고 30,000 과대 + 기초재고 10,000 과소 → 당기순이익 40,000 과대
        assertEquals(40_000.0, v("inv_err_ni", "d_bi" to -10_000, "d_ei" to 30_000), 0.0)
        assertEquals(-40_000.0, v("inv_err_cogs", "d_bi" to -10_000, "d_ei" to 30_000), 0.0)
    }

    /* ── 감모·평가 ── */
    @Test fun shrinkageNrvWritedownAndReversal() {
        assertEquals(20.0, v("shrink_qty", "bookq" to 1_000, "actq" to 980), 0.0)
        assertEquals(2_000.0, v("shrink_loss", "bookq" to 1_000, "actq" to 980, "unit" to 100), 0.0)
        assertEquals(1_200.0, v("normal_shrink", "bookq" to 1_000, "actq" to 980, "normq" to 12, "unit" to 100), 0.0)
        assertEquals(800.0, v("abnormal_shrink", "bookq" to 1_000, "actq" to 980, "normq" to 12, "unit" to 100), 0.0)
        assertEquals(85.0, v("nrv_unit", "price" to 100, "complete" to 10, "sell" to 5), 0.0)
        assertEquals(14_700.0, v("writedown", "actq" to 980, "cost" to 100, "nrv" to 85), 0.0)
        assertEquals(0.0, v("writedown", "actq" to 980, "cost" to 100, "nrv" to 120), 0.0)
        assertEquals(83_300.0, v("inv_carrying", "actq" to 980, "cost" to 100, "nrv" to 85), 0.0)
        // 기초 충당금 30,000, 필요 충당금 200 × 50 = 10,000 → 환입 20,000 / NRV가 원가 초과면 충당금 전액이 한도
        assertEquals(20_000.0, v("wd_reversal", "prev_allow" to 30_000, "actq" to 200, "cost" to 1_000, "nrv" to 950), 0.0)
        assertEquals(30_000.0, v("wd_reversal", "prev_allow" to 30_000, "actq" to 200, "cost" to 1_000, "nrv" to 1_200), 0.0)
    }

    /* ── 매출총이익률법·소매재고법 ── */
    @Test fun grossProfitMethodAndRateConversion() {
        // 기초 20,000 + 매입 100,000 − 매출 120,000 × 75% = 30,000
        assertEquals(30_000.0, v("gp_ending", "bi" to 20_000, "purch" to 100_000, "sales" to 120_000, "gpr" to 25), 0.0)
        assertEquals(25.0, v("markup_to_gpr", "markup" to 100.0 / 3), 0.0)
        assertEquals(25.0, v("gpr_to_markup", "gpr" to 20), 0.0)
    }

    private fun retail(method: Int) = arrayOf(
        "method" to method, "bic" to 14_000, "bir" to 20_000, "pc" to 58_000, "pr" to 80_000, "mu" to 10_000, "muc" to 0,
        "md" to 5_000, "mdc" to 0, "sales" to 80_000, "sret" to 0, "emp" to 0, "norm" to 0, "abc" to 0, "abr" to 0,
    )

    @Test fun retailMethodsUseDifferentDenominators() {
        // 평균원가: 72,000 / (100,000 + 10,000 − 5,000) = 68.57%
        assertEquals(68.57, v("retail_ratio", *retail(1)), 0.0)
        // 저가기준: 순인하 제외 → 72,000 / 110,000 = 65.45%
        assertEquals(65.45, v("retail_ratio", *retail(2)), 0.0)
        // 선입선출: 기초 제외 → 58,000 / (80,000 + 10,000 − 5,000) = 68.24%
        assertEquals(68.24, v("retail_ratio", *retail(3)), 0.0)
        // 저가기준 선입선출: 기초·순인하 제외 → 58,000 / 90,000 = 64.44%
        assertEquals(64.44, v("retail_ratio", *retail(4)), 0.0)
        // 기말 매가 = 100,000 + 10,000 − 5,000 − 80,000 = 25,000 (방법과 무관)
        listOf(1, 2, 3, 4).forEach { assertEquals(25_000.0, v("retail_end_retail", *retail(it)), 0.0) }
    }

    /* ── 유형자산 ── */
    @Test fun depreciationMethods() {
        assertEquals(180_000.0, v("dep_sl", "cost" to 1_000_000, "rv" to 100_000, "life" to 5, "months" to 12), 0.0)
        assertEquals(135_000.0, v("dep_sl", "cost" to 1_000_000, "rv" to 100_000, "life" to 5, "months" to 9), 0.0)
        assertEquals(240_000.0, v("dep_ddb", "cost" to 1_000_000, "life" to 5, "year" to 2), 0.0)
        assertEquals(720_000.0, v("dep_db", "cost" to 5_000_000, "rate" to 40, "year" to 3), 0.0)
        assertEquals(240_000.0, v("dep_syd", "cost" to 1_000_000, "rv" to 100_000, "life" to 5, "year" to 2), 0.0)
        assertEquals(180_000.0, v("dep_units", "cost" to 1_000_000, "rv" to 100_000, "total" to 90_000, "used" to 18_000), 0.0)
        assertEquals(150_000.0, v("dep_change", "cost" to 1_000_000, "rv" to 100_000, "life" to 5, "used" to 2, "newrv" to 40_000, "remaining" to 4), 0.0)
    }

    @Test fun revaluationModel() {
        assertEquals(150_000.0, v("reval_oci_up", "carrying" to 800_000, "fv" to 1_000_000, "priorloss" to 50_000), 0.0)
        assertEquals(50_000.0, v("reval_pl_up", "carrying" to 800_000, "fv" to 1_000_000, "priorloss" to 50_000), 0.0)
        assertEquals(50_000.0, v("reval_pl_down", "carrying" to 1_000_000, "fv" to 850_000, "surplus" to 100_000), 0.0)
        assertEquals(300_000.0, v("reval_dep", "fv" to 1_200_000, "rv" to 0, "remaining" to 4), 0.0)
        assertEquals(100_000.0, v("reval_transfer", "fv" to 1_200_000, "remaining" to 4, "cost" to 1_000_000, "life" to 5), 0.0)
    }

    @Test fun impairmentAndReversalLimit() {
        assertEquals(700_000.0, v("recoverable", "viu" to 700_000, "fvlcd" to 650_000), 0.0)
        assertEquals(200_000.0, v("impairment", "carrying" to 900_000, "viu" to 700_000, "fvlcd" to 650_000), 0.0)
        assertEquals(0.0, v("impairment", "carrying" to 600_000, "viu" to 700_000, "fvlcd" to 650_000), 0.0)
        // 손상 후 4년 말 장부 480,000, 한도 600,000, 회수가능액 700,000 → 120,000
        assertEquals(120_000.0, v("imp_reversal", "cost" to 1_000_000, "life" to 10, "t1" to 2, "rec1" to 640_000, "t2" to 4, "rec2" to 700_000), 0.0)
    }

    @Test fun decommissioningProvision() {
        assertEquals(1_000_000.0, v("pv_single", "amount" to 1_610_510, "rate" to 10, "years" to 5), 0.0)
        assertEquals(100_000.0, v("pv_interest1", "amount" to 1_610_510, "rate" to 10, "years" to 5), 0.0)
        assertEquals(960_000.0, v("aro_change_cost", "carrying" to 900_000, "increase" to 60_000), 0.0)
    }

    @Test fun disposalHeldForSaleExchangeAndGrants() {
        assertEquals(40_000.0, v("disposal_gain", "proceeds" to 450_000, "costs" to 10_000, "cost" to 1_000_000, "accdep" to 600_000), 0.0)
        assertEquals(460_000.0, v("hfs_measure", "carrying" to 500_000, "fv" to 480_000, "sellcost" to 20_000), 0.0)
        assertEquals(40_000.0, v("hfs_loss", "carrying" to 500_000, "fv" to 480_000, "sellcost" to 20_000), 0.0)
        assertEquals(900_000.0, v("exch_cost", "fvgiven" to 800_000, "cashpaid" to 100_000), 0.0)
        assertEquals(150_000.0, v("exch_gain", "fvgiven" to 800_000, "carrying" to 650_000), 0.0)
        assertEquals(180_000.0, v("grant_net_dep", "cost" to 1_000_000, "grant" to 100_000, "rv" to 0, "life" to 5), 0.0)
    }

    /* ── 차입원가 ── */
    @Test fun borrowingCosts() {
        // 1,000만원 6% + 2,000만원 9% → 8%
        assertEquals(8.0, v("bc_rate", "a" to 10_000_000, "ar" to 6, "am" to 12, "b" to 20_000_000, "br" to 9, "bm" to 12), 0.0)
        assertEquals(16_000_000.0, v("bc_avg_exp", "e1" to 12_000_000, "m1" to 12, "e2" to 6_000_000, "m2" to 6, "e3" to 3_000_000, "m3" to 4), 0.0)
        // 특정차입금: 이자 600,000 − 일시운용수익 100,000
        assertEquals(500_000.0, v("bc_specific", "amt" to 10_000_000, "rate" to 6, "m" to 12, "inv" to 100_000), 0.0)
        // 공사 중단 4개월: 8개월만 자본화
        assertEquals(400_000.0, v("bc_specific", "amt" to 10_000_000, "rate" to 6, "m" to 8, "inv" to 0), 0.0)
        // 일반차입금 한도: 계산액 3,200,000 > 실제 이자 2,400,000
        assertEquals(2_400_000.0, v("bc_general", "avgexp" to 40_000_000, "specavg" to 0, "rate" to 8, "limit" to 2_400_000), 0.0)
        // 특정차입금으로 지출을 모두 충당하면 일반차입금 자본화는 0
        assertEquals(0.0, v("bc_general", "avgexp" to 5_000_000, "specavg" to 10_000_000, "rate" to 8, "limit" to 2_400_000), 0.0)
        assertEquals(980_000.0, v("bc_total", "avgexp" to 16_000_000, "samt" to 10_000_000, "srate" to 6, "sm" to 12, "inv" to 100_000, "grate" to 8, "glimit" to 2_000_000), 0.0)
    }

    @Test fun agriculture() {
        assertEquals(1_900_000.0, v("bio_fvlcs", "fv" to 2_000_000, "cts" to 100_000), 0.0)
        assertEquals(-60_000.0, v("bio_change", "fv1" to 880_000, "cts1" to 40_000, "fv0" to 950_000, "cts0" to 50_000), 0.0)
    }

    @Test fun formattingMatchesContentStyle() {
        assertEquals("1,587,664", FormulaEngine.format(1_587_664.0))
        assertEquals("-120,000", FormulaEngine.format(-120_000.0))
        assertEquals("8.75", FormulaEngine.format(8.75))
        assertEquals("68.5", FormulaEngine.format(68.50))
        assertEquals("8", FormulaEngine.format(8.0))
    }
}

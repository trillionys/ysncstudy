package com.cpaconceptlab.study.domain

import com.cpaconceptlab.study.ui.quiz.Calculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** 문제 풀이 계산기 엔진: 우선순위·괄호·%·쉼표·0 나누기 */
class StudyToolsTest {
    private fun e(s: String) = Calculator.format(Calculator.eval(s))

    @Test fun arithmeticWithPrecedenceAndParentheses() {
        assertEquals("14", e("2+3×4"))
        assertEquals("20", e("(2+3)×4"))
        assertEquals("-2", e("3−5"))
        assertEquals("2.5", e("5÷2"))
        assertEquals("0.33333333", e("1÷3"))
        assertEquals("6", e("-2×-3"))
    }

    @Test fun moneyCalculationsUsedInTaxQuestions() {
        // 법인세 과세표준 5억: 2천만 + 3억 × 20%
        assertEquals("80,000,000", e("20000000+300000000×20%"))
        // 근로소득공제 총급여 3천만: 750만 + 1,500만 × 15%
        assertEquals("9,750,000", e("7,500,000+15,000,000×15%"))
        // 대손세액 1,100만 × 10/110
        assertEquals("1,000,000", e("11000000×10÷110"))
        assertEquals("0.1", e("0.1+0.2-0.2"))   // 이진 부동소수 오차 없음
    }

    @Test fun invalidInputIsRejected() {
        listOf("", "1+", "(1+2", "5÷0", "1..2+3").forEach { bad ->
            assertTrue(bad, runCatching { Calculator.eval(bad) }.isFailure)
        }
        assertEquals(BigDecimal("123"), Calculator.eval("1,23"))
    }
}

package com.cpaconceptlab.study.domain

import com.cpaconceptlab.study.utils.Chosung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class ChosungTest {
    // 한글 초성 추출
    @Test fun extractsHangulInitials() {
        assertEquals("ㅈㅅ", Chosung.hint("자산"))
        assertEquals("ㅁㅈㅈㅎㅅ", Chosung.hint("목적적합성"))
        assertEquals("ㅂㅅㅈㅇ", Chosung.hint("발생주의"))
        assertEquals("ㄱㅈㄱㅊ", Chosung.hint("공정가치"))
    }

    // 쌍자음 초성
    @Test fun doubleConsonantInitials() {
        assertEquals("ㄲㄸㅃㅆㅉ", Chosung.hint("까따빠싸짜"))
    }

    // 띄어쓰기는 유지
    @Test fun keepsSpaces() {
        assertEquals("ㅊㅅㅎ ㅍㅎ", Chosung.hint("충실한 표현"))
        assertEquals("ㄷㄷㅈ ㅎㅇ", Chosung.hint("도덕적  해이"))  // 연속 공백 정규화
    }

    // 숫자는 자릿수만 표시
    @Test fun maskDigits() {
        assertEquals("#,###", Chosung.hint("1,000"))
        assertEquals("####", Chosung.hint("1118"))
        assertEquals("##ㄱㅇ", Chosung.hint("12개월"))
    }

    // 영문은 단어 첫 글자만 표시
    @Test fun latinFirstLetterOnly() {
        assertEquals("I___", Chosung.hint("IFRS"))
        assertEquals("K-I___", Chosung.hint("K-IFRS"))
        assertEquals("ㄱㅇㅈㅇ ㅈㅇㅎ ㅅㄱㅊㅈㅊ M__", Chosung.hint("경영진이 정의한 성과측정치 MPM"))
        assertEquals("ㄱㅇㅈ M__", Chosung.hint("경영진 MPM"))
    }

    // 기호 유지와 NFD 입력 정규화
    @Test fun keepsSymbolsAndNormalizesNfd() {
        assertEquals("ㄱㅂ·ㅅㅎ", Chosung.hint("기본·심화"))
        assertEquals("ㅈㅅ·ㅂㅊ", Chosung.hint("자산·부채"))
        val nfd = Normalizer.normalize("자산", Normalizer.Form.NFD)
        assertEquals("ㅈㅅ", Chosung.hint(nfd))
    }

    // 글자 수
    @Test fun letterCount() {
        assertEquals(5, Chosung.letterCount("충실한 표현"))
    }
}

class AnswerGraderTest {
    private fun ok(user: String, answer: String, alts: List<String> = emptyList(), unit: String? = null) =
        AnswerGrader.grade(user, answer, alts, unit).isCorrect

    // 공백 대소문자 유니코드 정규화
    @Test fun normalizesSpacesCaseUnicode() {
        assertTrue(ok("  목적적합성  ", "목적적합성"))
        assertTrue(ok("충실한표현", "충실한 표현"))
        assertTrue(ok("충실한   표현", "충실한 표현"))
        assertTrue(ok("ifrs", "IFRS"))
        assertTrue(ok(Normalizer.normalize("발생주의", Normalizer.Form.NFD), "발생주의"))
        assertTrue(ok("(자산)", "자산"))
        assertTrue(ok("자산.", "자산"))
    }

    // 등록된 동의어만 허용
    @Test fun acceptsOnlyRegisteredSynonyms() {
        assertTrue(ok("K-IFRS", "한국채택국제회계기준", listOf("K-IFRS")))
        assertTrue(ok("표현충실성", "충실한 표현", listOf("표현충실성")))
        assertFalse(ok("국제회계기준", "한국채택국제회계기준", listOf("K-IFRS")))
    }

    // 다른 개념을 정답으로 오인하지 않음
    @Test fun doesNotAcceptDifferentConcept() {
        assertFalse(ok("충실한 표현", "목적적합성"))
        assertFalse(ok("목적적합", "목적적합성"))       // 부분 일치 불허
        assertFalse(ok("비교가능성", "일관성"))
        assertFalse(ok("역선택", "도덕적 해이", listOf("모럴해저드")))
        assertFalse(ok("공정가치", "현행원가", listOf("대체원가")))
        assertFalse(ok("", "자산"))
    }

    // 수치 표기 차이 허용
    @Test fun numericFormattingDifferences() {
        assertTrue(ok("1000", "1,000", unit = "원"))
        assertTrue(ok("1,000원", "1,000", unit = "원"))
        assertTrue(ok("1 000", "1,000", unit = "원"))
        assertTrue(ok("400억", "400", unit = "억 원"))
        assertTrue(ok("400 억 원", "400", unit = "억 원"))
        assertTrue(ok("12개월", "12", unit = "개월"))
        assertTrue(ok("2011년", "2011", unit = "년"))
        assertFalse(ok("1001", "1,000", unit = "원"))
        assertFalse(ok("100", "1,000", unit = "원"))
    }

    // 수치 정답은 숫자 동치로만 판단
    @Test fun numericComparesByValue() {
        assertTrue(ok("2850", "2,850", unit = "원"))
        assertTrue(ok("2850.0", "2,850", unit = "원"))
        assertFalse(ok("2,580", "2,850", unit = "원"))
    }
}

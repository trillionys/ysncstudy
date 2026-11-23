package com.cpaconceptlab.study.domain

import java.math.BigDecimal
import java.text.Normalizer

/**
 * 빈칸 정답 채점기.
 *
 * 허용하는 차이: 앞뒤·연속 공백, 띄어쓰기, 한글 유니코드 정규화(NFC/NFD), 영문 대소문자,
 * 바깥 괄호·따옴표·끝 문장부호, 수치의 쉼표·지정 단위 표기.
 *
 * 허용하지 않는 것: 문제별로 명시 등록되지 않은 유사어. 예컨대 '목적적합성' 문제에 '충실한 표현'은 오답이다.
 * 부분 문자열 일치나 편집거리 기반의 느슨한 채점은 하지 않는다.
 */
object AnswerGrader {

    private val COMMON_UNITS = listOf("억 원", "억원", "만원", "만 원", "원", "개월", "개", "년", "%", "퍼센트", "호")

    /** 비교용 정규화: NFC, 소문자, 모든 공백 제거, 바깥 괄호·따옴표·끝 문장부호 제거. */
    fun normalize(input: String): String {
        var s = Normalizer.normalize(input, Normalizer.Form.NFC).trim()
        s = s.replace(' ', ' ').replace(Regex("\\s+"), " ")
        // 바깥 괄호/따옴표 제거
        val pairs = listOf("(" to ")", "[" to "]", "\"" to "\"", "'" to "'", "“" to "”", "‘" to "’", "「" to "」")
        var changed = true
        while (changed && s.length >= 2) {
            changed = false
            for ((open, close) in pairs) {
                if (s.startsWith(open) && s.endsWith(close)) {
                    s = s.substring(open.length, s.length - close.length).trim(); changed = true
                }
            }
        }
        s = s.trimEnd('.', '。', '!', '?', ',', ';', ':')
        return s.lowercase().replace(" ", "")
    }

    /** 수치 정답인지 판단한다(쉼표·소수점 허용). */
    fun isNumeric(answer: String): Boolean = parseNumber(answer, null) != null && answer.any { it.isDigit() }

    /**
     * 문자열을 수치로 파싱한다. 쉼표·공백 제거, 지정 단위 및 일반 단위 접미사 제거.
     * 숫자가 아니면 null.
     */
    fun parseNumber(raw: String, unit: String?): BigDecimal? {
        var s = Normalizer.normalize(raw, Normalizer.Form.NFC).trim().replace(" ", "")
        val units = buildList {
            if (!unit.isNullOrBlank()) {
                val u = unit.replace(" ", "")
                add(u)
                // '억 원' 단위 문제에서 '400억' 처럼 앞부분 단위만 쓴 표기도 허용
                if (u.length > 1 && u.endsWith("원")) add(u.removeSuffix("원"))
            }
            addAll(COMMON_UNITS.map { it.replace(" ", "") })
        }.distinct().sortedByDescending { it.length }
        for (u in units) {
            if (u.isNotEmpty() && s.endsWith(u)) { s = s.removeSuffix(u); break }
        }
        s = s.replace(",", "")
        if (s.isEmpty() || !s.matches(Regex("[-+]?\\d+(\\.\\d+)?"))) return null
        return s.toBigDecimalOrNull()
    }

    data class Result(val isCorrect: Boolean, val matched: String?)

    fun grade(userAnswer: String, blankAnswer: String, acceptedAnswers: List<String>, unit: String? = null): Result {
        val user = normalize(userAnswer)
        if (user.isEmpty()) return Result(false, null)
        val candidates = (listOf(blankAnswer) + acceptedAnswers).distinct()
        for (c in candidates) {
            if (normalize(c) == user) return Result(true, c)
        }
        // 수치 정답: 표기 차이(쉼표, 단위) 허용
        val expectedNum = parseNumber(blankAnswer, unit)
        if (expectedNum != null && blankAnswer.any { it.isDigit() }) {
            val userNum = parseNumber(userAnswer, unit)
            if (userNum != null && userNum.compareTo(expectedNum) == 0) return Result(true, blankAnswer)
        }
        return Result(false, null)
    }

    /* ───────── v3: 보기 선택형 문항 ───────── */

    const val SLOT_ARROW = " → "
    const val SLOT_SEP = " / "

    /** 슬롯형 답안을 콘텐츠 정답과 같은 형식('라벨 → 답 / 라벨 → 답')으로 만든다. 선택하지 않은 칸은 빈 문자열. */
    fun encodeSlots(labels: List<String>, picks: List<String?>): String =
        labels.mapIndexed { i, l -> l + SLOT_ARROW + (picks.getOrNull(i) ?: "") }.joinToString(SLOT_SEP)

    /** 4지선다: 선택한 보기가 정답 보기와 정확히 같아야 정답(부분 일치·유사어 불인정). */
    fun gradeChoice(selected: String?, answer: String): Result {
        if (selected.isNullOrBlank()) return Result(false, null)
        return if (normalize(selected) == normalize(answer)) Result(true, answer) else Result(false, null)
    }

    /** 슬롯형(매칭·분류·순서·분개 계정): 모든 칸이 정답과 같아야 정답. */
    fun gradeSlots(picks: List<String?>, answers: List<String>): Result {
        if (picks.size != answers.size || picks.any { it.isNullOrBlank() }) return Result(false, null)
        val ok = picks.zip(answers).all { (p, a) -> normalize(p!!) == normalize(a) }
        return Result(ok, if (ok) answers.joinToString(SLOT_SEP) else null)
    }

    /** 슬롯형 부분 점수(맞힌 칸 수). 해설 화면에서 칸별 정오를 보여줄 때 사용한다. */
    fun slotHits(picks: List<String?>, answers: List<String>): List<Boolean> =
        answers.mapIndexed { i, a -> picks.getOrNull(i)?.let { normalize(it) == normalize(a) } ?: false }
}

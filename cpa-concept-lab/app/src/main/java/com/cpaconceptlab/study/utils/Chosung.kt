package com.cpaconceptlab.study.utils

import java.text.Normalizer

/**
 * 한글 초성 힌트 생성기.
 *
 * 규칙(모든 정답에 일관 적용):
 *  - 완성형 한글 음절(가~힣): 유니코드 분해 공식으로 초성 추출  (예: 목적적합성 → ㅁㅈㅈㅎㅅ)
 *  - 한글 호환 자모(ㄱ, ㅏ 등): 그대로 표시
 *  - 숫자: 자릿수만 알 수 있도록 '#' 으로 가림  (예: 1,000 → #,###)
 *  - 영문자: 각 영문 단어의 첫 글자는 대문자로, 나머지는 '_' 로 가림 (예: IFRS → I___, K-IFRS → K-I___)
 *  - 공백과 기호(·, -, %, ., , 등): 그대로 유지
 */
object Chosung {
    private val CHOSEONG = charArrayOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
    )
    private const val SYLLABLE_BASE = 0xAC00
    private const val SYLLABLE_LAST = 0xD7A3
    private const val JUNG_JONG = 21 * 28

    fun isHangulSyllable(c: Char): Boolean = c.code in SYLLABLE_BASE..SYLLABLE_LAST

    /** 완성형 한글 음절의 초성. 음절이 아니면 null. */
    fun choseongOf(c: Char): Char? =
        if (isHangulSyllable(c)) CHOSEONG[(c.code - SYLLABLE_BASE) / JUNG_JONG] else null

    fun hint(answer: String): String {
        val text = Normalizer.normalize(answer.trim(), Normalizer.Form.NFC).replace(Regex("\\s+"), " ")
        val sb = StringBuilder()
        var prevLatin = false
        for (c in text) {
            val cho = choseongOf(c)
            when {
                cho != null -> { sb.append(cho); prevLatin = false }
                c in 'ㄱ'..'ㅣ' -> { sb.append(c); prevLatin = false }
                c.isDigit() -> { sb.append('#'); prevLatin = false }
                c in 'a'..'z' || c in 'A'..'Z' -> {
                    sb.append(if (prevLatin) '_' else c.uppercaseChar())
                    prevLatin = true
                }
                else -> { sb.append(c); prevLatin = false }
            }
        }
        return sb.toString()
    }

    /** 공백을 제외한 글자 수(힌트와 함께 표시). */
    fun letterCount(answer: String): Int =
        Normalizer.normalize(answer, Normalizer.Form.NFC).count { !it.isWhitespace() }
}

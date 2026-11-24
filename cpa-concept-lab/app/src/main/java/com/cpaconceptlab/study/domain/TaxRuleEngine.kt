package com.cpaconceptlab.study.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * 세법 세율표 엔진.
 *
 * 세율·구간은 코드 상수가 아니라 콘텐츠 데이터(assets/content/tax_rules.json)로 관리한다.
 * 콘텐츠 빌드(tools/content/tax_rules.py)가 각 표의 근거 문구를 법령 원문과 대조하고,
 * 앱은 이 데이터를 읽어 누진세액을 독립 계산한다(FormulaEngine 'prog_tax').
 * 데이터를 읽지 못하면 tables 가 비어 있고, 세율 계산 공식은 오류를 낸다(임의의 숫자로 대신하지 않는다).
 */
object TaxRuleEngine {

    @Serializable
    data class Bracket(val upTo: Long? = null, val rate: Double)

    @Serializable
    data class Table(
        val id: String,
        val code: Int,
        val taxType: String,
        val lawName: String,
        val article: String,
        val effectiveFrom: String,
        val effectiveTo: String? = null,
        val taxYearFrom: Int? = null,
        val taxpayerType: String = "",
        val brackets: List<Bracket>,
        val cap: Long? = null,
        val evidence: List<String> = emptyList(),
        val verificationStatus: String = "",
    )

    @Serializable
    data class RuleFile(val formatVersion: Int = 1, val tables: List<Table> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    var tables: List<Table> = emptyList()
        private set

    fun parse(text: String): List<Table> = json.decodeFromString(RuleFile.serializer(), text).tables

    fun install(text: String) {
        tables = parse(text)
    }

    fun table(code: Int): Table = tables.firstOrNull { it.code == code }
        ?: throw IllegalStateException("세율표 $code 가 로드되지 않았습니다(tax_rules.json)")

    /** 누진세액: 구간별로 (구간 상한 − 하한) × 세율을 더하고, 한도(cap)가 있으면 적용 */
    fun compute(code: Int, base: Double): Double {
        val t = table(code)
        var tax = 0.0
        var lower = 0.0
        for (b in t.brackets) {
            val upper = b.upTo?.toDouble()
            val top = if (upper == null) base else minOf(base, upper)
            if (top > lower) tax += (top - lower) * b.rate / 100.0
            if (upper == null || base <= upper) break
            lower = upper
        }
        t.cap?.let { tax = minOf(tax, it.toDouble()) }
        return tax
    }

    /** 표시용: 1억 이상은 '억', 만 단위는 '만원' */
    fun won(v: Long): String {
        val eok = v / 100_000_000
        val man = (v % 100_000_000) / 10_000
        return when {
            eok > 0 && man > 0 -> String.format(Locale.US, "%,d억%,d만원", eok, man)
            eok > 0 -> String.format(Locale.US, "%,d억원", eok)
            else -> String.format(Locale.US, "%,d만원", man)
        }
    }

    fun rate(r: Double): String = if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
}

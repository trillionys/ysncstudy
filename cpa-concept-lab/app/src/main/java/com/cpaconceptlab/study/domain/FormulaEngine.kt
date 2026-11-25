package com.cpaconceptlab.study.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 계산문항 독립 검산 엔진.
 *
 * 콘텐츠 빌드 도구(tools/content/calc.py)가 만든 정답을 앱 쪽에서 별도 구현으로 다시 계산한다.
 * 두 구현의 결과가 다르면 FormulaVerificationTest 가 실패하므로, 숫자를 바꾼 변형문제도 자동 검산된다.
 *
 * 반올림: 금액은 원 단위 사사오입(HALF_UP), 비율(%)은 소수 둘째 자리 사사오입.
 */
object FormulaEngine {

    enum class Kind { AMOUNT, PERCENT, QTY }

    private class Formula(val kind: Kind, val fn: (P) -> Double)

    /** 이름으로 값을 꺼내는 입력값 래퍼. 누락된 입력은 예외로 알린다. */
    class P(private val m: Map<String, Double>) {
        operator fun get(k: String): Double = m[k] ?: throw IllegalArgumentException("입력값 '$k' 누락")
    }

    fun r0(x: Double): Double = BigDecimal(x.toString()).setScale(0, RoundingMode.HALF_UP).toDouble()
    fun r2(x: Double): Double = BigDecimal(x.toString()).setScale(2, RoundingMode.HALF_UP).toDouble()

    private val F: Map<String, Formula> = buildMap {
        fun amount(id: String, fn: (P) -> Double) = put(id, Formula(Kind.AMOUNT, fn))
        fun percent(id: String, fn: (P) -> Double) = put(id, Formula(Kind.PERCENT, fn))

        // ── 재고자산 매입·원가흐름 ──
        amount("net_purchase") { it["gross"] - it["ret"] - it["allow"] - it["disc"] + it["freight"] }
        amount("cogs") { it["bi"] + it["purch"] - it["ei"] }
        amount("goods_available") { it["bi"] + it["purch"] }
        amount("fifo_end") { fifo(it).second }
        amount("fifo_cogs") { val (total, end) = fifo(it); total - end }
        amount("tavg_end") { p -> val (unit, tq) = totalAvg(p); r0(unit * (tq - p["sq"])) }
        amount("tavg_cogs") { p -> val (unit, _) = totalAvg(p); r0(unit * p["sq"]) }
        amount("mavg_cogs") { r0(movingAvg(it).first) }
        amount("mavg_end") { r0(movingAvg(it).second) }
        amount("inv_err_ni") { it["d_ei"] - it["d_bi"] }
        amount("inv_err_cogs") { it["d_bi"] - it["d_ei"] }
        amount("count_adjust") {
            it["counted"] + it["transit_fob_sp"] + it["consign_out"] - it["consign_in"] - it["sold_not_delivered"] + it["sale_return_exp"]
        }
        // ── 감모·평가 ──
        put("shrink_qty", Formula(Kind.QTY) { it["bookq"] - it["actq"] })
        amount("shrink_loss") { (it["bookq"] - it["actq"]) * it["unit"] }
        amount("normal_shrink") { it["normq"] * it["unit"] }
        amount("abnormal_shrink") { (it["bookq"] - it["actq"] - it["normq"]) * it["unit"] }
        amount("nrv_unit") { it["price"] - it["complete"] - it["sell"] }
        amount("writedown") { it["actq"] * max(0.0, it["cost"] - it["nrv"]) }
        amount("inv_carrying") { it["actq"] * min(it["cost"], it["nrv"]) }
        amount("wd_reversal") { max(0.0, it["prev_allow"] - it["actq"] * max(0.0, it["cost"] - it["nrv"])) }
        amount("inv_total_pl") { (it["bookq"] - it["actq"]) * it["cost"] + it["actq"] * max(0.0, it["cost"] - it["nrv"]) }
        amount("cogs_with_losses") {
            it["bi"] + it["purch"] - it["bookq"] * it["cost"] + it["normq"] * it["cost"] + it["actq"] * max(0.0, it["cost"] - it["nrv"])
        }
        // ── 매출총이익률법·소매재고법·결합원가 ──
        amount("gp_ending") { r0(it["bi"] + it["purch"] - it["sales"] * (100 - it["gpr"]) / 100) }
        amount("gp_fire_loss") { r0(it["bi"] + it["purch"] - it["sales"] * (100 - it["gpr"]) / 100 - it["salvage"]) }
        percent("markup_to_gpr") { r2(it["markup"] / (100 + it["markup"]) * 100) }
        percent("gpr_to_markup") { r2(it["gpr"] / (100 - it["gpr"]) * 100) }
        percent("retail_ratio") { r2(retail(it).ratio * 100) }
        amount("retail_end_retail") { retail(it).endRetail }
        amount("retail_end_cost") { val r = retail(it); r0(r.endRetail * r.ratio) }
        amount("retail_cogs") { p -> val r = retail(p); p["bic"] + p["pc"] - p["abc"] - r0(r.endRetail * r.ratio) }
        amount("joint_alloc") { r0(it["joint"] * it["sva"] / (it["sva"] + it["svb"])) }
        amount("joint_nrv_alloc") {
            val a = it["sva"] - it["costa"]; val b = it["svb"] - it["costb"]
            r0(it["joint"] * a / (a + b))
        }
        // ── 유형자산 ──
        amount("lump_alloc") { r0(it["total"] * it["fva"] / (it["fva"] + it["fvb"])) }
        amount("pv_single") { r0(it["amount"] / (1 + it["rate"] / 100).pow(it["years"])) }
        amount("pv_interest1") { r0(r0(it["amount"] / (1 + it["rate"] / 100).pow(it["years"])) * it["rate"] / 100) }
        amount("dep_sl") { r0((it["cost"] - it["rv"]) / it["life"] * it["months"] / 12) }
        amount("dep_ddb") {
            val rate = 2 / it["life"]
            r0(it["cost"] * (1 - rate).pow(it["year"] - 1) * rate)
        }
        amount("dep_db") { r0(it["cost"] * (1 - it["rate"] / 100).pow(it["year"] - 1) * it["rate"] / 100) }
        amount("dep_syd") {
            val sum = it["life"] * (it["life"] + 1) / 2
            r0((it["cost"] - it["rv"]) * (it["life"] - it["year"] + 1) / sum)
        }
        amount("dep_units") { r0((it["cost"] - it["rv"]) / it["total"] * it["used"]) }
        amount("dep_change") {
            val carrying = it["cost"] - (it["cost"] - it["rv"]) / it["life"] * it["used"]
            r0((carrying - it["newrv"]) / it["remaining"])
        }
        amount("carrying_cost_model") { it["cost"] - it["accdep"] - it["accimp"] }
        amount("grant_net_dep") { r0((it["cost"] - it["grant"] - it["rv"]) / it["life"]) }
        amount("grant_disposal_gain") { r0(it["proceeds"] - (it["cost"] - it["grant"]) * (1 - it["years"] / it["life"])) }
        amount("exch_cost") { it["fvgiven"] + it["cashpaid"] }
        amount("exch_gain") { it["fvgiven"] - it["carrying"] }
        amount("exch_nocs_cost") { it["carrying"] + it["cashpaid"] }
        amount("reval_oci_up") { val inc = it["fv"] - it["carrying"]; inc - min(it["priorloss"], inc) }
        amount("reval_pl_up") { min(it["priorloss"], it["fv"] - it["carrying"]) }
        amount("reval_pl_down") { val dec = it["carrying"] - it["fv"]; dec - min(it["surplus"], dec) }
        amount("reval_dep") { r0((it["fv"] - it["rv"]) / it["remaining"]) }
        amount("reval_transfer") { r0(it["fv"] / it["remaining"] - it["cost"] / it["life"]) }
        amount("recoverable") { max(it["viu"], it["fvlcd"]) }
        amount("impairment") { max(0.0, it["carrying"] - max(it["viu"], it["fvlcd"])) }
        amount("imp_reversal") {
            val depAfter = it["rec1"] / (it["life"] - it["t1"])
            val carrying = it["rec1"] - depAfter * (it["t2"] - it["t1"])
            val limit = it["cost"] * (1 - it["t2"] / it["life"])
            r0(max(0.0, min(it["rec2"], limit) - carrying))
        }
        amount("aro_change_cost") { it["carrying"] + it["increase"] }
        amount("disposal_gain") { it["proceeds"] - it["costs"] - (it["cost"] - it["accdep"]) }
        amount("hfs_measure") { min(it["carrying"], it["fv"] - it["sellcost"]) }
        amount("hfs_loss") { max(0.0, it["carrying"] - (it["fv"] - it["sellcost"])) }
        // ── 차입원가 ──
        percent("bc_rate") {
            val interest = it["a"] * it["ar"] / 100 * it["am"] / 12 + it["b"] * it["br"] / 100 * it["bm"] / 12
            val principal = it["a"] * it["am"] / 12 + it["b"] * it["bm"] / 12
            r2(interest / principal * 100)
        }
        amount("bc_avg_exp") { r0(it["e1"] * it["m1"] / 12 + it["e2"] * it["m2"] / 12 + it["e3"] * it["m3"] / 12) }
        amount("bc_specific") { r0(it["amt"] * it["rate"] / 100 * it["m"] / 12 - it["inv"]) }
        amount("bc_general") { r0(min(max(0.0, (it["avgexp"] - it["specavg"]) * it["rate"] / 100), it["limit"])) }
        amount("bc_total") {
            val spec = it["samt"] * it["srate"] / 100 * it["sm"] / 12 - it["inv"]
            val specAvg = it["samt"] * it["sm"] / 12
            val general = min((it["avgexp"] - specAvg) * it["grate"] / 100, it["glimit"])
            r0(spec + max(0.0, general))
        }

        // ── 재무관리 ──
        fun pc(x: Double) = x / 100.0

        // ── 세법(세율은 TaxRuleEngine 데이터) ──
        amount("prog_tax") { r0(TaxRuleEngine.compute(it["table"].toInt(), it["base"])) }
        amount("corp_income") { r0(it["ni"] + it["add"] + it["disallow"] - it["exclude"] - it["deduct"]) }
        amount("rate_of") { r0(it["amount"] * it["rate"] / 100.0) }
        amount("ent_base_limit") { r0((if (it["sme"] != 0.0) 36_000_000.0 else 12_000_000.0) * it["months"] / 12.0) }
        amount("donation_limit") { r0(max(0.0, it["base"] - it["loss"]) * it["rate"] / 100.0) }
        amount("vat_payable") { r0(it["sales"] * it["rate"] / 100.0 - it["input_tax"]) }
        amount("common_input_tax") { r0(it["common"] * it["exempt"] / it["total"]) }
        amount("minus") { r0(it["a"] - it["b"]) }
        // v8 범용 계산(목차 보강 문항)
        amount("add2") { r0(it["a"] + it["b"]) }
        amount("add3") { r0(it["a"] + it["b"] + it["c"]) }
        amount("sub3") { r0(it["a"] - it["b"] - it["c"]) }
        amount("adj") { r0(it["a"] + it["b"] - it["c"]) }
        amount("pct_of") { r0(it["a"] * it["r"] / 100.0) }
        amount("prorate") { r0(it["a"] * it["b"] / it["c"]) }
        amount("diff_pct") { r0((it["a"] - it["b"]) * it["r"] / 100.0) }
        amount("eps") { r0((it["ni"] - it["pref"]) / it["shares"]) }
        percent("pct_ratio") { r2(it["a"] / it["b"] * 100) }
        percent("mult_ratio") { r2(it["a"] / it["b"]) }
        amount("poc_rev") { r0(it["contract"] * it["cum"] / it["total"] - it["prior"]) }
        amount("cum_alloc") { r0(it["total"] * it["elapsed"] / it["period"] - it["prior"]) }
        amount("mul2") { r0(it["a"] * it["b"]) }
        amount("ev2") { r0(it["a"] * it["ra"] / 100.0 + it["b"] * it["rb"] / 100.0) }
        amount("fv") { r0(it["pv"] * (1 + pc(it["r"])).pow(it["n"])) }
        amount("pv") { r0(it["fv"] / (1 + pc(it["r"])).pow(it["n"])) }
        amount("annuity_pv") { val i = pc(it["r"]); r0(it["c"] * (1 - (1 + i).pow(-it["n"])) / i) }
        amount("growing_perp") { r0(it["c1"] / (pc(it["r"]) - pc(it["g"]))) }
        amount("npv3") { val i = 1 + pc(it["r"]); r0(-it["c0"] + it["c1"] / i + it["c2"] / i.pow(2) + it["c3"] / i.pow(3)) }
        percent("capm") { r2(it["rf"] + it["beta"] * (it["rm"] - it["rf"])) }
        percent("wacc") { r2((it["e"] * it["re"] + it["d"] * it["rd"] * (1 - pc(it["t"]))) / (it["e"] + it["d"])) }
        amount("bond_price") { p ->
            val i = pc(p["y"]); val c = p["face"] * pc(p["cr"]); val n = p["n"].toInt()
            r0((1..n).sumOf { t -> c / (1 + i).pow(t) } + p["face"] / (1 + i).pow(n))
        }
        percent("eff_rate") { r2(((1 + pc(it["apr"]) / it["m"]).pow(it["m"]) - 1) * 100) }
        percent("port_ret") { r2(pc(it["w1"]) * it["r1"] + (1 - pc(it["w1"])) * it["r2_"]) }
        percent("port_sd") { p ->
            val a = pc(p["w1"]); val b = 1 - a
            r2(kotlin.math.sqrt(a * a * p["s1"] * p["s1"] + b * b * p["s2"] * p["s2"] + 2 * a * b * p["rho"] * p["s1"] * p["s2"]))
        }
        percent("put_parity") { r2(it["c"] - it["s"] + it["k"] / (1 + pc(it["r"])).pow(it["t"])) }
        percent("fwd_rate") { r2(((1 + pc(it["r2y"])).pow(2) / (1 + pc(it["r1"])) - 1) * 100) }
        // ── 원가관리회계 ──
        put("bep_units", Formula(Kind.QTY) { r0(it["fc"] / (it["p"] - it["vc"])) })
        put("target_units", Formula(Kind.QTY) { r0((it["fc"] + it["ti"]) / (it["p"] - it["vc"])) })
        percent("cm_ratio") { r2((it["p"] - it["vc"]) / it["p"] * 100) }
        percent("dol") { r2(it["cm"] / it["oi"]) }
        amount("price_var") { r0((it["ap"] - it["sp"]) * it["aq"]) }
        amount("qty_var") { r0((it["aq"] - it["sq"]) * it["sp"]) }
        put("eoq", Formula(Kind.QTY) { r0(kotlin.math.sqrt(2 * it["d"] * it["s"] / it["h"])) })
        percent("roi") { r2(it["oi"] / it["ia"] * 100) }
        amount("ri") { r0(it["oi"] - it["ia"] * pc(it["rr"])) }
        put("eu_wavg", Formula(Kind.QTY) { r0(it["done"] + it["end"] * pc(it["pct"])) })
        put("eu_fifo", Formula(Kind.QTY) { r0(it["done"] - it["beg"] * pc(it["bpct"]) + it["end"] * pc(it["epct"])) })
        percent("high_low_vc") { r2((it["c_hi"] - it["c_lo"]) / (it["x_hi"] - it["x_lo"])) }
        percent("poh_rate") { r2(it["boh"] / it["base"]) }
        // ── 경제학 ──
        percent("elast_mid") { p ->
            r2(kotlin.math.abs(((p["q2"] - p["q1"]) / ((p["q1"] + p["q2"]) / 2)) / ((p["p2"] - p["p1"]) / ((p["p1"] + p["p2"]) / 2))))
        }
        percent("spend_mult") { r2(1 / (1 - pc(it["mpc"]))) }
        percent("tax_mult") { r2(-pc(it["mpc"]) / (1 - pc(it["mpc"]))) }
        percent("money_mult") { r2(100 / it["rr"]) }
        percent("unemp") { r2(it["u"] / (it["u"] + it["e"]) * 100) }
        percent("deflator") { r2(it["ngdp"] / it["rgdp"] * 100) }
        percent("monopoly_q") { r2((it["a"] - it["c"]) / (2 * it["b"])) }
        percent("cournot_q") { r2((it["a"] - it["c"]) / (3 * it["b"])) }
        percent("comp_q") { r2((it["a"] - it["c"]) / it["b"]) }
        percent("irp_fwd") { r2(it["s"] * (1 + pc(it["id_"])) / (1 + pc(it["if_"]))) }
        // ── 기업법(상법) ──
        put("res_ordinary_min", Formula(Kind.QTY) {
            val base = it["total"] - it["nonvote"]
            max(floor(it["present"] / 2) + 1, ceil(base / 4))
        })
        put("res_special_min", Formula(Kind.QTY) {
            val base = it["total"] - it["nonvote"]
            max(ceil(it["present"] * 2 / 3), ceil(base / 3))
        })
        amount("div_capacity") { it["na"] - it["cap"] - it["cres"] - it["lres"] - it["newres"] - it["unreal"] }
        amount("legal_reserve_min") { r0(min(it["div"] / 10, max(0.0, it["cap"] / 2 - it["lres"]))) }
        amount("reserve_reducible") { r0(max(0.0, it["cres"] + it["lres"] - it["cap"] * 1.5)) }
        amount("stock_div_max") { r0(it["total_div"] / 2) }
        // ── 농림어업 ──
        amount("bio_fvlcs") { it["fv"] - it["cts"] }
        amount("bio_change") { (it["fv1"] - it["cts1"]) - (it["fv0"] - it["cts0"]) }
    }

    private fun fifo(p: P): Pair<Double, Double> {
        val total = p["bq"] * p["bp"] + p["p1q"] * p["p1p"] + p["p2q"] * p["p2p"]
        var remaining = p["bq"] + p["p1q"] + p["p2q"] - p["sq"]
        var end = 0.0
        // 선입선출: 기말재고는 가장 나중에 매입한 원가층부터 남는다.
        for ((q, price) in listOf(p["p2q"] to p["p2p"], p["p1q"] to p["p1p"], p["bq"] to p["bp"])) {
            val take = min(remaining, q)
            end += take * price
            remaining -= take
        }
        return total to end
    }

    private fun totalAvg(p: P): Pair<Double, Double> {
        val qty = p["bq"] + p["p1q"] + p["p2q"]
        val cost = p["bq"] * p["bp"] + p["p1q"] * p["p1p"] + p["p2q"] * p["p2p"]
        return cost / qty to qty
    }

    /** 이동평균법: (매출원가, 기말재고) */
    private fun movingAvg(p: P): Pair<Double, Double> {
        val avg1 = (p["bq"] * p["bp"] + p["p1q"] * p["p1p"]) / (p["bq"] + p["p1q"])
        val left1 = p["bq"] + p["p1q"] - p["s1q"]
        val avg2 = (left1 * avg1 + p["p2q"] * p["p2p"]) / (left1 + p["p2q"])
        val cogs = p["s1q"] * avg1 + p["s2q"] * avg2
        val endQty = left1 + p["p2q"] - p["s2q"]
        return cogs to endQty * avg2
    }

    private data class Retail(val ratio: Double, val endRetail: Double)

    /**
     * 소매재고법. method: 1 평균원가, 2 저가기준(전통적), 3 선입선출, 4 저가기준 선입선출.
     * 원가율 분모: 순인상은 모든 방법에 포함, 순인하는 저가기준(2·4)에서 제외, 기초재고는 선입선출(3·4)에서 제외.
     * 비정상파손은 원가·매가 양쪽에서 제외하고, 기말 매가에서는 종업원할인·정상파손을 차감한다.
     */
    private fun retail(p: P): Retail {
        val m = p["method"].toInt()
        val includeOpening = m == 1 || m == 2
        val includeMarkdown = m == 1 || m == 3
        val num = (if (includeOpening) p["bic"] else 0.0) + p["pc"] - p["abc"]
        var den = (if (includeOpening) p["bir"] else 0.0) + p["pr"] - p["abr"] + p["mu"] - p["muc"]
        if (includeMarkdown) den += -p["md"] + p["mdc"]
        val endRetail = p["bir"] + p["pr"] - p["abr"] + p["mu"] - p["muc"] - p["md"] + p["mdc"] -
            (p["sales"] - p["sret"]) - p["emp"] - p["norm"]
        return Retail(num / den, endRetail)
    }

    fun has(formulaId: String): Boolean = formulaId in F
    fun kind(formulaId: String): Kind? = F[formulaId]?.kind
    val formulaIds: Set<String> get() = F.keys

    fun evaluate(formulaId: String, params: Map<String, Double>): Double {
        val f = F[formulaId] ?: throw IllegalArgumentException("알 수 없는 공식: $formulaId")
        return f.fn(P(params))
    }

    /** 콘텐츠 정답과 같은 형식(천 단위 쉼표, 비율은 소수 둘째 자리까지·끝 0 제거)으로 표기 */
    fun format(v: Double): String {
        val bd = BigDecimal(v.toString())
        return if (bd.stripTrailingZeros().scale() <= 0) {
            String.format(Locale.US, "%,d", bd.setScale(0, RoundingMode.HALF_UP).toLong())
        } else {
            val s = String.format(Locale.US, "%,.2f", bd.setScale(2, RoundingMode.HALF_UP))
            s.trimEnd('0').trimEnd('.')
        }
    }
}

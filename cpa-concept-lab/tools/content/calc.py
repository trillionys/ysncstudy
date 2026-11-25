"""계산문제용 공식 모듈.

각 공식은 (값, 풀이과정 문자열)을 반환한다. 계산문제의 정답은 사람이 문자열로 적지 않고
이 공식으로 생성하며, 앱에는 formulaId 와 params 가 함께 저장된다.
앱 쪽 Kotlin FormulaEngine 이 같은 공식을 독립적으로 다시 구현해 모든 계산문제의 정답을 검산한다
(단위 테스트 FormulaVerificationTest).

반올림 정책: 금액은 원 단위 사사오입(HALF_UP), 비율(%)은 소수 둘째 자리 사사오입.
"""
from decimal import Decimal, ROUND_HALF_UP


def r0(x):
    return int(Decimal(repr(x)).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def r2(x):
    v = Decimal(repr(x)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    return float(v)


def fmt(v):
    if isinstance(v, float) and not v.is_integer():
        s = f"{v:,.2f}".rstrip("0").rstrip(".")
        return s
    return f"{int(v):,}"


F = {}  # formulaId -> (function, kind) kind: "amount"(원 단위 반올림) | "percent" | "qty"


def formula(kind="amount"):
    def deco(fn):
        F[fn.__name__] = (fn, kind)
        return fn
    return deco


# ───────── 재고자산 ─────────
@formula()
def net_purchase(gross, ret, allow, disc, freight):
    v = gross - ret - allow - disc + freight
    return v, f"순매입액 = 총매입 {fmt(gross)} − 환출 {fmt(ret)} − 에누리 {fmt(allow)} − 할인 {fmt(disc)} + 매입운임 {fmt(freight)} = {fmt(v)}"


@formula()
def cogs(bi, purch, ei):
    v = bi + purch - ei
    return v, f"매출원가 = 기초 {fmt(bi)} + 순매입 {fmt(purch)} − 기말 {fmt(ei)} = {fmt(v)}"


@formula()
def goods_available(bi, purch):
    v = bi + purch
    return v, f"판매가능재고 = 기초 {fmt(bi)} + 순매입 {fmt(purch)} = {fmt(v)}"


def _fifo(bq, bp, p1q, p1p, p2q, p2p, sq):
    total_cost = bq * bp + p1q * p1p + p2q * p2p
    eq = bq + p1q + p2q - sq
    rem = eq
    end = 0
    for q, p in ((p2q, p2p), (p1q, p1p), (bq, bp)):
        take = min(rem, q)
        end += take * p
        rem -= take
    return total_cost, eq, end


@formula()
def fifo_end(bq, bp, p1q, p1p, p2q, p2p, sq):
    tc, eq, end = _fifo(bq, bp, p1q, p1p, p2q, p2p, sq)
    return end, (f"기말수량 = {fmt(bq + p1q + p2q)} − 판매 {fmt(sq)} = {fmt(eq)}개 → 가장 최근 매입분부터 기말재고로 남긴다"
                 f"(2차 매입 @{fmt(p2p)}, 1차 매입 @{fmt(p1p)} 순) → 기말재고 {fmt(end)}")


@formula()
def fifo_cogs(bq, bp, p1q, p1p, p2q, p2p, sq):
    tc, eq, end = _fifo(bq, bp, p1q, p1p, p2q, p2p, sq)
    v = tc - end
    return v, f"판매가능원가 {fmt(tc)} − 선입선출 기말재고 {fmt(end)} = 매출원가 {fmt(v)}"


@formula()
def tavg_end(bq, bp, p1q, p1p, p2q, p2p, sq):
    tc = bq * bp + p1q * p1p + p2q * p2p
    tq = bq + p1q + p2q
    unit = tc / tq
    v = r0(unit * (tq - sq))
    return v, f"총평균단가 = {fmt(tc)} ÷ {fmt(tq)}개 = {fmt(r2(unit))} → 기말재고 = {fmt(tq - sq)}개 × 단가 = {fmt(v)}"


@formula()
def tavg_cogs(bq, bp, p1q, p1p, p2q, p2p, sq):
    tc = bq * bp + p1q * p1p + p2q * p2p
    tq = bq + p1q + p2q
    unit = tc / tq
    v = r0(unit * sq)
    return v, f"총평균단가 = {fmt(tc)} ÷ {fmt(tq)}개 = {fmt(r2(unit))} → 매출원가 = 판매 {fmt(sq)}개 × 단가 = {fmt(v)}"


def _mavg(bq, bp, p1q, p1p, s1q, p2q, p2p, s2q):
    a1 = (bq * bp + p1q * p1p) / (bq + p1q)
    r1 = bq + p1q - s1q
    a2 = (r1 * a1 + p2q * p2p) / (r1 + p2q)
    cogs_v = s1q * a1 + s2q * a2
    end_q = r1 + p2q - s2q
    return a1, a2, cogs_v, end_q * a2, end_q


@formula()
def mavg_cogs(bq, bp, p1q, p1p, s1q, p2q, p2p, s2q):
    a1, a2, c, e, eq = _mavg(bq, bp, p1q, p1p, s1q, p2q, p2p, s2q)
    v = r0(c)
    return v, (f"1차 이동평균단가 = ({fmt(bq * bp)} + {fmt(p1q * p1p)}) ÷ {fmt(bq + p1q)}개 = {fmt(r2(a1))}, "
               f"1차 판매원가 = {fmt(s1q)}개 × {fmt(r2(a1))}; 2차 매입 후 단가 = {fmt(r2(a2))}, 2차 판매원가 = {fmt(s2q)}개 × {fmt(r2(a2))} → 매출원가 합계 {fmt(v)}")


@formula()
def mavg_end(bq, bp, p1q, p1p, s1q, p2q, p2p, s2q):
    a1, a2, c, e, eq = _mavg(bq, bp, p1q, p1p, s1q, p2q, p2p, s2q)
    v = r0(e)
    return v, f"마지막 이동평균단가 {fmt(r2(a2))} × 기말수량 {fmt(eq)}개 = 기말재고 {fmt(v)}"


@formula()
def inv_err_ni(d_bi, d_ei):
    v = d_ei - d_bi
    return v, (f"당기순이익 영향 = 기말재고 오류 {fmt(d_ei)} − 기초재고 오류 {fmt(d_bi)} = {fmt(v)} "
               "(양수면 이익 과대계상, 음수면 과소계상)")


@formula()
def inv_err_cogs(d_bi, d_ei):
    v = d_bi - d_ei
    return v, f"매출원가 영향 = 기초재고 오류 {fmt(d_bi)} − 기말재고 오류 {fmt(d_ei)} = {fmt(v)} (양수면 과대계상)"


@formula()
def count_adjust(counted, transit_fob_sp, consign_out, consign_in, sold_not_delivered, sale_return_exp):
    v = counted + transit_fob_sp + consign_out - consign_in - sold_not_delivered + sale_return_exp
    return v, (f"수정 기말재고 = 실사 {fmt(counted)} + 선적지조건 미착품 {fmt(transit_fob_sp)} + 적송품 {fmt(consign_out)} "
               f"− 수탁상품 {fmt(consign_in)} − 판매 후 미인도상품 {fmt(sold_not_delivered)} + 반품예상재고 {fmt(sale_return_exp)} = {fmt(v)}")


@formula("qty")
def shrink_qty(bookq, actq):
    v = bookq - actq
    return v, f"감모수량 = 장부수량 {fmt(bookq)} − 실사수량 {fmt(actq)} = {fmt(v)}개"


@formula()
def shrink_loss(bookq, actq, unit):
    v = (bookq - actq) * unit
    return v, f"감모손실 = ({fmt(bookq)} − {fmt(actq)})개 × 단위원가 {fmt(unit)} = {fmt(v)}"


@formula()
def normal_shrink(bookq, actq, normq, unit):
    v = normq * unit
    return v, f"정상감모손실 = 정상감모 {fmt(normq)}개 × {fmt(unit)} = {fmt(v)} (총감모 {fmt(bookq - actq)}개 중)"


@formula()
def abnormal_shrink(bookq, actq, normq, unit):
    v = (bookq - actq - normq) * unit
    return v, f"비정상감모손실 = (총감모 {fmt(bookq - actq)} − 정상감모 {fmt(normq)})개 × {fmt(unit)} = {fmt(v)}"


@formula()
def nrv_unit(price, complete, sell):
    v = price - complete - sell
    return v, f"순실현가능가치 = 예상판매가격 {fmt(price)} − 예상완성원가 {fmt(complete)} − 판매원가 {fmt(sell)} = {fmt(v)}"


@formula()
def writedown(actq, cost, nrv):
    v = actq * max(0, cost - nrv)
    return v, f"평가손실 = 실사수량 {fmt(actq)}개 × (원가 {fmt(cost)} − NRV {fmt(nrv)}) = {fmt(v)} (원가 ≤ NRV 이면 0)"


@formula()
def inv_carrying(actq, cost, nrv):
    v = actq * min(cost, nrv)
    return v, f"기말 장부금액 = 실사수량 {fmt(actq)}개 × Min(원가 {fmt(cost)}, NRV {fmt(nrv)}) = {fmt(v)}"


@formula()
def wd_reversal(prev_allow, actq, cost, nrv):
    need = actq * max(0, cost - nrv)
    v = max(0, prev_allow - need)
    return v, (f"기말 필요 충당금 = {fmt(actq)}개 × Max(0, {fmt(cost)} − {fmt(nrv)}) = {fmt(need)} → "
               f"환입액 = 기초 충당금 {fmt(prev_allow)} − 필요 충당금 {fmt(need)} = {fmt(v)} (음수면 0, 추가 손실 인식)")


@formula()
def inv_total_pl(bookq, actq, cost, nrv):
    v = (bookq - actq) * cost + actq * max(0, cost - nrv)
    return v, (f"감모손실 {fmt((bookq - actq) * cost)} + 평가손실 {fmt(actq * max(0, cost - nrv))} = 비용 합계 {fmt(v)} "
               "(감모 먼저 측정 후 남은 실제 수량에 평가손실 적용)")


@formula()
def cogs_with_losses(bi, purch, bookq, cost, actq, nrv, normq):
    ending_book = bookq * cost
    v = bi + purch - ending_book + normq * cost + actq * max(0, cost - nrv)
    return v, (f"장부상 매출원가 = {fmt(bi)} + {fmt(purch)} − 장부재고 {fmt(ending_book)} = {fmt(bi + purch - ending_book)}; "
               f"+ 정상감모 {fmt(normq * cost)} + 평가손실 {fmt(actq * max(0, cost - nrv))} = {fmt(v)} "
               "(교재 관행: 정상감모·평가손실을 매출원가에 포함, 비정상감모는 기타비용)")


# ───────── 매출총이익률법·소매재고법 ─────────
@formula()
def gp_ending(bi, purch, sales, gpr):
    est_cogs = sales * (100 - gpr) / 100
    v = r0(bi + purch - est_cogs)
    return v, (f"추정 매출원가 = 순매출 {fmt(sales)} × (1 − {fmt(gpr)}%) = {fmt(r0(est_cogs))} → "
               f"추정 기말재고 = {fmt(bi)} + {fmt(purch)} − {fmt(r0(est_cogs))} = {fmt(v)}")


@formula()
def gp_fire_loss(bi, purch, sales, gpr, salvage):
    est_cogs = sales * (100 - gpr) / 100
    v = r0(bi + purch - est_cogs - salvage)
    return v, f"추정 기말재고 {fmt(r0(bi + purch - est_cogs))} − 남은 재고 {fmt(salvage)} = 화재손실 추정액 {fmt(v)}"


@formula("percent")
def markup_to_gpr(markup):
    v = r2(markup / (100 + markup) * 100)
    return v, f"매출총이익률 = 원가 기준 이익률 {fmt(markup)} ÷ (100 + {fmt(markup)}) × 100 = {fmt(v)}%"


@formula("percent")
def gpr_to_markup(gpr):
    v = r2(gpr / (100 - gpr) * 100)
    return v, f"원가 기준 이익률 = 매출총이익률 {fmt(gpr)} ÷ (100 − {fmt(gpr)}) × 100 = {fmt(v)}%"


RETAIL_NAMES = {1: "평균원가 소매재고법", 2: "저가기준(전통적) 소매재고법", 3: "선입선출 소매재고법", 4: "저가기준 선입선출 소매재고법"}


def _retail(method, bic, bir, pc, pr, mu, muc, md, mdc, sales, sret, emp, norm, abc, abr):
    method = int(method)
    num = (bic if method in (1, 2) else 0) + pc - abc
    den = (bir if method in (1, 2) else 0) + pr - abr + mu - muc
    if method in (1, 3):
        den += -md + mdc
    ratio = num / den
    end_r = bir + pr - abr + mu - muc - md + mdc - (sales - sret) - emp - norm
    return num, den, ratio, end_r


@formula("percent")
def retail_ratio(method, bic, bir, pc, pr, mu, muc, md, mdc, sales, sret, emp, norm, abc, abr):
    num, den, ratio, end_r = _retail(method, bic, bir, pc, pr, mu, muc, md, mdc, sales, sret, emp, norm, abc, abr)
    v = r2(ratio * 100)
    return v, f"{RETAIL_NAMES[int(method)]} 원가율 = 원가 {fmt(num)} ÷ 매가 {fmt(den)} = {fmt(v)}%"


@formula()
def retail_end_retail(method, bic, bir, pc, pr, mu, muc, md, mdc, sales, sret, emp, norm, abc, abr):
    num, den, ratio, end_r = _retail(method, bic, bir, pc, pr, mu, muc, md, mdc, sales, sret, emp, norm, abc, abr)
    return end_r, (f"기말재고 매가 = 기초 {fmt(bir)} + 매입 {fmt(pr)} − 비정상파손 {fmt(abr)} + 순인상 {fmt(mu - muc)} − 순인하 {fmt(md - mdc)} "
                   f"− 순매출 {fmt(sales - sret)} − 종업원할인 {fmt(emp)} − 정상파손 {fmt(norm)} = {fmt(end_r)}")


@formula()
def retail_end_cost(method, bic, bir, pc, pr, mu, muc, md, mdc, sales, sret, emp, norm, abc, abr):
    num, den, ratio, end_r = _retail(method, bic, bir, pc, pr, mu, muc, md, mdc, sales, sret, emp, norm, abc, abr)
    v = r0(end_r * ratio)
    return v, (f"원가율 = {fmt(num)} ÷ {fmt(den)} = {fmt(r2(ratio * 100))}%, 기말재고 매가 = {fmt(end_r)} → "
               f"기말재고 원가 = {fmt(end_r)} × 원가율 = {fmt(v)}")


@formula()
def retail_cogs(method, bic, bir, pc, pr, mu, muc, md, mdc, sales, sret, emp, norm, abc, abr):
    num, den, ratio, end_r = _retail(method, bic, bir, pc, pr, mu, muc, md, mdc, sales, sret, emp, norm, abc, abr)
    end_c = r0(end_r * ratio)
    v = bic + pc - abc - end_c
    return v, (f"매출원가 = (기초원가 {fmt(bic)} + 매입원가 {fmt(pc)} − 비정상파손 원가 {fmt(abc)}) − 기말재고 원가 {fmt(end_c)} = {fmt(v)}")


@formula()
def joint_alloc(joint, sva, svb):
    v = r0(joint * sva / (sva + svb))
    return v, f"A 배분액 = 결합원가 {fmt(joint)} × {fmt(sva)} ÷ ({fmt(sva)} + {fmt(svb)}) = {fmt(v)}"


@formula()
def joint_nrv_alloc(joint, sva, costa, svb, costb):
    na, nb = sva - costa, svb - costb
    v = r0(joint * na / (na + nb))
    return v, f"A 순실현가능가치 = {fmt(sva)} − {fmt(costa)} = {fmt(na)}, B = {fmt(nb)} → A 배분액 = {fmt(joint)} × {fmt(na)} ÷ {fmt(na + nb)} = {fmt(v)}"


# ───────── 유형자산 ─────────
@formula()
def lump_alloc(total, fva, fvb):
    v = r0(total * fva / (fva + fvb))
    return v, f"A 원가 = 일괄구입가 {fmt(total)} × {fmt(fva)} ÷ ({fmt(fva)} + {fmt(fvb)}) = {fmt(v)}"


@formula()
def pv_single(amount, rate, years):
    v = r0(amount / (1 + rate / 100) ** years)
    return v, f"현재가치 = {fmt(amount)} ÷ (1 + {fmt(rate)}%)^{fmt(years)} = {fmt(v)}"


@formula()
def pv_interest1(amount, rate, years):
    pv = r0(amount / (1 + rate / 100) ** years)
    v = r0(pv * rate / 100)
    return v, f"최초 현재가치 {fmt(pv)} × {fmt(rate)}% = 첫해 이자(할인액 상각) {fmt(v)}"


@formula()
def dep_sl(cost, rv, life, months):
    v = r0((cost - rv) / life * months / 12)
    return v, f"정액법 = ({fmt(cost)} − {fmt(rv)}) ÷ {fmt(life)}년 × {fmt(months)}/12 = {fmt(v)}"


@formula()
def dep_ddb(cost, life, year):
    rate = 2 / life
    bv = cost * (1 - rate) ** (year - 1)
    v = r0(bv * rate)
    return v, f"이중체감법 상각률 = 2 ÷ {fmt(life)} = {fmt(r2(rate * 100))}%, {fmt(year)}차 연도 기초 장부금액 {fmt(r0(bv))} × 상각률 = {fmt(v)}"


@formula()
def dep_db(cost, rate, year):
    bv = cost * (1 - rate / 100) ** (year - 1)
    v = r0(bv * rate / 100)
    return v, f"정률법 {fmt(year)}차 연도 = 기초 장부금액 {fmt(r0(bv))} × {fmt(rate)}% = {fmt(v)}"


@formula()
def dep_syd(cost, rv, life, year):
    s = life * (life + 1) / 2
    v = r0((cost - rv) * (life - year + 1) / s)
    return v, f"연수합계법 = ({fmt(cost)} − {fmt(rv)}) × {fmt(life - year + 1)}/{fmt(s)} = {fmt(v)}"


@formula()
def dep_units(cost, rv, total, used):
    v = r0((cost - rv) / total * used)
    return v, f"생산량비례법 = ({fmt(cost)} − {fmt(rv)}) ÷ 총 {fmt(total)} × 당기 {fmt(used)} = {fmt(v)}"


@formula()
def dep_change(cost, rv, life, used, newrv, remaining):
    carrying = cost - (cost - rv) / life * used
    v = r0((carrying - newrv) / remaining)
    return v, (f"변경 시점 장부금액 = {fmt(cost)} − ({fmt(cost)} − {fmt(rv)}) ÷ {fmt(life)} × {fmt(used)} = {fmt(r0(carrying))} → "
               f"변경 후 연 감가상각비 = ({fmt(r0(carrying))} − {fmt(newrv)}) ÷ 잔여 {fmt(remaining)}년 = {fmt(v)} (전진 적용)")


@formula()
def carrying_cost_model(cost, accdep, accimp):
    v = cost - accdep - accimp
    return v, f"장부금액 = 취득원가 {fmt(cost)} − 감가상각누계액 {fmt(accdep)} − 손상차손누계액 {fmt(accimp)} = {fmt(v)}"


@formula()
def grant_net_dep(cost, grant, rv, life):
    v = r0((cost - grant - rv) / life)
    return v, f"자산차감법 순감가상각비 = ({fmt(cost)} − 보조금 {fmt(grant)} − {fmt(rv)}) ÷ {fmt(life)} = {fmt(v)}"


@formula()
def grant_disposal_gain(cost, grant, life, years, proceeds):
    carrying = (cost - grant) * (1 - years / life)
    v = r0(proceeds - carrying)
    return v, (f"보조금 차감 후 장부금액 = ({fmt(cost)} − {fmt(grant)}) × (1 − {fmt(years)}/{fmt(life)}) = {fmt(r0(carrying))} → "
               f"처분손익 = {fmt(proceeds)} − {fmt(r0(carrying))} = {fmt(v)}")


@formula()
def exch_cost(fvgiven, cashpaid):
    v = fvgiven + cashpaid
    return v, f"취득원가 = 제공자산 공정가치 {fmt(fvgiven)} + 지급현금 {fmt(cashpaid)} = {fmt(v)} (수령 현금은 음수)"


@formula()
def exch_gain(fvgiven, carrying):
    v = fvgiven - carrying
    return v, f"교환손익 = 제공자산 공정가치 {fmt(fvgiven)} − 장부금액 {fmt(carrying)} = {fmt(v)}"


@formula()
def exch_nocs_cost(carrying, cashpaid):
    v = carrying + cashpaid
    return v, f"상업적 실질이 없는 교환: 취득원가 = 제공자산 장부금액 {fmt(carrying)} + 지급현금 {fmt(cashpaid)} = {fmt(v)} (교환손익 없음)"


@formula()
def reval_oci_up(carrying, fv, priorloss):
    inc = fv - carrying
    pl = min(priorloss, inc)
    v = inc - pl
    return v, f"재평가 증가 {fmt(inc)} 중 과거 당기손실 환입 {fmt(pl)}은 당기이익, 나머지 {fmt(v)}는 재평가잉여금(OCI)"


@formula()
def reval_pl_up(carrying, fv, priorloss):
    inc = fv - carrying
    v = min(priorloss, inc)
    return v, f"재평가 증가 {fmt(inc)} 중 과거에 당기손실로 인식한 {fmt(priorloss)} 한도 내 금액 {fmt(v)}을 당기이익으로 인식"


@formula()
def reval_pl_down(carrying, fv, surplus):
    dec = carrying - fv
    oci = min(surplus, dec)
    v = dec - oci
    return v, f"재평가 감소 {fmt(dec)} 중 잉여금 {fmt(surplus)} 한도 {fmt(oci)}은 OCI 감소, 나머지 {fmt(v)}는 당기손실"


@formula()
def reval_dep(fv, rv, remaining):
    v = r0((fv - rv) / remaining)
    return v, f"재평가 후 감가상각비 = (재평가금액 {fmt(fv)} − {fmt(rv)}) ÷ 잔여 {fmt(remaining)}년 = {fmt(v)}"


@formula()
def reval_transfer(fv, remaining, cost, life):
    a = fv / remaining
    b = cost / life
    v = r0(a - b)
    return v, f"재평가 기준 상각비 {fmt(r0(a))} − 원가 기준 상각비 {fmt(r0(b))} = 이익잉여금 대체 가능액 {fmt(v)}"


@formula()
def recoverable(viu, fvlcd):
    v = max(viu, fvlcd)
    return v, f"회수가능액 = Max(사용가치 {fmt(viu)}, 순공정가치 {fmt(fvlcd)}) = {fmt(v)}"


@formula()
def impairment(carrying, viu, fvlcd):
    rec = max(viu, fvlcd)
    v = max(0, carrying - rec)
    return v, f"회수가능액 = Max({fmt(viu)}, {fmt(fvlcd)}) = {fmt(rec)} → 손상차손 = Max(0, 장부금액 {fmt(carrying)} − {fmt(rec)}) = {fmt(v)}"


@formula()
def imp_reversal(cost, life, t1, rec1, t2, rec2):
    dep_after = rec1 / (life - t1)
    carrying2 = rec1 - dep_after * (t2 - t1)
    wouldbe = cost * (1 - t2 / life)
    v = r0(max(0, min(rec2, wouldbe) - carrying2))
    return v, (f"손상 후 연 상각비 = {fmt(rec1)} ÷ {fmt(life - t1)} = {fmt(r0(dep_after))}, {fmt(t2)}년 말 장부금액 = {fmt(r0(carrying2))}; "
               f"손상이 없었다면 장부금액 = {fmt(cost)} × (1 − {fmt(t2)}/{fmt(life)}) = {fmt(r0(wouldbe))} → "
               f"환입액 = Min(회수가능액 {fmt(rec2)}, 한도 {fmt(r0(wouldbe))}) − {fmt(r0(carrying2))} = {fmt(v)}")


@formula()
def aro_change_cost(carrying, increase):
    v = carrying + increase
    return v, f"원가모형: 복구충당부채 증가 {fmt(increase)}를 자산 원가에 가산 → 조정 후 장부금액 {fmt(v)}"


@formula()
def disposal_gain(proceeds, costs, cost, accdep):
    v = proceeds - costs - (cost - accdep)
    return v, f"처분손익 = (처분대가 {fmt(proceeds)} − 처분원가 {fmt(costs)}) − 장부금액({fmt(cost)} − {fmt(accdep)}) = {fmt(v)}"


@formula()
def hfs_measure(carrying, fv, sellcost):
    v = min(carrying, fv - sellcost)
    return v, f"매각예정 측정액 = Min(장부금액 {fmt(carrying)}, 순공정가치 {fmt(fv - sellcost)}) = {fmt(v)}"


@formula()
def hfs_loss(carrying, fv, sellcost):
    v = max(0, carrying - (fv - sellcost))
    return v, f"손상차손 = Max(0, {fmt(carrying)} − ({fmt(fv)} − {fmt(sellcost)})) = {fmt(v)}"


# ───────── 차입원가 ─────────
@formula("percent")
def bc_rate(a, ar, am, b, br, bm):
    interest = a * ar / 100 * am / 12 + b * br / 100 * bm / 12
    principal = a * am / 12 + b * bm / 12
    v = r2(interest / principal * 100)
    return v, (f"연평균 차입금 = {fmt(a)}×{fmt(am)}/12 + {fmt(b)}×{fmt(bm)}/12 = {fmt(r0(principal))}, "
               f"이자 = {fmt(r0(interest))} → 자본화이자율 = {fmt(r0(interest))} ÷ {fmt(r0(principal))} = {fmt(v)}%")


@formula()
def bc_avg_exp(e1, m1, e2, m2, e3, m3):
    v = r0(e1 * m1 / 12 + e2 * m2 / 12 + e3 * m3 / 12)
    return v, (f"연평균 지출액 = {fmt(e1)}×{fmt(m1)}/12 + {fmt(e2)}×{fmt(m2)}/12 + {fmt(e3)}×{fmt(m3)}/12 = {fmt(v)} "
               "(지출일 또는 자본화 개시일 중 늦은 날부터 자본화 종료일·연말까지 기간)")


@formula()
def bc_specific(amt, rate, m, inv):
    v = r0(amt * rate / 100 * m / 12 - inv)
    return v, f"특정차입금 자본화액 = {fmt(amt)} × {fmt(rate)}% × {fmt(m)}/12 − 일시운용수익 {fmt(inv)} = {fmt(v)}"


@formula()
def bc_general(avgexp, specavg, rate, limit):
    raw = max(0, (avgexp - specavg) * rate / 100)
    v = r0(min(raw, limit))
    return v, (f"일반차입금 자본화액 = (연평균 지출 {fmt(avgexp)} − 특정차입금 연평균 {fmt(specavg)}) × {fmt(rate)}% = {fmt(r0(raw))}, "
               f"한도(실제 일반차입금 이자) {fmt(limit)} → {fmt(v)}")


@formula()
def bc_total(avgexp, samt, srate, sm, inv, grate, glimit):
    spec = samt * srate / 100 * sm / 12 - inv
    specavg = samt * sm / 12
    gen = min((avgexp - specavg) * grate / 100, glimit)
    v = r0(spec + max(0, gen))
    return v, (f"특정 = {fmt(samt)}×{fmt(srate)}%×{fmt(sm)}/12 − {fmt(inv)} = {fmt(r0(spec))}; "
               f"일반 = Min(({fmt(avgexp)} − {fmt(r0(specavg))}) × {fmt(grate)}%, {fmt(glimit)}) = {fmt(r0(max(0, gen)))} → 합계 {fmt(v)}")


# ───────── 농림어업 ─────────
@formula()
def bio_fvlcs(fv, cts):
    v = fv - cts
    return v, f"순공정가치 = 공정가치 {fmt(fv)} − 처분부대원가 {fmt(cts)} = {fmt(v)}"


@formula()
def bio_change(fv1, cts1, fv0, cts0):
    v = (fv1 - cts1) - (fv0 - cts0)
    return v, f"평가손익 = 기말 순공정가치 {fmt(fv1 - cts1)} − 기초 순공정가치 {fmt(fv0 - cts0)} = {fmt(v)}"


# ───────── 기업법(상법) 계산 ─────────
import math


@formula("qty")
def res_ordinary_min(total, nonvote, present):
    """보통결의 가결에 필요한 최소 찬성 의결권 수(제368조①, 제371조①): 출석 과반수 + 발행주식총수(의결권 있는)의 4분의 1 이상."""
    base = total - nonvote
    v = max(math.floor(present / 2) + 1, math.ceil(base / 4))
    return v, (f"출석 의결권 {fmt(present)}의 과반수 = {fmt(math.floor(present / 2) + 1)}, "
               f"(발행주식총수 {fmt(total)} − 의결권 없는 주식 {fmt(nonvote)}) × 1/4 = {fmt(math.ceil(base / 4))} → 둘 중 큰 수 {fmt(v)}")


@formula("qty")
def res_special_min(total, nonvote, present):
    """특별결의 가결에 필요한 최소 찬성 의결권 수(제434조, 제371조①): 출석 3분의 2 이상 + 발행주식총수 3분의 1 이상."""
    base = total - nonvote
    a = math.ceil(present * 2 / 3)
    b = math.ceil(base / 3)
    v = max(a, b)
    return v, (f"출석 의결권 {fmt(present)} × 2/3 = {fmt(a)}, (발행주식총수 {fmt(total)} − 의결권 없는 주식 {fmt(nonvote)}) × 1/3 = {fmt(b)} "
               f"→ 둘 중 큰 수 {fmt(v)}")


@formula()
def div_capacity(na, cap, cres, lres, newres, unreal):
    """배당가능이익(제462조①): 순자산 − 자본금 − 자본준비금·이익준비금 합계 − 당기 적립할 이익준비금 − 미실현이익."""
    v = na - cap - cres - lres - newres - unreal
    return v, (f"순자산 {fmt(na)} − 자본금 {fmt(cap)} − 자본준비금 {fmt(cres)} − 이익준비금 {fmt(lres)} − 적립할 이익준비금 {fmt(newres)} "
               f"− 미실현이익 {fmt(unreal)} = {fmt(v)}")


@formula()
def legal_reserve_min(div, cap, lres):
    """적립할 이익준비금 최소액(제458조): 이익배당액(금전)의 10분의 1, 다만 이익준비금이 자본금의 2분의 1에 이를 때까지."""
    need = max(0, cap / 2 - lres)
    v = r0(min(div / 10, need))
    return v, f"Min(배당액 {fmt(div)} × 1/10 = {fmt(r0(div / 10))}, 자본금의 1/2 {fmt(r0(cap / 2))} − 기적립 이익준비금 {fmt(lres)} = {fmt(r0(need))}) = {fmt(v)}"


@formula()
def reserve_reducible(cres, lres, cap):
    """준비금 감소 가능액(제461조의2): 자본준비금·이익준비금 총액이 자본금의 1.5배를 초과하는 금액."""
    v = r0(max(0, cres + lres - cap * 1.5))
    return v, f"(자본준비금 {fmt(cres)} + 이익준비금 {fmt(lres)}) − 자본금 {fmt(cap)} × 1.5 = {fmt(v)}"


@formula()
def stock_div_max(total_div):
    """주식배당 한도(제462조의2①): 이익배당총액의 2분의 1."""
    v = r0(total_div / 2)
    return v, f"이익배당총액 {fmt(total_div)} × 1/2 = {fmt(v)}"



# ───────── 재무관리 ─────────
def _pct(x):
    return x / 100.0


@formula()
def fv(pv, r, n):
    v = r0(pv * (1 + _pct(r)) ** n)
    return v, f"FV = {fmt(pv)} × (1 + {r}%)^{n} = {fmt(v)}"


@formula()
def pv(fv, r, n):
    v = r0(fv / (1 + _pct(r)) ** n)
    return v, f"PV = {fmt(fv)} ÷ (1 + {r}%)^{n} = {fmt(v)}"


@formula()
def annuity_pv(c, r, n):
    i = _pct(r)
    v = r0(c * (1 - (1 + i) ** -n) / i)
    return v, f"연금 현재가치 = {fmt(c)} × [1 − (1+{r}%)^−{n}] ÷ {r}% = {fmt(v)}"


@formula()
def growing_perp(c1, r, g):
    v = r0(c1 / (_pct(r) - _pct(g)))
    return v, f"성장영구연금 현재가치 = {fmt(c1)} ÷ ({r}% − {g}%) = {fmt(v)}"


@formula()
def npv3(c0, c1, c2, c3, r):
    i = _pct(r)
    v = r0(-c0 + c1 / (1 + i) + c2 / (1 + i) ** 2 + c3 / (1 + i) ** 3)
    return v, f"NPV = −{fmt(c0)} + {fmt(c1)}/(1+{r}%) + {fmt(c2)}/(1+{r}%)² + {fmt(c3)}/(1+{r}%)³ = {fmt(v)}"


@formula("percent")
def capm(rf, beta, rm):
    v = r2(rf + beta * (rm - rf))
    return v, f"요구수익률 = {rf}% + {beta} × ({rm}% − {rf}%) = {v}%"


@formula("percent")
def wacc(e, d, re, rd, t):
    v = r2((e * re + d * rd * (1 - _pct(t))) / (e + d))
    return v, f"WACC = ({fmt(e)}×{re}% + {fmt(d)}×{rd}%×(1−{t}%)) ÷ {fmt(e + d)} = {v}%"


@formula()
def bond_price(face, cr, y, n):
    i, c = _pct(y), face * _pct(cr)
    v = r0(sum(c / (1 + i) ** t for t in range(1, int(n) + 1)) + face / (1 + i) ** n)
    return v, f"채권가격 = Σ 이자 {fmt(c)}/(1+{y}%)^t + 액면 {fmt(face)}/(1+{y}%)^{n} = {fmt(v)}"


@formula("percent")
def eff_rate(apr, m):
    v = r2(((1 + _pct(apr) / m) ** m - 1) * 100)
    return v, f"실효이자율 = (1 + {apr}%/{m})^{m} − 1 = {v}%"


@formula("percent")
def port_ret(w1, r1, r2_):
    v = r2(_pct(w1) * r1 + (1 - _pct(w1)) * r2_)
    return v, f"포트폴리오 기대수익률 = {w1}%×{r1}% + {100 - w1}%×{r2_}% = {v}%"


@formula("percent")
def port_sd(w1, s1, s2, rho):
    a, b = _pct(w1), 1 - _pct(w1)
    v = r2((a * a * s1 * s1 + b * b * s2 * s2 + 2 * a * b * rho * s1 * s2) ** 0.5)
    return v, f"σp = √(w1²σ1² + w2²σ2² + 2w1w2ρσ1σ2) = {v}%"


@formula("percent")
def put_parity(c, s, k, r, t):
    v = r2(c - s + k / (1 + _pct(r)) ** t)
    return v, f"P = C − S + K/(1+r)^T = {c} − {s} + {k}/(1+{r}%)^{t} = {v}"


@formula("percent")
def fwd_rate(r1, r2y):
    v = r2(((1 + _pct(r2y)) ** 2 / (1 + _pct(r1)) - 1) * 100)
    return v, f"1년 후 1년 선도이자율 = (1+{r2y}%)² ÷ (1+{r1}%) − 1 = {v}%"


# ───────── 원가관리회계 ─────────
@formula("qty")
def bep_units(fc, p, vc):
    v = r0(fc / (p - vc))
    return v, f"손익분기점 판매량 = 고정원가 {fmt(fc)} ÷ 단위당 공헌이익({fmt(p)} − {fmt(vc)}) = {fmt(v)}"


@formula("qty")
def target_units(fc, ti, p, vc):
    v = r0((fc + ti) / (p - vc))
    return v, f"목표판매량 = (고정원가 {fmt(fc)} + 목표이익 {fmt(ti)}) ÷ 단위당 공헌이익 {fmt(p - vc)} = {fmt(v)}"


@formula("percent")
def cm_ratio(p, vc):
    v = r2((p - vc) / p * 100)
    return v, f"공헌이익률 = ({fmt(p)} − {fmt(vc)}) ÷ {fmt(p)} = {v}%"


@formula("percent")
def dol(cm, oi):
    v = r2(cm / oi)
    return v, f"영업레버리지도 = 공헌이익 {fmt(cm)} ÷ 영업이익 {fmt(oi)} = {v}"


@formula()
def price_var(aq, ap, sp):
    v = r0((ap - sp) * aq)
    return v, f"가격(임률)차이 = (실제가격 {fmt(ap)} − 표준가격 {fmt(sp)}) × 실제투입량 {fmt(aq)} = {fmt(v)} (양수 = 불리)"


@formula()
def qty_var(aq, sq, sp):
    v = r0((aq - sq) * sp)
    return v, f"수량(능률)차이 = (실제투입량 {fmt(aq)} − 표준투입량 {fmt(sq)}) × 표준가격 {fmt(sp)} = {fmt(v)} (양수 = 불리)"


@formula("qty")
def eoq(d, s, h):
    v = r0((2 * d * s / h) ** 0.5)
    return v, f"EOQ = √(2 × 연간수요 {fmt(d)} × 1회 주문비 {fmt(s)} ÷ 단위당 유지비 {fmt(h)}) = {fmt(v)}"


@formula("percent")
def roi(oi, ia):
    v = r2(oi / ia * 100)
    return v, f"ROI = 영업이익 {fmt(oi)} ÷ 투자액 {fmt(ia)} = {v}%"


@formula()
def ri(oi, ia, rr):
    v = r0(oi - ia * _pct(rr))
    return v, f"RI = 영업이익 {fmt(oi)} − 투자액 {fmt(ia)} × 최저필수수익률 {rr}% = {fmt(v)}"


@formula("qty")
def eu_wavg(done, end, pct):
    v = r0(done + end * _pct(pct))
    return v, f"평균법 완성품환산량 = 완성품 {fmt(done)} + 기말재공품 {fmt(end)} × {pct}% = {fmt(v)}"


@formula("qty")
def eu_fifo(done, beg, bpct, end, epct):
    v = r0(done - beg * _pct(bpct) + end * _pct(epct))
    return v, f"선입선출법 완성품환산량 = 완성품 {fmt(done)} − 기초재공품 {fmt(beg)}×{bpct}% + 기말재공품 {fmt(end)}×{epct}% = {fmt(v)}"


@formula("percent")
def high_low_vc(c_hi, c_lo, x_hi, x_lo):
    v = r2((c_hi - c_lo) / (x_hi - x_lo))
    return v, f"단위당 변동원가 = ({fmt(c_hi)} − {fmt(c_lo)}) ÷ ({fmt(x_hi)} − {fmt(x_lo)}) = {v}"


@formula("percent")
def poh_rate(boh, base):
    v = r2(boh / base)
    return v, f"예정배부율 = 예산 제조간접원가 {fmt(boh)} ÷ 예정 배부기준 {fmt(base)} = {v}"


# ───────── 경제학 ─────────
@formula("percent")
def elast_mid(q1, q2, p1, p2):
    v = r2(abs(((q2 - q1) / ((q1 + q2) / 2)) / ((p2 - p1) / ((p1 + p2) / 2))))
    return v, f"중간점 가격탄력성 = |(ΔQ/평균Q) ÷ (ΔP/평균P)| = {v}"


@formula("percent")
def spend_mult(mpc):
    v = r2(1 / (1 - _pct(mpc)))
    return v, f"정부지출승수 = 1 ÷ (1 − MPC {mpc}%) = {v}"


@formula("percent")
def tax_mult(mpc):
    v = r2(-_pct(mpc) / (1 - _pct(mpc)))
    return v, f"조세승수 = −MPC ÷ (1 − MPC) = {v}"


@formula("percent")
def money_mult(rr):
    v = r2(100 / rr)
    return v, f"단순 통화승수 = 1 ÷ 지급준비율 {rr}% = {v}"


@formula("percent")
def unemp(u, e):
    v = r2(u / (u + e) * 100)
    return v, f"실업률 = 실업자 {fmt(u)} ÷ 경제활동인구 {fmt(u + e)} = {v}%"


@formula("percent")
def deflator(ngdp, rgdp):
    v = r2(ngdp / rgdp * 100)
    return v, f"GDP 디플레이터 = 명목GDP {fmt(ngdp)} ÷ 실질GDP {fmt(rgdp)} × 100 = {v}"


@formula("percent")
def monopoly_q(a, b, c):
    v = r2((a - c) / (2 * b))
    return v, f"독점 산출량: MR = {a} − 2×{b}Q = MC {c} → Q = {v}"


@formula("percent")
def cournot_q(a, b, c):
    v = r2((a - c) / (3 * b))
    return v, f"쿠르노 복점 기업별 산출량 = (a − c) ÷ 3b = ({a} − {c}) ÷ {3 * b} = {v}"


@formula("percent")
def comp_q(a, b, c):
    v = r2((a - c) / b)
    return v, f"완전경쟁 산출량: P = MC → {a} − {b}Q = {c} → Q = {v}"


@formula("percent")
def irp_fwd(s, id_, if_):
    v = r2(s * (1 + _pct(id_)) / (1 + _pct(if_)))
    return v, f"선물환율 = 현물환율 {s} × (1 + 자국이자율 {id_}%) ÷ (1 + 외국이자율 {if_}%) = {v}"



# ───────── 세법(TaxRuleEngine) ─────────
@formula()
def prog_tax(base, table):
    import tax_rules
    v = r0(tax_rules.progressive(int(table), base))
    name = next(t for t in tax_rules.TABLES if t["code"] == int(table))
    return v, f"{name['lawName']} {name['article']} 세율표(시행 {name['effectiveFrom']})를 과세표준 {fmt(base)}원에 구간별 적용 = {fmt(v)}"


@formula()
def corp_income(ni, add, disallow, exclude, deduct):
    v = r0(ni + add + disallow - exclude - deduct)
    return v, f"각 사업연도 소득 = 당기순이익 {fmt(ni)} + 익금산입 {fmt(add)} + 손금불산입 {fmt(disallow)} − 익금불산입 {fmt(exclude)} − 손금산입 {fmt(deduct)} = {fmt(v)}"


@formula()
def rate_of(amount, rate):
    v = r0(amount * rate / 100.0)
    return v, f"{fmt(amount)} × {rate}% = {fmt(v)}"


@formula()
def ent_base_limit(months, sme):
    v = r0((36000000 if sme else 12000000) * months / 12)
    return v, f"기본한도 = {'3천600만원' if sme else '1천200만원'} × {int(months)}/12 = {fmt(v)}"


@formula()
def donation_limit(base, loss, rate):
    v = r0(max(0, base - loss) * rate / 100.0)
    return v, f"손금산입한도 = (기준소득금액 {fmt(base)} − 이월결손금 {fmt(loss)}) × {rate}% = {fmt(v)}"


@formula()
def vat_payable(sales, rate, input_tax):
    v = r0(sales * rate / 100.0 - input_tax)
    return v, f"납부세액 = 매출세액({fmt(sales)} × {rate}%) − 매입세액 {fmt(input_tax)} = {fmt(v)}"


@formula()
def common_input_tax(common, exempt, total):
    v = r0(common * exempt / total)
    return v, f"면세사업분 불공제 = 공통매입세액 {fmt(common)} × 면세공급가액 {fmt(exempt)} ÷ 총공급가액 {fmt(total)} = {fmt(v)}"


@formula()
def minus(a, b):
    v = r0(a - b)
    return v, f"{fmt(a)} − {fmt(b)} = {fmt(v)}"


# ───────── v8 범용 계산(목차 보강 문항용 — 앱 FormulaEngine 이 같은 식으로 독립 검산) ─────────
@formula()
def add2(a, b):
    v = r0(a + b)
    return v, f"{fmt(a)} + {fmt(b)} = {fmt(v)}"


@formula()
def add3(a, b, c):
    v = r0(a + b + c)
    return v, f"{fmt(a)} + {fmt(b)} + {fmt(c)} = {fmt(v)}"


@formula()
def sub3(a, b, c):
    v = r0(a - b - c)
    return v, f"{fmt(a)} − {fmt(b)} − {fmt(c)} = {fmt(v)}"


@formula()
def adj(a, b, c):
    """a + b − c"""
    v = r0(a + b - c)
    return v, f"{fmt(a)} + {fmt(b)} − {fmt(c)} = {fmt(v)}"


@formula()
def pct_of(a, r):
    v = r0(a * r / 100)
    return v, f"{fmt(a)} × {fmt(r)}% = {fmt(v)}"


@formula()
def prorate(a, b, c):
    """a × b ÷ c"""
    v = r0(a * b / c)
    return v, f"{fmt(a)} × {fmt(b)} ÷ {fmt(c)} = {fmt(v)}"


@formula()
def diff_pct(a, b, r):
    """(a − b) × r%"""
    v = r0((a - b) * r / 100)
    return v, f"({fmt(a)} − {fmt(b)}) × {fmt(r)}% = {fmt(v)}"


@formula()
def eps(ni, pref, shares):
    v = r0((ni - pref) / shares)
    return v, f"(당기순이익 {fmt(ni)} − 우선주배당 {fmt(pref)}) ÷ 가중평균유통보통주식수 {fmt(shares)} = {fmt(v)}"


@formula(kind="percent")
def pct_ratio(a, b):
    """a ÷ b × 100 (%)"""
    v = r2(a / b * 100)
    return v, f"{fmt(a)} ÷ {fmt(b)} × 100 = {fmt(v)}%"


@formula(kind="percent")
def mult_ratio(a, b):
    """a ÷ b (배수, 소수 둘째 자리)"""
    v = r2(a / b)
    return v, f"{fmt(a)} ÷ {fmt(b)} = {fmt(v)}"


@formula()
def ev2(a, ra, b, rb):
    """두 결과의 기대값: a × ra% + b × rb% (나머지 결과는 0)"""
    v = r0(a * ra / 100 + b * rb / 100)
    return v, f"{fmt(a)} × {fmt(ra)}% + {fmt(b)} × {fmt(rb)}% = {fmt(v)}"


@formula()
def mul2(a, b):
    v = r0(a * b)
    return v, f"{fmt(a)} × {fmt(b)} = {fmt(v)}"


@formula()
def poc_rev(contract, cum, total, prior):
    """진행기준 당기 수익 = 계약금액 × 누적발생원가 ÷ 총예정원가 − 전기까지 인식한 누적수익"""
    v = r0(contract * cum / total - prior)
    return v, f"{fmt(contract)} × {fmt(cum)} ÷ {fmt(total)} − 전기 누적수익 {fmt(prior)} = {fmt(v)}"


@formula()
def cum_alloc(total, elapsed, period, prior):
    """기간 배분 누적액 − 전기 누적액: 총액 × 경과기간 ÷ 전체기간 − 전기까지 인식액"""
    v = r0(total * elapsed / period - prior)
    return v, f"총액 {fmt(total)} × {fmt(elapsed)} ÷ {fmt(period)} − 전기 누적 {fmt(prior)} = {fmt(v)}"


@formula(kind="qty")
def wavg_sh(base, add, months):
    """가중평균유통보통주식수 = 기초 유통주식수 + 기중 발행주식수 × 유통 개월수 ÷ 12"""
    v = r0(base + add * months / 12)
    return v, f"{fmt(base)} + {fmt(add)} × {fmt(months)}/12 = {fmt(v)}"


@formula(kind="qty")
def tsm(n, ex, avg):
    """자기주식법 증가 주식수 = 행사 가정 주식수 − 행사대금 ÷ 평균시가"""
    v = r0(n - n * ex / avg)
    return v, f"{fmt(n)} − {fmt(n)} × {fmt(ex)} ÷ {fmt(avg)} = {fmt(v)}"


@formula()
def lin(f, v, x):
    """총원가 = 고정원가 + 단위당 변동원가 × 조업도"""
    v_ = r0(f + v * x)
    return v_, f"{fmt(f)} + {fmt(v)} × {fmt(x)} = {fmt(v_)}"


@formula()
def dmul(a, b, n):
    """차이 × 수량: (a − b) × n"""
    v = r0((a - b) * n)
    return v, f"({fmt(a)} − {fmt(b)}) × {fmt(n)} = {fmt(v)}"


def evaluate(formula_id, params):
    fn, kind = F[formula_id]
    return fn(**params)

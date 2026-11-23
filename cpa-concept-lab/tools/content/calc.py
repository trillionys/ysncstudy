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


def evaluate(formula_id, params):
    fn, kind = F[formula_id]
    return fn(**params)

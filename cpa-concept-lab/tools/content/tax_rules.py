"""세법 세율표(TaxRuleEngine 데이터).

세율은 코드 상수가 아니라 적용 연도·근거 조문이 붙은 데이터로 관리한다.
- 빌드는 각 표의 evidence 문자열이 근거 조문 원문(국가법령정보센터 2026. 11. 24. 조회)에 실제로 있는지 대조한다.
- 앱은 이 데이터(assets/content/tax_rules.json)를 읽어 Kotlin TaxRuleEngine 으로 세액을 독립 계산한다.
- 2027년 시험의 법령 적용 기준일은 최종 시행계획 공고 전이므로 verificationStatus 는 STATUTE_TEXT_MATCHED(현행 조문 일치)이며,
  '2027 시험 적용 확정'으로 표시하지 않는다.
"""
TABLES = [
    dict(id="CIT", code=1, taxType="법인세", lawName="법인세법", article="제55조①1호", effectiveFrom="2026-01-01", effectiveTo=None,
         taxYearFrom=2026, taxpayerType="내국법인(성실신고확인 대상 소규모 법인 제외)",
         brackets=[(200000000, 10), (20000000000, 20), (300000000000, 22), (None, 25)], cap=None,
         evidence=["2억원 이하 과세표준의 100분의 10", "2억원을 초과하는 금액의 100분의 20", "200억원을 초과하는 금액의 100분의 22",
                   "3천억원을 초과하는 금액의 100분의 25"]),
    dict(id="PIT", code=2, taxType="소득세", lawName="소득세법", article="제55조①", effectiveFrom="2023-01-01", effectiveTo=None,
         taxYearFrom=2023, taxpayerType="거주자 종합소득",
         brackets=[(14000000, 6), (50000000, 15), (88000000, 24), (150000000, 35), (300000000, 38), (500000000, 40),
                   (1000000000, 42), (None, 45)], cap=None,
         evidence=["1,400만원 이하 과세표준의 6퍼센트", "1,400만원을초과하는금액의15퍼센트", "5,000만원을초과하는금액의24퍼센트",
                   "8,800만원을초과하는금액의35퍼센트", "1억5천만원을초과하는금액의38퍼센트", "3억원을초과하는금액의40퍼센트",
                   "5억원을초과하는금액의42퍼센트", "10억원을 초과하는 금액의 45퍼센트"]),
    dict(id="INH", code=3, taxType="상속세·증여세", lawName="상증법", article="제26조", effectiveFrom="2010-01-01", effectiveTo=None,
         taxYearFrom=2010, taxpayerType="상속세·증여세(제56조 준용)",
         brackets=[(100000000, 10), (500000000, 20), (1000000000, 30), (3000000000, 40), (None, 50)], cap=None,
         evidence=["1억원 이하 과세표준의 100분의 10", "1억원을초과하는금액의100분의20", "5억원을초과하는금액의100분의30",
                   "10억원을초과하는금액의100분의40", "30억원을초과하는금액의100분의50"]),
    dict(id="EID", code=4, taxType="근로소득공제", lawName="소득세법", article="제47조①", effectiveFrom="2020-01-01", effectiveTo=None,
         taxYearFrom=2020, taxpayerType="근로소득이 있는 거주자(일용근로자 제외)",
         brackets=[(5000000, 70), (15000000, 40), (45000000, 15), (100000000, 5), (None, 2)], cap=20000000,
         evidence=["500만원 이하 총 급여액의 100분의 70", "500만원을초과하는금액의100분의40", "1천500만원을초과하는금액의100분의15",
                   "4천500만원을초과하는금액의100분의5", "1억원을초과하는금액의100분의2", "2천만원을 초과하는 경우에는 2천만원"]),
]


def progressive(code, base):
    t = next(x for x in TABLES if x["code"] == code)
    tax, lower = 0.0, 0
    for upper, rate in t["brackets"]:
        top = base if upper is None else min(base, upper)
        if top > lower:
            tax += (top - lower) * rate / 100.0
        if upper is None or base <= upper:
            break
        lower = upper
    if t["cap"] is not None:
        tax = min(tax, t["cap"])
    return tax


def export():
    return {"formatVersion": 1, "tables": [
        {k: v for k, v in t.items() if k != "brackets"} | {"brackets": [{"upTo": u, "rate": r} for u, r in t["brackets"]],
                                                            "verificationStatus": "STATUTE_TEXT_MATCHED"}
        for t in TABLES]}

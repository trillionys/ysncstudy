"""세법개론(TX) 공통 도우미.

- 법령 원문: 2026. 11. 24. 국가법령정보센터 조회 현행 법률(tools/law/data).
- 2027년 CPA 1차 세법개론의 법령 적용 기준일은 최종 시행계획 공고 전이며, 2026년 말 세법개정 내용은 아직 반영하지 않았다.
  그래서 세법 개념은 '[현재 기준]'(CURRENT_LAW)으로 표시하고 '2027년 적용 확정'으로 표시하지 않는다.
- 시행령·시행규칙: 원문을 확보·대조하지 못했다. 시행령 위임 수치(예: 법정이자율, 감가상각 내용연수)는 숫자 문항으로 만들지 않는다.
"""
from lawdsl import lc
import curriculum

NOTE = ("2026. 11. 24. 현행 법률 기준(국가법령정보센터 원문 대조). 2027년 시험 법령 적용 기준일은 최종 시행계획 공고 전이고 "
        "2026년 말 세법개정은 반영 전이므로 시험 직전 개정 여부를 다시 확인해야 한다. 시행령·시행규칙 위임 사항은 원문 대조를 하지 않았다.")

# 실전 비중 모드 영역(금융위원회 공고 제2026-284호 세법개론 출제 비중)
AREAS = [
    ("TAX_BASIC", "국세기본법", 10, 15, 5),
    ("CORP", "법인세법", 30, 40, 14),
    ("INCOME", "소득세법", 20, 30, 10),
    ("VAT", "부가가치세법", 15, 25, 8),
    ("INH_LOCAL", "상속세 및 증여세법·지방세법", 5, 10, 3),
]
LAW_AREA = {"국세기본법": "TAX_BASIC", "법인세법": "CORP", "소득세법": "INCOME", "부가가치세법": "VAT",
            "상증법": "INH_LOCAL", "지방세법": "INH_LOCAL"}
# 조세특례제한법 개념은 관련 세목 영역으로 넣는다: conceptId -> area
CONCEPT_AREA = {}


def tc(cid, ko, en, art, *a, area=None, cur=(), **kw):
    """세법 개념 등록. area: 조특법 등 근거 법률만으로 영역이 정해지지 않을 때 지정. cur: 출제범위 목차 항목 명시 연결."""
    kw.setdefault("applic", "CURRENT_LAW")
    kw.setdefault("note", NOTE)
    c = lc(cid, ko, en, art, *a, **kw)
    if area:
        CONCEPT_AREA[cid] = area
    for item in cur:
        curriculum.link("TX", item, cid)
    return c


def tcq(cid, text, formula_id, params, exp, unit="원", lv=4, art=None, **kw):
    """세법 계산문항. 정답은 calc.py(세율은 tax_rules 데이터)로 계산하고 앱 FormulaEngine·TaxRuleEngine 이 독립 검산한다.
    기업법 lcq 와 달리 questionType 을 'calculation' 으로 유지해 '계산 전용' 학습 모드에 포함한다."""
    import dsl
    import lawdsl
    qq = dsl.cq(cid, text, formula_id, params, exp, unit=unit, lv=lv, **kw)
    return lawdsl._register(qq, art, False)

"""v7 간단 요약(shortSummary) + 상세 학습(detailSections) 생성.

- 간단 요약: 기존 정의(definition)를 그대로 보존하고, 기존 공식·출제 포인트의 첫 문장을 덧붙인다(3~6줄). 기존 문장을 고치지 않는다.
- 상세 학습: 개념의 기존 구조화 필드(정의·필요성·원리·요건·측정·공식·분개·예외·사례·비교·출제·함정·관련 기준)를
  정해진 순서의 섹션으로 옮기고, detail_extra.EXTRA 의 직접 작성 섹션(계산 예제·분개·변형 상황)을 추가한다.
  필드가 비어 있는 섹션은 만들지 않는다(억지 생성 금지).
- 충실도(detailLevel): RICH(직접 작성 보강 포함) / FULL(핵심 섹션 충족) / BASIC(보완 필요). 보고서에 그대로 집계한다.
"""
import re

import detail_extra

ORDER = [
    ("DEFINITION", "개념의 정의", "definition"),
    ("WHY", "개념이 필요한 이유", "whyItMatters"),
    ("PRINCIPLE", "핵심 원리", "detailedExplanation"),
    ("REQUIREMENTS", "적용요건", "recognitionRules"),
    ("METHOD", "적용방법·측정", "measurementRules"),
    ("FORMULA", "공식 및 계산방법", "formula"),
    ("JOURNAL", "회계분개", "journalEntries"),
    ("EXCEPTIONS", "예외 및 특수한 상황", "exceptions"),
    ("EXAMPLE", "구체적인 사례", "example"),
    ("COMPARISON", "유사 개념 비교", "comparison"),
    ("EXAM_POINTS", "CPA 시험 출제 포인트", "examTips"),
    ("MISTAKES", "자주 틀리는 부분", "commonMistakes"),
]
EXTRA_AFTER = "EXAMPLE"   # 직접 작성 섹션은 '구체적인 사례' 뒤에 넣는다


def _first_sentence(s):
    s = (s or "").strip()
    m = re.match(r"(.+?[다\.\)])(\s|$)", s)
    return (m.group(1) if m else s)[:140]


def short_summary(c):
    """기존 정의 전문을 보존하고 공식·시험 포인트 요지를 덧붙인다."""
    lines = [c["definition"].strip()]
    core = _first_sentence(c.get("detailedExplanation"))
    if core and core not in lines[0]:
        lines.append("핵심: " + core)
    if c.get("formula"):
        lines.append("공식: " + c["formula"].split("\n")[0].strip()[:160])
    if c.get("examTips"):
        lines.append("시험: " + _first_sentence(c["examTips"]))
    return "\n".join(lines)


def _nums(side):
    vals = [int(x.replace(",", "")) for x in re.findall(r"(?<![\d.])(\d{1,3}(?:,\d{3})+|\d+)(?![\d.])", side)]
    return vals


def check_journal(cid, j, errors):
    """'계정 금액 / 계정 금액' 형식이면 양변 합계가 같아야 한다. 금액 없는 단일 계정이면 amount 를 쓴다."""
    amt = int(str(j["amount"]).replace(",", ""))
    dr = _nums(j["dr"]) or [amt]
    cr = _nums(j["cr"]) or [amt]
    if sum(dr) != sum(cr):
        errors.append(f"[DETAIL] {cid} 분개 차변 {sum(dr):,} ≠ 대변 {sum(cr):,}: {j}")


# 법률·세법 개념은 같은 필드에 다른 의미를 담는다(lawdsl.lc: whyItMatters=쉬운 설명, measurementRules=법률효과 등)
LAW_TITLES = {"DEFINITION": "법률상 정확한 의미", "WHY": "쉬운 설명", "REQUIREMENTS": "요건", "METHOD": "법률효과",
              "FORMULA": "숫자·기간·세율", "EXAMPLE": "간단한 사례", "COMPARISON": "비교 개념", "EXAM_POINTS": "출제 포인트",
              "MISTAKES": "함정"}


def sections(c, errors):
    out = []
    is_law = bool(c.get("articleRefs"))
    extra = detail_extra.EXTRA.get(c["id"], [])
    n = 0
    for typ, title, field in ORDER:
        body = (c.get(field) or "").strip()
        if is_law and typ == "PRINCIPLE" and body == (c.get("measurementRules") or "").strip():
            body = ""   # 법률 개념의 core 는 [요건]/[효과] 요약이라 아래 섹션과 중복되면 생략
        if body:
            n += 1
            sec = dict(sectionId=f"{c['id']}-D{n:02d}", type=typ, title=LAW_TITLES.get(typ, title) if is_law else title, body=body, order=n, formulas=[], journal=[],
                       source="EXISTING")
            if typ == "FORMULA":
                sec["formulas"] = [x.strip() for x in re.split(r"\n|(?<=\))\s*/\s*|\s/\s", body) if x.strip()]
            out.append(sec)
        if typ == EXTRA_AFTER:
            for e in extra:
                n += 1
                for j in e.get("journal", []):
                    check_journal(c["id"], j, errors)
                out.append(dict(sectionId=f"{c['id']}-D{n:02d}", type=e["type"], title=e["title"], body=e["body"].strip(), order=n,
                                formulas=e.get("formulas", []), journal=e.get("journal", []), source="AUTHORED_V7"))
        if is_law and typ == "EXCEPTIONS" and c.get("caseLaw"):
            n += 1
            out.append(dict(sectionId=f"{c['id']}-D{n:02d}", type="CASE_LAW", title="판례 법리(사건번호 미기재·원문 미대조)",
                            body=c["caseLaw"].strip(), order=n, formulas=[], journal=[], source="EXISTING"))
    refs = (c.get("standardReference") or "").strip()
    if refs:
        n += 1
        out.append(dict(sectionId=f"{c['id']}-D{n:02d}", type="REFERENCES", title="관련 기준서·법령", body=refs, order=n,
                        formulas=[], journal=[], source="EXISTING"))
    return out


CORE = {"DEFINITION", "PRINCIPLE", "EXAMPLE", "COMPARISON", "EXAM_POINTS", "MISTAKES"}


def level(c, secs):
    types = {s["type"] for s in secs}
    if any(s["source"] == "AUTHORED_V7" for s in secs):
        return "RICH"
    applied = types & {"FORMULA", "JOURNAL", "REQUIREMENTS", "METHOD", "EXCEPTIONS"}
    chars = sum(len(s["body"]) for s in secs if s["type"] != "REFERENCES")
    if CORE <= types and applied and chars >= 450:
        return "FULL"
    return "BASIC"


def apply(concepts, errors):
    stats = {"RICH": 0, "FULL": 0, "BASIC": 0}
    basic_ids = []
    for c in concepts:
        c["shortSummary"] = short_summary(c)
        secs = sections(c, errors)
        c["detailSections"] = secs
        c["detailLevel"] = level(c, secs)
        stats[c["detailLevel"]] += 1
        if c["detailLevel"] == "BASIC":
            basic_ids.append(c["id"])
        if not c["definition"].strip() or c["definition"].strip() not in c["shortSummary"]:
            errors.append(f"[DETAIL] {c['id']} 간단 요약에 기존 정의가 보존되지 않음")
    for cid in detail_extra.EXTRA:
        if not any(c["id"] == cid for c in concepts):
            errors.append(f"[DETAIL] 보강 섹션 대상 개념 {cid} 없음")
    return stats, basic_ids

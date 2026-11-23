"""기업법(CPA-LAW) 콘텐츠 작성용 DSL.

법령 원문은 tools/law/data/*.json (국가법령정보센터 lsInfoR.do 응답을 tools/law/parse_law.py 로 변환)에서 읽는다.
- 문항마다 근거 조문(art)을 적는다. 빌드 때 정답(또는 등록된 동의어 중 하나)이 근거 조문 원문에 실제로
  들어 있는지 자동 대조한다(verify=True 기본). 해석·개념형 정답처럼 조문 문언에 그대로 없는 경우에만
  verify=False 로 두며, 이런 문항은 '해석 문항(조문 직접 대조 불가)'으로 따로 집계한다.
- 숫자·기간·정족수는 nr() 로 숫자 규칙 DB 에 등록하며, 값이 조문 원문에 없으면 빌드가 실패한다.
- 판례: 사건번호·선고일을 확인하지 못한 판례는 사건번호를 적지 않고 '판례 법리(원문 미대조)'로만 설명한다.
"""
import json
import os
import re
import unicodedata

import dsl

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(os.path.dirname(HERE), "law", "data")
SUBJECT = "CPA-LAW"
EXAM_YEAR = 2027
VERIFIED_AT = "2026-11-23"

LAWS = {
    "상법": dict(file="sangbeop.json", version="법률 제21044호", promulgated="2025-09-09", effective="2026-09-10",
               url="https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=273629"),
    "공인회계사법": dict(file="cpa_act.json", version="법률 제20055호", promulgated="2024-01-16", effective="2024-01-16",
                   url="https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=258791"),
    "외부감사법": dict(file="audit_act.json", version="법률 제20896호", promulgated="2025-04-01", effective="2025-04-01",
                  url="https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=270309",
                  full="주식회사 등의 외부감사에 관한 법률"),
}
ARTICLES = {}
for law, meta in LAWS.items():
    d = json.load(open(os.path.join(DATA, meta["file"]), encoding="utf-8"))
    ARTICLES[law] = {a["articleId"]: a for a in d["articles"]}

NUMERIC_RULES = []
CASE_NOTES = []
QUESTION_ARTICLES = {}   # questionId -> [(law, articleId)]
QUESTION_VERIFY = {}     # questionId -> True(조문 대조 대상) / False(해석 문항)

_ART_RE = re.compile(r"(?:(상법|공인회계사법|외부감사법)\s*)?제(\d+)조(?:의(\d+))?\s*([①-⑳]?)")
CIRCLED = "①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳"


def parse_refs(spec, default_law="상법"):
    """'제368조① 제434조', '외부감사법 제10조' → [(law, articleId, paragraph)]"""
    out = []
    law = default_law
    for m in _ART_RE.finditer(spec or ""):
        if m.group(1):
            law = m.group(1)
        aid = f"제{m.group(2)}조" + (f"의{m.group(3)}" if m.group(3) else "")
        para = CIRCLED.index(m.group(4)) + 1 if m.group(4) else None
        if aid not in ARTICLES[law]:
            raise ValueError(f"{law} {aid} 가 법령 데이터에 없습니다: {spec}")
        out.append((law, aid, para))
    return out


def ref_label(refs):
    return ", ".join(f"{l} {a}" + (f" 제{p}항" if p else "") for l, a, p in refs)


def _n(s):
    s = unicodedata.normalize("NFC", s or "")
    return re.sub(r"[\s·ㆍ,]", "", s)


def article_text(refs):
    return " ".join(ARTICLES[l][a]["title"] + " " + ARTICLES[l][a]["text"] for l, a, _ in refs)


_state = {"law": "상법"}


def lchapter(code, title, english, part, law="상법"):
    _state["law"] = law
    meta = LAWS[law]
    return dsl.chapter(code, title, english, part=part, subject=SUBJECT, chapter_id=f"LAW-{code}",
                       std=f"{meta.get('full', law)} ({meta['version']}, {meta['promulgated']} 공포, {meta['effective']} 시행)",
                       eff=meta["effective"])


def lc(cid, ko, en, art, easy, legal, req, eff, cmp, ex, tip, trap, exc=None, nums=None, case=None, rel="",
       review=False, note=None, applic="APPLIES_2027", law=None, eff_date=None, transition=None, hist=False):
    """기업법 개념 등록.
    easy: 쉬운 설명 / legal: 법률상 정확한 의미 / req: 성립요건·적용요건 / eff: 법률효과 / exc: 예외
    cmp: 비교 개념 / ex: 간단한 사례 / tip: 출제 포인트 / trap: 함정 / nums: 숫자·기간·정족수 / case: 판례 법리
    applic: APPLIES_2027(2027년 적용 기준) | PAST(과거 기출 기준) | NOT_IN_FORCE(미시행 개정) | CHECK_DATE(시행일 확인 필요)
    """
    law = law or _state["law"]
    refs = parse_refs(art, law)
    meta = LAWS[refs[0][0]]
    core = f"[요건] {req}\n[효과] {eff}"
    if exc:
        core += f"\n[예외] {exc}"
    if case:
        core += f"\n[판례 법리] {case}"
    c = dsl.concept(cid, ko, en, d=legal, why=easy, core=core, cmp=cmp, ex=ex, tip=tip, trap=trap, rel=rel, f=nums,
                    rec=req, meas=eff, exc=exc, review=review or applic in ("NOT_IN_FORCE", "CHECK_DATE"), note=note,
                    std=f"{meta.get('full', refs[0][0])} {ref_label(refs).replace(refs[0][0] + ' ', '', 1)}",
                    eff=eff_date or meta["effective"], hist=hist)
    c.update(
        lawName=meta.get("full", refs[0][0]),
        articleRefs=[ref_label([r]) for r in refs],
        lawVersion=meta["version"],
        promulgationDate=meta["promulgated"],
        transitionRule=transition,
        examYear=EXAM_YEAR,
        examApplicability=applic,
        sourceUrl=meta["url"],
        verifiedAt=VERIFIED_AT,
        caseLaw=case,
    )
    return c


def _register(qq, art, verify):
    cid = qq["primaryConceptId"]
    concept = next(c for c in dsl.CONCEPTS if c["id"] == cid)
    default_law = re.match(r"(상법|공인회계사법|외부감사법)", concept["articleRefs"][0]).group(1)
    refs = parse_refs(art, default_law) if art else parse_refs(" ".join(concept["articleRefs"]), default_law)
    QUESTION_ARTICLES[qq["id"]] = refs
    QUESTION_VERIFY[qq["id"]] = verify
    qq["standardReference"] = ref_label(refs)
    return qq


def lq(cid, text, answer, exp, art=None, verify=True, **kw):
    """빈칸·단답 문항. verify=True 면 정답(또는 동의어)이 근거 조문 원문에 있는지 빌드에서 대조한다."""
    return _register(dsl.q(cid, text, answer, exp, **kw), art, verify)


def lmc(cid, text, options, answer, exp, art=None, **kw):
    return _register(dsl.mc(cid, text, options, answer, exp, **kw), art, False)


def lslot(cid, text, pairs, exp, art=None, **kw):
    return _register(dsl.slot_q(cid, text, pairs, exp, **kw), art, False)


def lorder(cid, text, items, exp, art=None, **kw):
    return _register(dsl.order_q(cid, text, items, exp, **kw), art, False)


def nr(rule_id, cid, art, name, text, value, unit, exp, subject="", condition="", exception=None, alt=(), lv=2, **kw):
    """숫자·기간·정족수 규칙 + 숫자 문항. value(또는 alt 중 하나)는 근거 조문 원문에 있어야 한다."""
    qq = lq(cid, text, value, exp, art=art, alt=list(alt), t="number", lv=lv, unit=unit if unit else None, **kw)
    refs = QUESTION_ARTICLES[qq["id"]]
    NUMERIC_RULES.append(dict(ruleId=rule_id, lawName=refs[0][0], article=ref_label(refs), ruleName=name, subject=subject,
                              condition=condition, value=value, unit=unit or "", exception=exception,
                              effectiveDate=LAWS[refs[0][0]]["effective"], sourceUrl=LAWS[refs[0][0]]["url"],
                              conceptId=cid, questionId=qq["id"]))
    return qq


def case_note(cid, topic, holding, art):
    """판례 법리 학습자료. 사건번호·선고일은 원문을 확인한 경우에만 적는다(이번 판은 확인하지 못해 비움)."""
    refs = parse_refs(art)
    CASE_NOTES.append(dict(id=f"CASE-{len(CASE_NOTES) + 1:03d}", conceptId=cid, topic=topic, holding=holding,
                           caseNumber=None, decisionDate=None, court="대법원", articles=ref_label(refs),
                           verificationStatus="DOCTRINE_ONLY_NOT_VERIFIED"))


def verify_answers(questions):
    """정답-조문 대조. 반환: (대조 일치 문항 수, 해석 문항 수, 오류 목록)"""
    ok = interp = 0
    errors = []
    for x in questions:
        if x["id"] not in QUESTION_ARTICLES:
            continue
        if not QUESTION_VERIFY[x["id"]]:
            interp += 1
            x["answerVerification"] = "INTERPRETIVE"
            continue
        body = _n(article_text(QUESTION_ARTICLES[x["id"]]))
        cands = [x["blankAnswer"]] + x["acceptedAnswers"]
        if any(_n(c) and _n(c) in body for c in cands):
            ok += 1
            x["answerVerification"] = "STATUTE_TEXT_MATCHED"
        else:
            errors.append(f"[L1] {x['id']} 정답 '{x['blankAnswer']}' 이(가) 근거 조문({x['standardReference']}) 원문에 없음")
    return ok, interp, errors


def lcq(cid, text, formula_id, params, exp, unit="원", lv=4, art=None, **kw):
    """기업법 계산문항(정족수·배당가능이익 등). 정답은 calc.py 공식으로 계산하고 앱 FormulaEngine 이 독립 검산한다."""
    qq = dsl.cq(cid, text, formula_id, params, exp, unit=unit, lv=lv, **kw)
    qq["questionType"] = "number"
    return _register(qq, art, False)

"""CPA Concept Lab 콘텐츠 작성용 DSL.

콘텐츠 원본은 tools/content/*.py 에 사람이 직접 작성한 확정 데이터이며,
build_content.py 가 이를 검증한 뒤 app/src/main/assets/content/*.json 으로 변환한다.
앱은 학습 시점에 외부 서비스로 문제를 생성하지 않는다.
"""

SUBJECT_ID = "FA"

CHAPTERS = []      # dict(id, code, title, englishTitle, sortOrder)
SECTIONS = []      # dict(id, chapterId, title, sortOrder)
CONCEPTS = []      # dict
QUESTIONS = []     # dict
EXAM_SCOPES = []   # dict(number, title, conceptIds)
PROBLEM_SOURCES = []  # dict(sourceId, ...)
SCOPE_ITEMS = []   # 학습범위 체크리스트(지정 세부항목 → conceptId)

DEFAULT_PART = "개념체계와 재무제표 표시"

_state = {"chapter": None, "section": None, "defaults": {}}

# 콘텐츠 리비전: 기존 ID 의 설명·메타데이터를 고치면 올린다(앱은 더 높은 리비전만 갱신).
# v2: 문항 relatedConceptIds·effectiveFrom 보강
REVISION = 2

# 대단원별 기준 시행일 기본값(개념·문항에 eff 를 따로 주지 않은 경우)
#  B: K-IFRS 의무 적용 2011.1.1 / C~H: 2018 개정 개념체계 시행 2020.1.1
#  I: K-IFRS 제1001호 2011.1.1 / J: 부채 유동·비유동 분류 개정 2024.1.1 / K: 제1118호 2027.1.1
#  A: 회계기준이 아닌 학술 이론이므로 시행일 없음
CHAPTER_EFFECTIVE = {"B": "2011-01-01", "C": "2020-01-01", "D": "2020-01-01", "E": "2020-01-01",
                     "F": "2020-01-01", "G": "2020-01-01", "H": "2020-01-01", "I": "2011-01-01",
                     "J": "2024-01-01", "K": "2027-01-01"}


def chapter(code, title, english, part=DEFAULT_PART, **defaults):
    ch = {
        "partTitle": part,
        "id": f"FA-{code}",
        "code": code,
        "subjectId": SUBJECT_ID,
        "title": title,
        "englishTitle": english,
        "sortOrder": len(CHAPTERS) + 1,
    }
    CHAPTERS.append(ch)
    _state["chapter"] = ch
    _state["section"] = None
    defaults.setdefault("eff", CHAPTER_EFFECTIVE.get(code))
    _state["defaults"] = defaults
    return ch


def section(title):
    ch = _state["chapter"]
    n = sum(1 for s in SECTIONS if s["chapterId"] == ch["id"]) + 1
    sec = {
        "id": f"{ch['id']}-S{n:02d}",
        "chapterId": ch["id"],
        "title": title,
        "sortOrder": n,
    }
    SECTIONS.append(sec)
    _state["section"] = sec
    return sec


def _split(v):
    if not v:
        return []
    if isinstance(v, (list, tuple)):
        return list(v)
    return v.split()


def concept(cid, ko, en, d, why, core, cmp, ex, tip, trap, rel="", f=None,
            parent=None, pre="", std=None, eff=None, eff_to=None, hist=False,
            review=False, note=None, year=None, rec=None, meas=None, exc=None, je=None, src=""):
    """개념 등록.

    d: 정의, why: 필요한 이유, core: 핵심 원리, cmp: 유사 개념과의 차이,
    ex: 실제 회계 사례, tip: CPA 출제 포인트, trap: 자주 틀리는 함정, f: 공식/계산과정
    """
    ch = _state["chapter"]
    sec = _state["section"]
    dflt = _state["defaults"]
    c = {
        "id": cid,
        "subjectId": SUBJECT_ID,
        "chapterId": ch["id"],
        "sectionId": sec["id"],
        "parentConceptId": parent,
        "koreanName": ko,
        "englishName": en,
        "definition": d,
        "whyItMatters": why,
        "detailedExplanation": core,
        "comparison": cmp,
        "example": ex,
        "formula": f,
        "examTips": tip,
        "commonMistakes": trap,
        "recognitionRules": rec,
        "measurementRules": meas,
        "exceptions": exc,
        "journalEntries": je,
        "sourceProblemIds": _split(src),
        "relatedConceptIds": _split(rel),
        "prerequisiteConceptIds": _split(pre),
        "standardReference": std or dflt.get("std", ""),
        "effectiveFrom": eff or dflt.get("eff"),
        "effectiveTo": eff_to or dflt.get("eff_to"),
        "isHistorical": hist,
        "requiresReview": review,
        "revisionNote": note or dflt.get("note"),
        "originalExamYear": year,
        "reviewStatus": "NEEDS_REVIEW" if review else "REVIEWED",
        "isRequired": True,
        "isActive": True,
        "sortOrder": len(CONCEPTS) + 1,
        "revision": REVISION,
    }
    CONCEPTS.append(c)
    _state["concept"] = c
    return c


def q(cid, text, answer, exp, alt=(), t="definition", lv=1, conf=None,
      calc=None, unit=None, also="", std=None, eff=None, review=False, note=None,
      input_type="text", choices=None, slots=None, calc_spec=None, src=""):
    """기본 문항 등록. 모든 기본 문항은 기본(초성)·심화(무힌트) 두 모드로 출제된다.

    text: 빈칸 '(______)' 를 정확히 1개 포함하는 문장
    answer: 정답(조사 없는 핵심 용어 또는 수치)
    exp: 왜 이 답이 정답인지(문제 조건과 개념 연결)
    alt: 명시적으로 허용하는 동의어
    conf: 헷갈리기 쉬운 인접 개념 설명
    calc: 계산문제의 공식·대입·순서·결과
    also: 함께 연결할 추가 conceptId(공백 구분)
    """
    n = sum(1 for x in QUESTIONS if x["primaryConceptId"] == cid) + 1
    ch_id = None
    for c in CONCEPTS:
        if c["id"] == cid:
            ch_id = c["chapterId"]
            c_std, c_eff = c["standardReference"], c["effectiveFrom"]
            c_rel = c["relatedConceptIds"]
            break
    else:
        raise ValueError(f"unknown concept {cid} for question")
    qq = {
        "id": f"{cid}-Q{n:02d}",
        "primaryConceptId": cid,
        "conceptIds": [cid] + [x for x in _split(also) if x != cid],
        "subjectId": SUBJECT_ID,
        "chapterId": ch_id,
        "questionType": t,
        "questionText": text,
        "blankAnswer": answer,
        "acceptedAnswers": _dedupe([answer] + list(alt)),
        "relatedConceptIds": [r for r in c_rel if r != cid and r not in _split(also)],
        "unit": unit,
        "inputType": input_type,
        "choices": list(choices or []),
        "slots": list(slots or []),
        "calcSpec": calc_spec,
        "sourceProblemIds": _split(src),
        "explanation": exp,
        "confusion": conf,
        "calculation": calc,
        "difficulty": lv,
        "standardReference": std or c_std,
        "effectiveFrom": eff or c_eff,
        "requiresReview": review,
        "revisionNote": note,
        "reviewStatus": "NEEDS_REVIEW" if review else "REVIEWED",
        "isActive": True,
        "revision": REVISION,
        "sortOrder": len(QUESTIONS) + 1,
    }
    QUESTIONS.append(qq)
    return qq


def _key(a):
    import re
    import unicodedata
    return re.sub(r"\s+", "", unicodedata.normalize("NFC", a).lower())


def _dedupe(answers):
    """채점기가 공백·대소문자를 무시하므로, 정규화 결과가 같은 표기는 하나만 남긴다."""
    seen, out = set(), []
    for a in answers:
        k = _key(a)
        if k and k not in seen:
            seen.add(k)
            out.append(a)
    return out


def exam(number, title, concept_ids):
    EXAM_SCOPES.append({
        "number": f"{number:02d}",
        "title": title,
        "conceptIds": _split(concept_ids),
    })


# ───────── 확장 문항 유형 (v3) ─────────
# OX·참거짓·예/아니요 형식은 만들지 않는다(검증기가 금지어를 검사한다).

def mc(cid, text, options, answer, exp, t="choice", lv=2, conf=None, also="", **kw):
    """4지선다 개념 구별. answer 는 options 중 하나의 문자열."""
    assert answer in options and len(options) == 4, (cid, text)
    return q(cid, text, answer, exp, t=t, lv=lv, conf=conf, also=also, input_type="choice", choices=options, **kw)


def slot_q(cid, text, pairs, exp, t="matching", lv=2, options=None, also="", conf=None, **kw):
    """매칭·분류·순서배열·분개 계정 선택: 각 슬롯(label)에 보기(options) 중 하나를 고른다.
    pairs: [(label, answer), ...]  options: 보기 목록(없으면 정답들의 합집합)"""
    opts = list(options) if options else sorted({a for _, a in pairs})
    for _, a in pairs:
        assert a in opts, (cid, a)
    answer = " / ".join(f"{l} → {a}" for l, a in pairs)
    return q(cid, text, answer, exp, t=t, lv=lv, conf=conf, also=also, input_type="slots",
             choices=opts, slots=[{"label": l, "answer": a} for l, a in pairs], **kw)


def order_q(cid, text, items_in_order, exp, lv=3, also="", **kw):
    """순서 배열: 1번째~n번째 슬롯에 항목을 배치한다."""
    pairs = [(f"{i + 1}단계", it) for i, it in enumerate(items_in_order)]
    return slot_q(cid, text, pairs, exp, t="order", lv=lv, options=sorted(items_in_order), also=also, **kw)


def cq(cid, text, formula_id, params, exp, unit="원", lv=4, also="", conf=None, **kw):
    """계산문제. text 안의 {이름} 은 params 값(쉼표 서식)으로 치환된다.
    정답은 calc 모듈 공식으로 계산하며, 앱의 Kotlin 공식엔진이 같은 값을 독립 검산한다."""
    import calc as _c
    value, steps = _c.evaluate(formula_id, params)
    shown = {k: _c.fmt(v) for k, v in params.items()}
    body = text.format(**shown)
    ans = _c.fmt(value)
    alts = []
    if unit:
        alts.append(ans + unit)
    return q(cid, body, ans, exp, alt=alts, t="calculation", lv=lv, calc=steps, unit=unit, also=also, conf=conf,
             calc_spec={"formulaId": formula_id, "params": {k: float(v) for k, v in params.items()}}, **kw)


def scope(chapter_label, item, cid, kw=None):
    """지정 학습범위 체크리스트 항목 등록. kw: 콘텐츠에서 찾을 키워드(기본: 항목명, '/' 로 대안 구분)."""
    SCOPE_ITEMS.append({"area": chapter_label, "item": item, "conceptId": cid, "keywords": (kw or item).split("/")})


def scope_block(area, text):
    """여러 줄 'conceptId|항목1,항목2=키워드,...' 형식으로 체크리스트를 등록한다."""
    for line in text.strip().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        cid, items = line.split("|", 1)
        for it in items.split(","):
            it = it.strip()
            if not it:
                continue
            if "=" in it:
                name, kwd = it.split("=", 1)
                scope(area, name.strip(), cid.strip(), kwd.strip())
            else:
                scope(area, it, cid.strip())


def source(source_id, **fields):
    PROBLEM_SOURCES.append({"sourceId": source_id, **fields})

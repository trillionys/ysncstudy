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

_state = {"chapter": None, "section": None, "defaults": {}}


def chapter(code, title, english, **defaults):
    ch = {
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
            review=False, note=None, year=None):
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
        "revision": 1,
    }
    CONCEPTS.append(c)
    _state["concept"] = c
    return c


def q(cid, text, answer, exp, alt=(), t="definition", lv=1, conf=None,
      calc=None, unit=None, also="", std=None, eff=None, review=False, note=None):
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
        "unit": unit,
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
        "revision": 1,
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

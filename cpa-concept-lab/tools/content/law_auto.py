"""개념별 '비교 문제'·'사례·효과 문제' 보강(구조화 자동 생성).

요구사항: 기업법 각 개념에 비교 문제와 사례·효과 문제가 있어야 한다.
직접 작성한 문항으로 채우지 못한 개념에 한해, 작성자가 이미 검토한 개념 데이터만으로 정답이 확정되는
매칭형 문항을 만든다(새로운 법률 내용을 만들지 않는다).
  - 조문 연결(comparison): 대상 개념 + 관련 개념 2개를 각각의 주된 근거 조문과 연결
  - 법률효과 연결(effect): 대상 개념 + 관련 개념 2개를 각각의 법률효과(개념의 [효과] 항목)와 연결
이 문항들은 조문 문언 직접 대조 대상이 아니므로 해석 문항(INTERPRETIVE)으로 집계한다.
"""
import re

import dsl
from lawdsl import lslot

CMP_T = {"comparison", "matching", "choice", "classification"}
CASE_T = {"situation", "effect", "exception", "procedure", "order"}
GENERIC = {"형사처벌.", "과태료.", "절차 판단.", "소수주주권 행사.", "행정제재.", "행정조치.", "준용·연대책임."}
AUTO_COMPARISON = []
AUTO_EFFECT = []


def _art(c):
    m = re.match(r"(상법|공인회계사법|외부감사법) (제\d+조(?:의\d+)?)", c["articleRefs"][0])
    return f"{m.group(1)} {m.group(2)}"


def _eff(c):
    e = (c.get("measurementRules") or "").strip()
    return e if 6 <= len(e) <= 60 and e not in GENERIC else None


def _siblings(c, law, key):
    """관련 개념(rel) → 같은 중단원 → 같은 대단원 순서로, key 값이 서로 다른 개념 2개."""
    pool = [law[r] for r in c["relatedConceptIds"] if r in law]
    pool += [x for x in law.values() if x["sectionId"] == c["sectionId"]]
    pool += [x for x in law.values() if x["chapterId"] == c["chapterId"]]
    out, seen = [], {key(c)}
    for x in pool:
        k = key(x)
        if x["id"] == c["id"] or k is None or k in seen or any(o["id"] == x["id"] for o in out):
            continue
        out.append(x)
        seen.add(k)
        if len(out) == 2:
            return out
    return None


def generate():
    law = {c["id"]: c for c in dsl.CONCEPTS if c["subjectId"] == "CPA-LAW"}
    by = {}
    for q in dsl.QUESTIONS:
        by.setdefault(q["primaryConceptId"], []).append(q)
    for cid, c in law.items():
        mine = by.get(cid, [])
        if not any(q["questionType"] in CMP_T for q in mine):
            sib = _siblings(c, law, _art)
            if sib:
                group = [c] + sib
                pairs = [(x["koreanName"], _art(x)) for x in group]
                exp = "개념별 주된 근거 조문: " + " / ".join(f"{x['koreanName']} — {_art(x)}" for x in group) + "."
                lslot(cid, f"[조문 연결] '{c['koreanName']}'과(와) 관련 개념을 각각의 주된 근거 조문과 연결하시오.", pairs, exp,
                      art=" ".join(_art(x) for x in group), t="comparison", lv=3, also=" ".join(x["id"] for x in sib))
                AUTO_COMPARISON.append(cid)
        if not any(q["questionType"] in CASE_T for q in mine) and _eff(c):
            sib = _siblings(c, law, _eff)
            if sib:
                group = [c] + sib
                pairs = [(x["koreanName"], _eff(x)) for x in group]
                exp = "개념별 법률효과: " + " / ".join(f"{x['koreanName']} — {_eff(x)}" for x in group)
                lslot(cid, f"[법률효과 연결] '{c['koreanName']}'과(와) 관련 개념을 각각의 법률효과와 연결하시오.", pairs, exp,
                      art=" ".join(_art(x) for x in group), t="effect", lv=3, also=" ".join(x["id"] for x in sib))
                AUTO_EFFECT.append(cid)


generate()

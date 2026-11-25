"""v7: 중급회계 제1부(기존 재무회계 v1·v2 기초 개념) '개념 구별' 문항 보강(구조화 자동 생성).

이 개념들은 v1·v2 당시 기본 빈칸 + 해설 중심으로 작성되어 심화(난이도 3)·응용형 문항이 없는 경우가 많다.
작성자가 이미 검토한 '개념 정의'만으로 정답이 확정되는 매칭형 문항을 만든다(새로운 회계 내용을 만들지 않는다):
  대상 개념 + 관련 개념 2개(관련 개념 → 같은 중단원 → 같은 대단원 순) 각각의 정의(개념명은 ○○로 가림)를 개념명과 연결.
law_auto.py(기업법)와 같은 방식이며, 기존 문항 ID 는 바뀌지 않고 새 문항만 뒤에 붙는다.
"""
import re

import dsl
from dsl import slot_q

BASIC_CHAPTERS = {f"FA-{c}" for c in "ABCDEFGHIJK"}
APPLICATION = {"choice", "matching", "classification", "comparison", "situation", "exception", "order",
               "journal", "calculation", "treatment", "correction", "number", "effect"}
GENERATED = []


def _clue(c, names):
    s = re.split(r"(?<=다)\.\s|(?<=\.)\s", c["definition"].strip())[0].strip().rstrip(".")
    for n in sorted(names, key=len, reverse=True):
        for v in {n, re.sub(r"\s*\(.*?\)", "", n).strip()}:
            if len(v) >= 2:
                s = s.replace(v, "○○")
    return s[:110] + ("…" if len(s) > 110 else "")


def _siblings(c, pool_by_id):
    pool = [pool_by_id[r] for r in c["relatedConceptIds"] if r in pool_by_id]
    pool += [x for x in pool_by_id.values() if x["sectionId"] == c["sectionId"]]
    pool += [x for x in pool_by_id.values() if x["chapterId"] == c["chapterId"]]
    out = []
    for x in pool:
        if x["id"] == c["id"] or any(o["id"] == x["id"] for o in out) or x["koreanName"] == c["koreanName"]:
            continue
        if any(o["koreanName"] == x["koreanName"] for o in out):
            continue
        out.append(x)
        if len(out) == 2:
            break
    return out if len(out) == 2 else None


def generate():
    basics = {c["id"]: c for c in dsl.CONCEPTS if c["chapterId"] in BASIC_CHAPTERS}
    by = {}
    for x in dsl.QUESTIONS:
        by.setdefault(x["primaryConceptId"], []).append(x)
    for cid, c in basics.items():
        mine = by.get(cid, [])
        if any(x["difficulty"] >= 3 for x in mine) and any(x["questionType"] in APPLICATION for x in mine):
            continue
        sib = _siblings(c, basics)
        if not sib:
            continue
        group = [c] + sib
        names = [x["koreanName"] for x in group]
        labels = [_clue(x, names) for x in group]
        if len(set(labels)) < 3 or any(len(l) < 8 for l in labels):
            continue
        pairs = list(zip(labels, names))
        exp = "정의 확인: " + " / ".join(f"{x['koreanName']} — {x['definition'].strip()[:80]}" for x in group)
        sec = next((x["title"] for x in dsl.SECTIONS if x["id"] == c["sectionId"]), "")
        qq = slot_q(cid, f"[개념 구별 {len(GENERATED) + 1}] {sec}: 다음 설명에 해당하는 개념을 연결하시오.", pairs, exp,
               t="comparison", lv=3, also=" ".join(x["id"] for x in sib))
        qq["generatedBy"] = "FA_AUTO_V7"
        GENERATED.append(cid)


generate()

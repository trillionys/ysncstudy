#!/usr/bin/env python3
"""CoverageValidator — 출제범위 목차별 콘텐츠 구축 상태를 '실제 데이터'로 판정한다.

빌드 결과(app/src/main/assets/content/*.json)를 읽어, 저장된 covered 값을 믿지 않고
목차 → 개념 → 문항 관계를 직접 확인한다.

COMPLETE 조건(모두 충족):
  1. 목차에 연결된 개념이 1개 이상이고, 모두 존재·활성이며 해당 목차의 과목 팩(또는 같은 시험과목) 개념
  2. 연결 개념마다 정의·간단 요약·상세 학습(DEFINITION 외 2개 이상 섹션)이 비어 있지 않음
  3. 연결 개념들의 활성 문항 합계 ≥ 3 (신규 개념 저작 기준은 단순 3·계산 4·복합 6문항 — 목차 판정은 3문항)
  4. 그 문항들이 모두 정답·해설을 가짐, 선택형은 정답이 보기에 있음, 빈칸형은 빈칸이 정확히 1개
상태: COMPLETE / IN_PROGRESS(개념은 있으나 위 조건 일부 미충족) / NOT_STARTED(연결 개념 없음)
       NEEDS_REVIEW(개념·문항이 검토 필요 표시) / OUT_OF_SCOPE(현재 해당 항목 없음 — 사용하지 않음)
누락 유형: A 개념·문항 없음, B 개념만 있고 문항 부족, C 문항은 있으나 설명 부족, D 요약만 있고 상세 없음,
          E 데이터는 있으나 연결 누락(자동 일치 후보 존재), F 법령·기준 검증 필요, G 범위 밖 의심

사용: python3 tools/content/coverage_validator.py [--strict]   (--strict: 미구축·무결성 오류가 있으면 종료코드 1)
출력: reports/content-gap-inventory.json, reports/content-gap-inventory.md, reports/coverage-summary.json
"""
import json
import os
import re
import sys
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSET = os.path.join(ROOT, "app", "src", "main", "assets", "content")
REPORTS = os.path.join(ROOT, "reports")

EXAM = {"IA": "ACC", "AA": "ACC", "CM": "ACC", "GA": "ACC", "FM": "BUS", "HR": "BUS", "EC": "ECO", "CPA-LAW": "LAW", "TX": "TAX"}
EXAM_NAME = {"ACC": "회계학", "BUS": "경영학", "ECO": "경제원론", "LAW": "기업법", "TAX": "세법개론"}
PACK_NAME = {"IA": "중급회계", "AA": "고급회계", "CM": "원가관리회계", "GA": "정부회계", "FM": "재무관리", "HR": "인사·조직",
             "EC": "경제학", "CPA-LAW": "기업법", "TX": "세법개론"}
BLANK = "(______)"


def load():
    m = json.load(open(os.path.join(ASSET, "manifest.json"), encoding="utf-8"))
    v = m["contentVersion"]
    s = json.load(open(os.path.join(ASSET, f"structure_v{v}.json"), encoding="utf-8"))
    c = json.load(open(os.path.join(ASSET, f"concepts_v{v}.json"), encoding="utf-8"))["concepts"]
    q = json.load(open(os.path.join(ASSET, f"questions_v{v}.json"), encoding="utf-8"))["questions"]
    return v, s, c, q


def norm(s):
    return re.sub(r"[\s·ㆍ,()\[\]\-]", "", unicodedata.normalize("NFC", s or "").lower())


def question_problems(x):
    p = []
    if not str(x.get("blankAnswer") or "").strip():
        p.append("정답 없음")
    if not str(x.get("explanation") or "").strip():
        p.append("해설 없음")
    if x.get("inputType") == "choice":
        if x.get("blankAnswer") not in (x.get("choices") or []):
            p.append("정답이 보기에 없음")
        if len(set(x.get("choices") or [])) != len(x.get("choices") or []):
            p.append("보기 중복")
    elif x.get("inputType") == "slots":
        if not x.get("slots"):
            p.append("슬롯 없음")
    else:
        if x["questionText"].count(BLANK) != 1:
            p.append("빈칸이 1개가 아님")
    if re.search(r"\bOX\b|[○×]\s*[/·]\s*[○×]", x["questionText"]):
        p.append("OX 문제")
    return p


def main():
    strict = "--strict" in sys.argv
    version, s, concepts, questions = load()
    by_c = {c["id"]: c for c in concepts}
    qs_by_c = {}
    for x in questions:
        qs_by_c.setdefault(x["primaryConceptId"], []).append(x)
    integrity = []

    # ── 무결성: ID 중복, 고아 데이터, 잘못된 참조 ──
    def dup(ids, label):
        seen, d = set(), set()
        for i in ids:
            (d if i in seen else seen).add(i)
        for i in sorted(d):
            integrity.append(f"{label} ID 중복: {i}")
    dup([c["id"] for c in concepts], "개념")
    dup([x["id"] for x in questions], "문항")
    dup([i["id"] for i in s["curriculumItems"]], "목차")
    subj = {x["id"] for x in s["subjects"]}
    chapters = {x["id"] for x in s["chapters"]}
    for c in concepts:
        if c["subjectId"] not in subj:
            integrity.append(f"개념 {c['id']} 과목 {c['subjectId']} 없음")
        if c["chapterId"] not in chapters:
            integrity.append(f"개념 {c['id']} 대단원 {c['chapterId']} 없음")
        for r in c.get("relatedConceptIds") or []:
            if r not in by_c:
                integrity.append(f"개념 {c['id']} 관련개념 {r} 없음")
    for x in questions:
        pc = by_c.get(x["primaryConceptId"])
        if not pc:
            integrity.append(f"문항 {x['id']} 의 개념 {x['primaryConceptId']} 없음(고아)")
            continue
        if x["isActive"] and not pc["isActive"]:
            integrity.append(f"활성 문항 {x['id']} 이 비활성 개념 {pc['id']} 참조")
        if x["isActive"]:
            for p in question_problems(x):
                integrity.append(f"문항 {x['id']}: {p}")

    # ── 목차별 판정 ──
    items_out = []
    for it in s["curriculumItems"]:
        pack = it["subjectId"]
        cids = it["conceptIds"]
        missing = []
        cons = [by_c.get(cid) for cid in cids]
        for cid, c in zip(cids, cons):
            if c is None:
                missing.append(f"연결 개념 {cid} 없음")
            elif not c["isActive"]:
                missing.append(f"연결 개념 {cid} 비활성")
            elif EXAM.get(c["subjectId"]) != EXAM.get(pack):
                missing.append(f"연결 개념 {cid} 시험과목 불일치({c['subjectId']})")
        cons = [c for c in cons if c and c["isActive"]]
        aq = [x for c in cons for x in qs_by_c.get(c["id"], []) if x["isActive"]]
        need_q = 3
        bad_q = [f"{x['id']}:{p}" for x in aq for p in question_problems(x)]
        no_detail = [c["id"] for c in cons if not (c.get("definition") or "").strip() or not (c.get("shortSummary") or "").strip()
                     or len([d for d in (c.get("detailSections") or []) if d.get("type") != "DEFINITION" and (d.get("body") or "").strip()]) < 2]
        review = [c["id"] for c in cons if c.get("requiresReview")] + [x["id"] for x in aq if x.get("requiresReview")]
        if not cids:
            status, typ = "NOT_STARTED", "A"
            missing.append("개념·문항 없음")
        else:
            typ = None
            if len(aq) < need_q:
                missing.append(f"활성 문항 {len(aq)}개(필요 {need_q}개)")
                typ = "B"
            if no_detail:
                missing.append(f"설명·상세 학습 부족: {' '.join(no_detail)}")
                typ = typ or "D"
            if bad_q:
                missing.append(f"문항 오류: {', '.join(bad_q[:5])}")
                typ = typ or "C"
            status = "COMPLETE" if not missing else "IN_PROGRESS"
            if status == "COMPLETE" and review:
                status, typ = "NEEDS_REVIEW", "F"
        # E: 연결은 없지만 이름이 일치하는 개념이 데이터에 있음(연결 누락 후보)
        cand = []
        if not cids:
            key = norm(it["item"])
            if len(key) >= 2:
                cand = [c["id"] for c in concepts if c["isActive"] and EXAM.get(c["subjectId"]) == EXAM.get(pack) and key in norm(c["koreanName"])][:5]
            if cand:
                typ = "E"
        items_out.append({
            "scopeNodeId": it["id"], "officialSubject": EXAM_NAME[EXAM[pack]], "contentPack": PACK_NAME.get(pack, pack),
            "packId": pack, "parentChapter": it["unit"], "scopeTitle": it["item"],
            "existingConceptIds": cids, "existingQuestionIds": [x["id"] for x in aq],
            "missingComponents": missing, "currentCoverageStatus": status, "gapType": typ if status != "COMPLETE" else None,
            "linkCandidates": cand, "storedCoveredFlag": it["covered"],
        })

    # ── 집계 ──
    summary = {"contentVersion": version, "exams": {}, "packs": {}, "integrityErrors": len(integrity)}
    for ex, nm in EXAM_NAME.items():
        its = [i for i in items_out if EXAM[i["packId"]] == ex]
        done = sum(i["currentCoverageStatus"] in ("COMPLETE", "NEEDS_REVIEW") for i in its)
        summary["exams"][nm] = {"complete": done, "total": len(its), "missing": len(its) - done,
                                "needsReview": sum(i["currentCoverageStatus"] == "NEEDS_REVIEW" for i in its),
                                "storedFlagCovered": sum(i["storedCoveredFlag"] for i in its)}
    for p in PACK_NAME:
        its = [i for i in items_out if i["packId"] == p]
        done = sum(i["currentCoverageStatus"] in ("COMPLETE", "NEEDS_REVIEW") for i in its)
        summary["packs"][p] = {"complete": done, "total": len(its)}
    tot = sum(e["total"] for e in summary["exams"].values())
    dn = sum(e["complete"] for e in summary["exams"].values())
    summary["overall"] = {"complete": dn, "total": tot, "missing": tot - dn}

    os.makedirs(REPORTS, exist_ok=True)
    gaps = [i for i in items_out if i["currentCoverageStatus"] not in ("COMPLETE", "NEEDS_REVIEW")]
    json.dump({"summary": summary, "gaps": gaps, "integrity": integrity}, open(os.path.join(REPORTS, "content-gap-inventory.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.dump({"summary": summary, "items": items_out}, open(os.path.join(REPORTS, "coverage-all-items.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    md = [f"# 콘텐츠 미구축 목차 목록 (콘텐츠 v{version})", "", "CoverageValidator 가 빌드된 실제 데이터(목차→개념→문항)로 판정한 결과입니다.", "",
          "| 시험과목 | 구축 | 전체 | 미구축 | 구축률 |", "|---|---:|---:|---:|---:|"]
    for nm, e in summary["exams"].items():
        md.append(f"| {nm} | {e['complete']} | {e['total']} | {e['missing']} | {e['complete'] / e['total'] * 100:.1f}% |" if e["total"] else f"| {nm} | 0 | 0 | 0 | - |")
    o = summary["overall"]
    md.append(f"| **전체** | {o['complete']} | {o['total']} | {o['missing']} | {o['complete'] / o['total'] * 100:.1f}% |")
    md += ["", f"무결성 오류: {len(integrity)}건", ""]
    for i in integrity[:50]:
        md.append(f"- {i}")
    cur = None
    for g in gaps:
        head = f"{g['officialSubject']} · {g['contentPack']} · {g['parentChapter']}"
        if head != cur:
            md += ["", f"## {head}", "", "| 목차 ID | 항목 | 유형 | 상태 | 부족한 점 |", "|---|---|---|---|---|"]
            cur = head
        md.append(f"| {g['scopeNodeId']} | {g['scopeTitle']} | {g['gapType']} | {g['currentCoverageStatus']} | {'; '.join(g['missingComponents'])} |")
    open(os.path.join(REPORTS, "content-gap-inventory.md"), "w", encoding="utf-8").write("\n".join(md) + "\n")
    json.dump(summary, open(os.path.join(REPORTS, "coverage-summary.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    for nm, e in summary["exams"].items():
        print(f"{nm}: {e['complete']}/{e['total']} (미구축 {e['missing']}, 저장된 covered 값 {e['storedFlagCovered']})")
    print(f"전체: {o['complete']}/{o['total']} (미구축 {o['missing']}) · 무결성 오류 {len(integrity)}건")
    by_type = {}
    for g in gaps:
        by_type[g["gapType"]] = by_type.get(g["gapType"], 0) + 1
    print("미구축 유형:", dict(sorted(by_type.items(), key=lambda kv: str(kv[0]))))
    if strict and (gaps or integrity):
        sys.exit(1)


if __name__ == "__main__":
    main()

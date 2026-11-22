#!/usr/bin/env python3
"""콘텐츠 원본(tools/content/*.py)을 검증하고 앱 assets JSON 으로 생성한다.

사용법:  python3 tools/build_content.py            # 검증 + JSON 생성 + 리포트
         python3 tools/build_content.py --check    # 검증만 (파일 미생성)

검증 실패가 1건이라도 있으면 종료코드 1 로 끝나며 JSON 을 쓰지 않는다.
"""
import datetime
import hashlib
import json
import os
import re
import sys
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools", "content"))

import dsl  # noqa: E402
import ch_a, ch_b, ch_c, ch_d, ch_e, ch_f, ch_g, ch_h, ch_i, ch_j, ch_k  # noqa: E402,F401
import exam_scopes  # noqa: E402,F401
from required_concepts import REQUIRED  # noqa: E402

CONTENT_VERSION = 2
FORMAT_VERSION = 1
ASSET_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "content")
REGISTRY = os.path.join(ROOT, "tools", "content", "id_registry.json")
REPORT = os.path.join(ROOT, "docs", "CONTENT_VALIDATION_REPORT.md")
BLANK = "(______)"

SUBJECTS = [
    {"id": "FA", "name": "재무회계", "description": "재무보고를 위한 개념체계 및 재무제표 표시", "sortOrder": 1, "isActive": True},
    {"id": "IA", "name": "중급회계", "description": "콘텐츠 팩 추가 시 활성화", "sortOrder": 2, "isActive": False},
    {"id": "AA", "name": "고급회계", "description": "콘텐츠 팩 추가 시 활성화", "sortOrder": 3, "isActive": False},
    {"id": "CM", "name": "원가관리회계", "description": "콘텐츠 팩 추가 시 활성화", "sortOrder": 4, "isActive": False},
    {"id": "FM", "name": "재무관리", "description": "콘텐츠 팩 추가 시 활성화", "sortOrder": 5, "isActive": False},
    {"id": "EC", "name": "경제학", "description": "콘텐츠 팩 추가 시 활성화", "sortOrder": 6, "isActive": False},
    {"id": "BL", "name": "기업법", "description": "콘텐츠 팩 추가 시 활성화", "sortOrder": 7, "isActive": False},
    {"id": "TX", "name": "세법개론", "description": "콘텐츠 팩 추가 시 활성화", "sortOrder": 8, "isActive": False},
]
SUBJECTS[0]["isActive"] = True


def norm(s):
    s = unicodedata.normalize("NFC", s or "").lower()
    s = re.sub(r"\s+", "", s)
    s = s.strip(".,;:!?·'\"()[]")
    return s


def main():
    check_only = "--check" in sys.argv
    errors, warnings = [], []
    concepts = dsl.CONCEPTS
    questions = dsl.QUESTIONS
    scopes = dsl.EXAM_SCOPES
    cmap = {c["id"]: c for c in concepts}

    # 0. 중복 ID
    seen = set()
    for c in concepts:
        if c["id"] in seen:
            errors.append(f"[ID] 중복 conceptId {c['id']}")
        seen.add(c["id"])
    qseen = set()
    for x in questions:
        if x["id"] in qseen:
            errors.append(f"[ID] 중복 questionId {x['id']}")
        qseen.add(x["id"])

    # 1. 필수 개념 존재 + 이름 일치
    for rid, rname in REQUIRED:
        if rid not in cmap:
            errors.append(f"[1] 필수 개념 누락: {rid} {rname}")
    extra = [c["id"] for c in concepts if c["id"] not in dict(REQUIRED)]
    if extra:
        warnings.append(f"필수 목록 외 추가 개념: {', '.join(extra)}")

    # 2. 설명 필드
    fields = ["definition", "whyItMatters", "detailedExplanation", "comparison", "example", "examTips", "commonMistakes"]
    explained = 0
    for c in concepts:
        miss = [f for f in fields if not (c.get(f) or "").strip()]
        if miss:
            errors.append(f"[2] {c['id']} 설명 누락: {miss}")
        else:
            explained += 1

    # 3. 개념별 기본 문항 수 (해당 개념을 primary 로 하는 활성 문항)
    active_q = [x for x in questions if x["isActive"]]
    per_concept = {c["id"]: 0 for c in concepts}
    linked = {c["id"]: 0 for c in concepts}
    for x in active_q:
        per_concept[x["primaryConceptId"]] = per_concept.get(x["primaryConceptId"], 0) + 1
        for cid in x["conceptIds"]:
            linked[cid] = linked.get(cid, 0) + 1
    for cid, n in per_concept.items():
        if n < 2:
            errors.append(f"[3] {cid} 기본 문항 {n}개 (<2)")

    # 4. 출제범위 01~46
    nums = {s["number"] for s in scopes}
    for i in range(1, 47):
        if f"{i:02d}" not in nums:
            errors.append(f"[4] 출제범위 {i:02d} 미등록")
    scope_q = {}
    for s in scopes:
        if not s["conceptIds"]:
            errors.append(f"[4] 출제범위 {s['number']} 연결 개념 없음")
        for cid in s["conceptIds"]:
            if cid not in cmap:
                errors.append(f"[9] 출제범위 {s['number']} 미존재 개념 {cid}")
        sc = set(s["conceptIds"])
        n = sum(1 for x in active_q if sc & set(x["conceptIds"]))
        scope_q[s["number"]] = n
        if n < 3:
            errors.append(f"[4] 출제범위 {s['number']} 관련 문항 {n}개 (<3)")
    # 개념·문항에 기출번호 부여
    for c in concepts:
        c["examReferenceNumbers"] = sorted(s["number"] for s in scopes if c["id"] in s["conceptIds"])
    for x in questions:
        refs = set()
        for s in scopes:
            if set(s["conceptIds"]) & set(x["conceptIds"]):
                refs.add(s["number"])
        x["examReferenceNumbers"] = sorted(refs)

    # 5~8, 11. 문항 무결성
    names = {}
    for c in concepts:
        names.setdefault(norm(c["koreanName"]), set()).add(c["id"])
    text_seen = {}
    for x in questions:
        qid = x["id"]
        if not (x["explanation"] or "").strip():
            errors.append(f"[5] {qid} 해설 없음")
        if not norm(x["blankAnswer"]):
            errors.append(f"[6] {qid} 정답 없음")
        if x["questionText"].count(BLANK) != 1:
            errors.append(f"[11] {qid} 빈칸 '{BLANK}' 개수 {x['questionText'].count(BLANK)} (정확히 1개여야 기본·심화 모드 출제 가능)")
        body = norm(x["questionText"].replace(BLANK, "#"))
        ans_n = norm(x["blankAnswer"])
        if re.fullmatch(r"[\d,.]+", ans_n):
            exposed = re.search(r"(?<![\d,.])" + re.escape(ans_n) + r"(?![\d,.])", body) is not None
        else:
            exposed = ans_n in body
        if exposed:
            errors.append(f"[8] {qid} 정답 '{x['blankAnswer']}' 이 문제 문장에 그대로 노출됨")
        # 7. 동의어 모순: 다른 개념명과 같은 동의어
        alts = [norm(a) for a in x["acceptedAnswers"]]
        if len(set(alts)) != len(alts):
            warnings.append(f"{qid} acceptedAnswers 중복 표기")
        for a in x["acceptedAnswers"][1:]:
            owners = names.get(norm(a), set()) - set(x["conceptIds"])
            if owners and norm(a) != norm(x["blankAnswer"]):
                errors.append(f"[7] {qid} 동의어 '{a}' 가 다른 개념명({', '.join(sorted(owners))})과 같음")
        for cid in x["conceptIds"]:
            if cid not in cmap:
                errors.append(f"[9] {qid} 미존재 개념 {cid}")
        if body in text_seen:
            errors.append(f"[8] 문제 문장 중복: {qid} = {text_seen[body]}")
        text_seen[body] = qid
        if x["unit"] and not re.fullmatch(r"[\d,.\-]+", x["blankAnswer"].replace(" ", "")):
            warnings.append(f"{qid} 단위가 지정되었으나 정답이 숫자가 아님")
        if x["questionType"] == "calculation" and not x.get("calculation"):
            errors.append(f"[5] {qid} 계산문제에 계산과정(calculation) 없음")
        if re.search(r"(은|는|을|를)$", x["blankAnswer"]) and len(x["blankAnswer"]) > 2:
            warnings.append(f"{qid} 정답 '{x['blankAnswer']}' 이 조사로 끝나는지 확인 필요")
    for c in concepts:
        for k in ("relatedConceptIds", "prerequisiteConceptIds"):
            for r in c[k]:
                if r not in cmap:
                    errors.append(f"[9] {c['id']} {k} 미존재 개념 {r}")
        if c["parentConceptId"] and c["parentConceptId"] not in cmap:
            errors.append(f"[9] {c['id']} parent 미존재 {c['parentConceptId']}")

    # 10. 비활성 문항이 집계에서 제외되는지 (집계는 active_q 기준)
    inactive = [x["id"] for x in questions if not x["isActive"]]

    # 12. 모든 개념이 출제 후보에 포함되는가
    pool_concepts = {cid for x in active_q for cid in x["conceptIds"]}
    for c in concepts:
        if c["isActive"] and c["id"] not in pool_concepts:
            errors.append(f"[12] {c['id']} 균등출제 후보에 없음")

    # ID 레지스트리: 이미 발급한 ID 의 정답이 바뀌면 오류(새 ID 발급 필요)
    registry = {}
    if os.path.exists(REGISTRY):
        with open(REGISTRY, encoding="utf-8") as f:
            registry = json.load(f)
    for x in questions:
        h = hashlib.sha1((x["primaryConceptId"] + "|" + norm(x["blankAnswer"])).encode()).hexdigest()[:12]
        old = registry.get("questions", {}).get(x["id"])
        if old and old != h:
            errors.append(f"[ID] {x['id']} 기존 발급 ID 의 정답/개념이 변경됨 → 새 questionId 를 발급하거나 revision 정책 적용")
        registry.setdefault("questions", {})[x["id"]] = h
    for c in concepts:
        old = registry.get("concepts", {}).get(c["id"])
        if old and old != c["koreanName"]:
            errors.append(f"[ID] {c['id']} 기존 개념명 '{old}' 와 다름 → ID 재사용 금지")
        registry.setdefault("concepts", {})[c["id"]] = c["koreanName"]

    by_type = {}
    for x in active_q:
        by_type[x["questionType"]] = by_type.get(x["questionType"], 0) + 1
    calc = by_type.get("calculation", 0)

    lines = []
    lines.append("# 콘텐츠 검증 리포트 (Python 빌드 검증기)")
    lines.append("")
    lines.append(f"- 생성 시각: {datetime.datetime.now().isoformat(timespec='seconds')}")
    lines.append(f"- 콘텐츠 버전: {CONTENT_VERSION}")
    lines.append("")
    lines.append("| 항목 | 결과 |")
    lines.append("|---|---|")
    lines.append(f"| 전체 필수 개념 | {len(REQUIRED)}개 |")
    lines.append(f"| 등록된 개념 | {len(concepts)}개 |")
    lines.append(f"| 설명 완료 | {explained}개 |")
    lines.append(f"| 문제 연결 완료(기본 문항 ≥2) | {sum(1 for v in per_concept.values() if v >= 2)}개 |")
    lines.append(f"| 기본 문항(활성) | {len(active_q)}개 |")
    lines.append(f"| 기본 모드(초성) 이용 가능 | {sum(1 for x in active_q if x['questionText'].count(BLANK) == 1)}개 |")
    lines.append(f"| 심화 모드(무힌트) 이용 가능 | {sum(1 for x in active_q if x['questionText'].count(BLANK) == 1)}개 |")
    lines.append(f"| 비활성 문항(집계 제외) | {len(inactive)}개 |")
    lines.append(f"| 계산 문항 | {calc}개 |")
    lines.append(f"| 출제범위 01~46 연결 | {sum(1 for n in range(1, 47) if scope_q.get(f'{n:02d}', 0) >= 3)}/46 |")
    lines.append(f"| 검증 실패 | {len(errors)}건 |")
    lines.append(f"| 경고 | {len(warnings)}건 |")
    lines.append("")
    lines.append("## 문항 유형 분포")
    lines.append("")
    for k, v in sorted(by_type.items(), key=lambda kv: -kv[1]):
        lines.append(f"- {k}: {v}")
    lines.append("")
    lines.append("## 대단원별 개념·문항 수")
    lines.append("")
    lines.append("| 대단원 | 개념 | 문항 |")
    lines.append("|---|---|---|")
    for ch in dsl.CHAPTERS:
        nc = sum(1 for c in concepts if c["chapterId"] == ch["id"])
        nq = sum(1 for x in active_q if x["chapterId"] == ch["id"])
        lines.append(f"| {ch['code']}. {ch['title']} | {nc} | {nq} |")
    lines.append("")
    lines.append("## 출제범위(기출 01~46)별 연결 문항 수")
    lines.append("")
    lines.append("| 번호 | 주제 | 연결 개념 | 관련 문항 |")
    lines.append("|---|---|---|---|")
    for s in sorted(scopes, key=lambda s: s["number"]):
        lines.append(f"| {s['number']} | {s['title']} | {' '.join(s['conceptIds'])} | {scope_q[s['number']]} |")
    lines.append("")
    lines.append("## 개념별 문항 수")
    lines.append("")
    lines.append("| 개념 | 이름 | 기본 문항(주개념) | 연결 문항(전체) |")
    lines.append("|---|---|---|---|")
    for c in concepts:
        lines.append(f"| {c['id']} | {c['koreanName']} | {per_concept[c['id']]} | {linked[c['id']]} |")
    lines.append("")
    if errors:
        lines.append("## 오류")
        lines += [f"- {e}" for e in errors]
    if warnings:
        lines.append("## 경고")
        lines += [f"- {w}" for w in warnings]

    print("\n".join(lines[:22]))
    if errors:
        print("\n".join(errors[:80]))
        print(f"\n검증 실패 {len(errors)}건 — JSON 을 생성하지 않습니다.")
        if not check_only:
            with open(REPORT, "w", encoding="utf-8") as f:
                f.write("\n".join(lines) + "\n")
        sys.exit(1)
    for w in warnings[:30]:
        print("WARN", w)
    if check_only:
        return

    os.makedirs(ASSET_DIR, exist_ok=True)
    gen = datetime.date.today().isoformat()

    def pack(pack_id, **kw):
        d = {"formatVersion": FORMAT_VERSION, "contentVersion": CONTENT_VERSION, "packId": pack_id,
             "generatedAt": gen}
        d.update(kw)
        return d

    files = {
        f"structure_v{CONTENT_VERSION}.json": pack("fa-structure", subjects=SUBJECTS, chapters=dsl.CHAPTERS,
                                                   sections=dsl.SECTIONS, examScopes=scopes),
        f"concepts_v{CONTENT_VERSION}.json": pack("fa-concepts", concepts=concepts),
        f"questions_v{CONTENT_VERSION}.json": pack("fa-questions", questions=questions),
    }
    for name in os.listdir(ASSET_DIR):
        if name.endswith(".json"):
            os.remove(os.path.join(ASSET_DIR, name))
    for name, data in files.items():
        with open(os.path.join(ASSET_DIR, name), "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=1)
    manifest = {
        "formatVersion": FORMAT_VERSION,
        "contentVersion": CONTENT_VERSION,
        "contentId": "cpa-concept-lab-bundled",
        "generatedAt": gen,
        "files": list(files.keys()),
        "conceptCount": len(concepts),
        "questionCount": len(active_q),
        "requiredConceptIds": [rid for rid, _ in REQUIRED],
    }
    with open(os.path.join(ASSET_DIR, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    with open(REGISTRY, "w", encoding="utf-8") as f:
        json.dump(registry, f, ensure_ascii=False, indent=0, sort_keys=True)
    with open(REPORT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"\n생성 완료: {ASSET_DIR}")


if __name__ == "__main__":
    main()

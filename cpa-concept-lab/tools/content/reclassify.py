"""v7: '재무회계(FA)' 독립 팩 해체 → 중급회계(IA)·고급회계(AA)·정부회계(GA) 재분류.

원칙
- conceptId·questionId 는 바꾸지 않는다(학습기록은 이 ID 로 연결된다). 소속 과목·대단원 연결만 바꾼다.
- 분류는 단어 검색이 아니라 '주된 회계 주제와 적용 기준서'로 한다.
  1) 대단원(chapter)의 주제·기준서로 기본 소속을 정한다(CHAPTER_RULES).
  2) 개념마다 근거 기준서를 다시 확인해, 대단원과 다른 과목의 주된 주제를 다루는 개념은 개별 규칙(CONCEPT_RULES)으로 옮긴다.
  3) 다른 과목과 관련만 있는 개념은 소속은 그대로 두고 relatedPackIds 로만 연결한다(RELATED_RULES).
  4) 위 규칙으로 결정되지 않으면 NEEDS_CLASSIFICATION_REVIEW 로 표시한다(삭제·덮어쓰기 금지).
- 정부보조금(K-IFRS 제1020호)처럼 '정부'가 등장하는 기업회계 주제는 정부회계가 아니라 중급회계다.
"""
import dsl

LEGACY = "FA"

# 대단원 → (새 과목, 새 파트명, 근거)
BASICS = "제1부 재무회계의 기초"
CHAPTER_RULES = {
    "FA-A": ("IA", BASICS, "회계정보의 경제적 기능(정보비대칭·대리인 문제) — 재무보고의 기초"),
    "FA-B": ("IA", BASICS, "회계기준과 K-IFRS 구조 — 재무보고의 기초"),
    "FA-C": ("IA", BASICS, "재무보고를 위한 개념체계 제1장·제3장 — 일반목적재무보고"),
    "FA-D": ("IA", BASICS, "개념체계 제2장 질적특성"),
    "FA-E": ("IA", BASICS, "개념체계 제4장 재무제표 요소"),
    "FA-F": ("IA", BASICS, "개념체계 제5장 인식과 제거"),
    "FA-G": ("IA", BASICS, "개념체계 제8장 자본과 자본유지"),
    "FA-H": ("IA", BASICS, "개념체계 제6장 측정기준"),
    "FA-I": ("IA", BASICS, "K-IFRS 제1001호 재무제표 표시"),
    "FA-J": ("IA", BASICS, "K-IFRS 제1001호 유동·비유동 분류"),
    "FA-K": ("IA", BASICS, "K-IFRS 제1118호 재무제표 표시와 공시"),
    "FA-L": ("IA", "재고자산", "K-IFRS 제1002호 재고자산"),
    "FA-M": ("IA", "재고자산", "K-IFRS 제1002호 감모·평가손실"),
    "FA-N": ("IA", "재고자산", "K-IFRS 제1002호 원가측정 방법(소매재고법 등)"),
    "FA-P": ("IA", "유형자산", "K-IFRS 제1016호 유형자산(제1020호 정부보조금 포함 — 기업회계이므로 중급회계)"),
    "FA-Q": ("IA", "차입원가", "K-IFRS 제1023호 차입원가"),
    "FA-R": ("IA", "재고자산", "K-IFRS 제1041호 농림어업"),
}

# 개념의 주된 주제가 다른 과목인 경우: conceptId → (과목, 새 대단원 ID, 근거)
CONCEPT_RULES = {
    "B13": ("AA", "AA-A", "연결재무제표의 의의 — K-IFRS 제1110호 연결 주제(고급회계)"),
    "B14": ("AA", "AA-A", "지배력의 개념 — K-IFRS 제1110호(고급회계)"),
    "B15": ("AA", "AA-A", "종속기업의 연결범위 — K-IFRS 제1110호(고급회계)"),
}

# 소속은 유지하고 관련 과목만 연결: conceptId → (관련 과목, 근거)
RELATED_RULES = {
    "C22": ("AA", "개념체계의 보고기업(연결·결합재무제표) — 개념체계 주제라 중급회계 유지, 연결은 고급회계 참조"),
    "I45": ("AA", "현금흐름위험회피 효과적인 부분의 OCI 표시 — 표시 주제라 중급회계 유지, 위험회피회계는 고급회계 참조"),
    "K04": ("AA", "IFRS 18 투자 범주(관계기업 투자손익 표시) — 표시 주제라 중급회계 유지"),
    "L011": ("AA", "외화로 매입한 재고자산의 취득원가 — 재고자산 주제라 중급회계 유지, 외화환산은 고급회계 참조"),
    "Q019": ("AA", "외화차입금 관련 차입원가 — 차입원가 주제라 중급회계 유지"),
}

REPORT = []   # (conceptId, 이름, 이전 대단원, 새 과목, 새 대단원, 상태, 근거)


def apply():
    """dsl 의 대단원·개념·문항을 새 과목으로 옮긴다. build_content.main() 시작 시 1회 호출."""
    chapters = {ch["id"]: ch for ch in dsl.CHAPTERS}
    for cid, rule in CHAPTER_RULES.items():
        ch = chapters[cid]
        assert ch["subjectId"] == LEGACY, cid
        ch["subjectId"], ch["partTitle"] = rule[0], rule[1]
        ch["legacyPackId"] = LEGACY
    unknown_ch = [ch["id"] for ch in dsl.CHAPTERS if ch["subjectId"] == LEGACY]
    assert not unknown_ch, f"분류 규칙이 없는 재무회계 대단원: {unknown_ch}"

    by_concept_q = {}
    for x in dsl.QUESTIONS:
        by_concept_q.setdefault(x["primaryConceptId"], []).append(x)

    moved_sections = {}
    for c in dsl.CONCEPTS:
        if c.get("subjectId") != LEGACY and c["chapterId"] not in CHAPTER_RULES:
            c.setdefault("primaryPackId", c["subjectId"])
            c.setdefault("relatedPackIds", [])
            c.setdefault("classificationStatus", "ORIGINAL")
            c.setdefault("legacyPackId", None)
            continue
        old_ch = c["chapterId"]
        pack, _, why = CHAPTER_RULES[old_ch]
        status, related = "CLASSIFIED", []
        new_ch = old_ch
        if c["id"] in CONCEPT_RULES:
            pack, new_ch, why = CONCEPT_RULES[c["id"]]
            related = ["IA"]
            # 옮긴 개념의 문항도 같은 대단원·중단원으로 따라간다(문항 ID 유지)
            sec = moved_sections.get(new_ch)
            if sec is None:
                sec = f"{new_ch}-S90"
                moved_sections[new_ch] = sec
            c["sectionId"] = sec
            for x in by_concept_q.get(c["id"], []):
                x["chapterId"], x["sectionId"], x["subjectId"] = new_ch, sec, pack
                x["legacyPackId"] = LEGACY
        elif c["id"] in RELATED_RULES:
            rp, why = RELATED_RULES[c["id"]]
            related = [rp]
        c["subjectId"] = pack
        c["chapterId"] = new_ch
        c["primaryPackId"] = pack
        c["relatedPackIds"] = related
        c["classificationStatus"] = status
        c["legacyPackId"] = LEGACY
        REPORT.append((c["id"], c["koreanName"], old_ch, pack, new_ch, status, why))
    for x in dsl.QUESTIONS:
        if x["chapterId"] in CHAPTER_RULES:
            x["subjectId"] = CHAPTER_RULES[x["chapterId"]][0]
            x["legacyPackId"] = LEGACY
        elif x.get("subjectId") == LEGACY or x.get("legacyPackId") == LEGACY:
            x["legacyPackId"] = LEGACY
        else:
            x.setdefault("legacyPackId", None)
    return moved_sections


def ensure_moved_sections(moved_sections):
    """옮겨 온 개념을 담을 중단원(고급회계 대단원 안 '재무회계에서 이동한 개념')을 만든다."""
    have = {s["id"] for s in dsl.SECTIONS}
    for ch_id, sec_id in moved_sections.items():
        if sec_id in have:
            continue
        dsl.SECTIONS.append({"id": sec_id, "chapterId": ch_id, "title": "지배력과 연결범위(기존 재무회계에서 이동)",
                             "sortOrder": 90})


def report_lines(concepts, questions):
    from collections import Counter
    cnt = Counter(r[3] for r in REPORT)
    review = [r for r in REPORT if r[5] != "CLASSIFIED"]
    q_moved = [x for x in questions if x.get("legacyPackId") == LEGACY and not x.get("generatedBy")]
    lines = ["", "## v7 재무회계(FA) 팩 재분류", "",
             f"- 기존 재무회계 개념 {len(REPORT)}개 · 문항 {len(q_moved)}개 (ID 변경 없음)",
             f"- 중급회계 {cnt.get('IA', 0)}개 · 고급회계 {cnt.get('AA', 0)}개 · 정부회계 {cnt.get('GA', 0)}개 · 분류 검토 필요 {len(review)}개",
             "- 정부회계로 옮긴 개념이 0개인 이유: 기존 재무회계 팩에는 국가·지방자치단체 회계 개념이 없었다"
             "(K-IFRS 제1020호 정부보조금은 기업회계이므로 중급회계).", "",
             "| 개념 | 이름 | 이전 대단원 | 새 과목 | 새 대단원 | 상태 | 근거 |", "|---|---|---|---|---|---|---|"]
    for r in REPORT:
        if r[3] != "IA" or r[0] in RELATED_RULES:
            lines.append(f"| {r[0]} | {r[1]} | {r[2]} | {r[3]} | {r[4]} | {r[5]} | {r[6]} |")
    lines.append("")
    lines.append("(위 표는 대단원 기본 규칙과 다르게 처리했거나 관련 과목을 연결한 개념만 표시. 나머지는 대단원 규칙대로 중급회계.)")
    lines.append("")
    lines.append("| 이전 대단원 | 새 과목 | 새 파트 | 근거 |")
    lines.append("|---|---|---|---|")
    for k, (p, part, why) in CHAPTER_RULES.items():
        lines.append(f"| {k} | {p} | {part} | {why} |")
    return lines

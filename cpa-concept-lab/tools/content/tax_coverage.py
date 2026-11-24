"""세법개론(TX) 조문 커버리지·실전 비중 영역.

시험범위(금융위원회 공고 제2026-284호): 국세기본법, 법인세법, 소득세법, 부가가치세법, 상속세 및 증여세법,
지방세법(취득세·등록면허세·재산세에 한함), 조세특례제한법(관련 세목 범위). 시행령·시행규칙 포함이나
이 앱은 법률 조문만 원문 대조했다(시행령·시행규칙 원문 미확보).

조문 상태는 기업법(law_coverage)과 같은 코드를 쓴다. 조세특례제한법은 전체 조문 중 관련 세목 학습에 쓰인 조문만
연결되므로, 연결되지 않은 조특법 조문은 RELATED_ONLY 가 아니라 '학습 콘텐츠 작성 중'(WRITING)으로 정직하게 집계한다.
"""
import re

import lawdsl
import tax_common
from law_coverage import STATUS_LABEL

SUBJECT = "TX"
LOCAL_SCOPE = ("제1장 총칙", "제2장 취득세", "제3장 등록면허세", "제9장 재산세")
GROUPS = [("국세기본법", "국세기본법"), ("법인세법", "법인세법"), ("소득세법", "소득세법"), ("부가가치세법", "부가가치세법"),
          ("상속세 및 증여세법", "상증법"), ("지방세법(취득·등록면허·재산세)", "지방세법"), ("조세특례제한법", "조특법")]
_LABEL = re.compile(r"(국세기본법|법인세법|소득세법|부가가치세법|상증법|지방세법|조특법) (제\d+조(?:의\d+)?)")
# 개념 장(chapter) → 영역(근거 법률만으로 정하기 어려운 조특법·종합 단원용)
CHAPTER_AREA = {"TX-A": "TAX_BASIC", "TX-B": "CORP", "TX-C": "INCOME", "TX-D": "VAT", "TX-E": "INH_LOCAL", "TX-F": "INH_LOCAL"}


def in_scope(law, a):
    if law == "지방세법":
        return (a["chapter"] or "").startswith(LOCAL_SCOPE)
    return True


def concept_area(c):
    if c["id"] in tax_common.CONCEPT_AREA:
        return tax_common.CONCEPT_AREA[c["id"]]
    if c["chapterId"] in CHAPTER_AREA:
        return CHAPTER_AREA[c["chapterId"]]
    m = _LABEL.match(c["articleRefs"][0])
    return tax_common.LAW_AREA.get(m.group(1), "CORP")


def build(concepts, questions, errors, warnings):
    tx_concepts = [c for c in concepts if c["subjectId"] == SUBJECT]
    cmap = {c["id"]: c for c in tx_concepts}
    active = {x["id"]: x for x in questions if x["isActive"] and x["subjectId"] == SUBJECT}
    for c in tx_concepts:
        c["examArea"] = concept_area(c)
    for x in questions:
        if x["subjectId"] == SUBJECT:
            x["examArea"] = cmap[x["primaryConceptId"]]["examArea"]

    cited_q, linked_c = {}, {}
    for qid, refs in lawdsl.QUESTION_ARTICLES.items():
        if qid in active:
            for law, aid, _ in refs:
                cited_q.setdefault((law, aid), []).append(qid)
    for c in tx_concepts:
        for label in c["articleRefs"]:
            m = _LABEL.match(label)
            if m:
                linked_c.setdefault((m.group(1), m.group(2)), []).append(c["id"])

    articles = []
    for law in [g[1] for g in GROUPS]:
        meta = lawdsl.LAWS[law]
        for aid, a in lawdsl.ARTICLES[law].items():
            key = (law, aid)
            scope = in_scope(law, a)
            if a["deleted"]:
                status = "DELETED"
            elif not scope:
                status = "OUT_OF_SCOPE"
            elif key in cited_q:
                status = "COMPLETED"
            elif key in linked_c:
                status = "INTEGRATED"
            else:
                status = "WRITING"
            if not scope and (key in cited_q or key in linked_c):
                errors.append(f"[TX] 시험범위 밖 조문을 근거로 한 학습 콘텐츠: {law} {aid}")
            # 용량: 조특법은 학습에 연결된 조문만 원문을 싣는다
            keep_text = scope and not a["deleted"] and (law != "조특법" or status in ("COMPLETED", "INTEGRATED"))
            linked_ids = sorted(set(linked_c.get(key, [])))
            articles.append(dict(
                id=f"{law}|{aid}", lawName=meta.get("full", law), lawShortName=law, articleId=aid,
                articleNo=a["articleNo"], branch=a["branch"] or 0, title=a["title"] or "",
                text=a["text"] if keep_text else "", part=a["part"] or "", chapter=a["chapter"] or "",
                deleted=a["deleted"], inScope=scope, status=status, statusLabel=STATUS_LABEL[status], note=None,
                examArea=(cmap[linked_ids[0]]["examArea"] if linked_ids else tax_common.LAW_AREA.get(law)) if scope else None,
                conceptIds=linked_ids, questionCount=len(cited_q.get(key, [])),
                lawVersion=meta["version"], effectiveDate=meta["effective"], sourceUrl=meta["url"],
                verifiedAt=meta.get("verified", lawdsl.VERIFIED_AT)))

    rows = []
    for name, law in GROUPS:
        arts = [a for a in articles if a["lawShortName"] == law]
        live = [a for a in arts if not a["deleted"] and a["inScope"]]
        cnt = {k: sum(1 for a in arts if a["status"] == k) for k in STATUS_LABEL}
        linked = cnt["COMPLETED"] + cnt["INTEGRATED"]
        rows.append(dict(group=name, law=law, total=len(arts), live=len(live), linked=linked,
                         rate=100.0 * linked / len(live) if live else 0.0,
                         concepts=sum(1 for c in tx_concepts if _LABEL.match(c["articleRefs"][0]).group(1) == law),
                         questions=sum(1 for x in active.values() if lawdsl.QUESTION_ARTICLES[x["id"]][0][0] == law), **cnt))

    area_q = {aid: sum(1 for x in active.values() if x["examArea"] == aid) for aid, *_ in tax_common.AREAS}
    area_c = {aid: sum(1 for c in tx_concepts if c["examArea"] == aid) for aid, *_ in tax_common.AREAS}
    exam_areas = []
    for aid, name, lo, hi, mock in tax_common.AREAS:
        if area_q[aid] < mock:
            errors.append(f"[TX] 실전 비중 모드 {name} 문항 {area_q[aid]}개 < 배정 {mock}")
        if not lo <= mock * 100 / 40 <= hi:
            errors.append(f"[TX] 실전 비중 모드 {name} 배정 {mock}/40 이 공고 비중 {lo}~{hi}% 를 벗어남")
        exam_areas.append(dict(id=aid, name=name, minPercent=lo, maxPercent=hi, mockQuestions=mock,
                               questionCount=area_q[aid], subjectId=SUBJECT))

    law_versions = [dict(lawName=m.get("full", law), lawShortName=law, lawVersion=m["version"], promulgationDate=m["promulgated"],
                         effectiveDate=m["effective"], sourceUrl=m["url"], verifiedAt=m.get("verified", lawdsl.VERIFIED_AT),
                         examYear=lawdsl.EXAM_YEAR)
                    for law, m in lawdsl.LAWS.items() if law in lawdsl.TAX_LAWS]

    lines = ["", "## 세법개론 조문 커버리지 (2026. 11. 24. 현행 법률 기준)", "",
             "| 법률 | 버전 | 조문 수(삭제 포함) | 범위 내 현행 | 학습 완료 | 개념 통합 | 작성 중 | 범위 밖 | 삭제 | 조문 연결률 | 개념 | 문항 |",
             "|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for r in rows:
        lines.append(f"| {r['group']} | {lawdsl.LAWS[r['law']]['version']} | {r['total']} | {r['live']} | {r['COMPLETED']} | {r['INTEGRATED']} | "
                     f"{r['WRITING']} | {r['OUT_OF_SCOPE']} | {r['DELETED']} | {r['rate']:.1f}% | {r['concepts']} | {r['questions']} |")
    lines += ["", "- 시행령·시행규칙: 원문 미확보 — 위임 수치(법정이자율·내용연수·간이과세 기준금액 등)는 숫자 문항으로 만들지 않았다.",
              "- 2027년 시험 법령 적용 기준일: 최종 시행계획 공고 전(미확정). 모든 세법 개념은 [현재 기준]으로 표시.",
              "", "## 세법개론 실전 비중(40문항)", "", "| 영역 | 공고 비중 | 모의 배정 | 개념 | 보유 문항 |", "|---|---|---|---|---|"]
    for e in exam_areas:
        lines.append(f"| {e['name']} | {e['minPercent']}~{e['maxPercent']}% | {e['mockQuestions']} | {area_c[e['id']]} | {e['questionCount']} |")
    return dict(articles=articles, examAreas=exam_areas, lawVersions=law_versions, reportLines=lines, rows=rows)

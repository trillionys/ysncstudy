"""기업법 조문 커버리지 검사기.

시험범위(금융위원회 공고 제2026-284호): 상법 제1편·제2편·제3편(어음·수표법, 보험편·해상편·항공운송편 제외),
공인회계사법, 주식회사 등의 외부감사에 관한 법률. 2027년 시험은 '법률'만 포함(시행령은 2028년부터).

조문 상태:
  COMPLETED      학습 완료            — 활성 문항의 근거 조문으로 인용됨
  INTEGRATED     관련 개념에 통합      — 개념의 근거 조문으로 연결(문항 직접 인용은 없음)
  WRITING        학습 콘텐츠 작성 중   — 시험범위 내인데 아직 개념·문항 연결 없음
  OUT_OF_SCOPE   시험범위 밖          — 상법 제4편~제6편
  DELETED        삭제 조문
  NOT_IN_FORCE   미시행              — (현재 데이터 없음: 미시행 개정 조문 원문을 별도 보관하지 않음)
  REVIEW_NEEDED  검토 필요            — 2027. 1. 1. 시행 개정이 예정되어 있으나 개정 조문 원문을 확보·대조하지 못한 조문
"""
import re

import dsl
import lawdsl

STATUS_LABEL = {
    "COMPLETED": "학습 완료", "INTEGRATED": "관련 개념에 통합", "WRITING": "학습 콘텐츠 작성 중",
    "OUT_OF_SCOPE": "시험범위 밖", "DELETED": "삭제 조문", "NOT_IN_FORCE": "미시행", "REVIEW_NEEDED": "검토 필요",
}

# 법률 제20991호(2025. 7. 22.) 부칙 및 제21448호 부칙 제1조에 따라 2027. 1. 1. 시행되는 개정 대상 조문.
# 개정 후 문언을 원문으로 확보·대조하지 못했으므로 현재(2026. 11. 23.) 시행 문언으로 학습하되 '검토 필요'로 표시한다.
PENDING_2027 = {
    ("상법", "제364조"): "2027. 1. 1. 시행 개정 예정(법률 제20991호) — 개정 후 문언 미대조",
    ("상법", "제368조"): "2027. 1. 1. 시행 개정 예정(법률 제20991호) — 개정 후 문언 미대조",
    ("상법", "제542조의14"): "2027. 1. 1. 시행 개정 예정(법률 제20991호·제21448호 부칙) — 개정 후 문언 미대조",
    ("상법", "제542조의15"): "2027. 1. 1. 시행 개정 예정(법률 제20991호) — 개정 후 문언 미대조",
    ("상법", "제542조의16"): "2027. 1. 1. 시행 개정 예정(법률 제21448호 부칙 제1조) — 개정 후 문언 미대조",
}

AREAS = [
    # id, 이름, 최소%, 최대%, 실전 모의 40문항 중 배정
    ("GENERAL", "상법 제1편 총칙·제2편 상행위", 10, 20, 6),
    ("COMPANY", "상법 제3편 회사", 55, 65, 24),
    ("CPA_ACT", "공인회계사법", 5, 15, 4),
    ("AUDIT_ACT", "주식회사 등의 외부감사에 관한 법률", 10, 20, 6),
]
GROUPS = [("상법총칙", "상법", "제1편"), ("상행위", "상법", "제2편"), ("회사법", "상법", "제3편"),
          ("공인회계사법", "공인회계사법", None), ("외부감사법", "외부감사법", None)]

_LABEL = re.compile(r"(상법|공인회계사법|외부감사법) (제\d+조(?:의\d+)?)")


def area_of(law, aid):
    if law == "공인회계사법":
        return "CPA_ACT"
    if law == "외부감사법":
        return "AUDIT_ACT"
    part = lawdsl.ARTICLES["상법"][aid]["part"] or ""
    return "COMPANY" if part.startswith("제3편") else "GENERAL"


def in_scope(law, a):
    if law != "상법":
        return True
    return (a["part"] or "").startswith(("제1편", "제2편", "제3편"))


def build(concepts, questions, errors, warnings):
    law_concepts = [c for c in concepts if c["subjectId"] == lawdsl.SUBJECT]
    active = {x["id"]: x for x in questions if x["isActive"] and x["subjectId"] == lawdsl.SUBJECT}

    cited_q = {}      # (law, aid) -> [questionId]
    for qid, refs in lawdsl.QUESTION_ARTICLES.items():
        if qid not in active:
            continue
        for law, aid, _ in refs:
            cited_q.setdefault((law, aid), []).append(qid)
    linked_c = {}     # (law, aid) -> [conceptId]
    for c in law_concepts:
        for label in c["articleRefs"]:
            m = _LABEL.match(label)
            linked_c.setdefault((m.group(1), m.group(2)), []).append(c["id"])

    # 개념·문항의 시험 영역
    for c in law_concepts:
        m = _LABEL.match(c["articleRefs"][0])
        c["examArea"] = area_of(m.group(1), m.group(2))
    for qid, x in active.items():
        law, aid, _ = lawdsl.QUESTION_ARTICLES[qid][0]
        x["examArea"] = area_of(law, aid)
    for x in questions:
        x.setdefault("examArea", None)
        if x["subjectId"] == lawdsl.SUBJECT and x["id"] not in active:
            law, aid, _ = lawdsl.QUESTION_ARTICLES[x["id"]][0]
            x["examArea"] = area_of(law, aid)
    for c in concepts:
        c.setdefault("examArea", None)

    articles = []
    for law, table in lawdsl.ARTICLES.items():
        meta = lawdsl.LAWS[law]
        for aid, a in table.items():
            key = (law, aid)
            scope = in_scope(law, a)
            if a["deleted"]:
                status = "DELETED"
            elif not scope:
                status = "OUT_OF_SCOPE"
            elif key in PENDING_2027:
                status = "REVIEW_NEEDED"
            elif key in cited_q:
                status = "COMPLETED"
            elif key in linked_c:
                status = "INTEGRATED"
            else:
                status = "WRITING"
            if not scope and (key in cited_q or key in linked_c):
                errors.append(f"[20] 시험범위 밖 조문을 근거로 한 학습 콘텐츠: {law} {aid}")
            articles.append(dict(
                id=f"{law}|{aid}", lawName=meta.get("full", law), lawShortName=law, articleId=aid,
                articleNo=a["articleNo"], branch=a["branch"] or 0, title=a["title"] or "",
                text=a["text"] if scope and not a["deleted"] else "",
                part=a["part"] or "", chapter=a["chapter"] or "", deleted=a["deleted"], inScope=scope,
                status=status, statusLabel=STATUS_LABEL[status], note=PENDING_2027.get(key),
                examArea=area_of(law, aid) if scope else None,
                conceptIds=sorted(set(linked_c.get(key, []))), questionCount=len(cited_q.get(key, [])),
                lawVersion=meta["version"], effectiveDate=meta["effective"], sourceUrl=meta["url"],
                verifiedAt=lawdsl.VERIFIED_AT))

    # 커버리지 리포트
    rows = []
    for name, law, part in GROUPS:
        arts = [a for a in articles if a["lawShortName"] == law and (part is None or a["part"].startswith(part))]
        live = [a for a in arts if not a["deleted"]]
        cnt = {k: sum(1 for a in arts if a["status"] == k) for k in STATUS_LABEL}
        linked = cnt["COMPLETED"] + cnt["INTEGRATED"] + sum(1 for a in arts if a["status"] == "REVIEW_NEEDED" and (a["conceptIds"] or a["questionCount"]))
        rate = 100.0 * linked / len(live) if live else 0.0
        rows.append(dict(group=name, total=len(arts), live=len(live), linked=linked, rate=rate, **cnt))
    out_of_scope = [a for a in articles if a["status"] == "OUT_OF_SCOPE"]
    review = [a for a in articles if a["status"] == "REVIEW_NEEDED"]
    writing = [a for a in articles if a["status"] == "WRITING"]

    # 실전 비중 모드 구성 가능 여부
    area_q = {aid: sum(1 for x in active.values() if x["examArea"] == aid) for aid, *_ in AREAS}
    exam_areas = []
    for aid, name, lo, hi, mock in AREAS:
        if area_q[aid] < mock:
            errors.append(f"[20] 실전 비중 모드 {name} 문항 {area_q[aid]}개 < 배정 {mock}")
        if not lo <= mock * 100 / 40 <= hi:
            errors.append(f"[20] 실전 비중 모드 {name} 배정 {mock}/40 이 공고 비중 {lo}~{hi}% 를 벗어남")
        exam_areas.append(dict(id=aid, name=name, minPercent=lo, maxPercent=hi, mockQuestions=mock,
                               questionCount=area_q[aid], subjectId=lawdsl.SUBJECT))

    law_versions = [dict(lawName=m.get("full", law), lawShortName=law, lawVersion=m["version"],
                         promulgationDate=m["promulgated"], effectiveDate=m["effective"], sourceUrl=m["url"],
                         verifiedAt=lawdsl.VERIFIED_AT, examYear=lawdsl.EXAM_YEAR)
                    for law, m in lawdsl.LAWS.items()]

    lines = ["", "## 기업법 조문 커버리지", "",
             "| 구분 | 조문 수(삭제 포함) | 현행 조문 | 학습 완료 | 관련 개념에 통합 | 작성 중 | 검토 필요 | 시험범위 밖 | 삭제 | 개념 연결률 |",
             "|---|---|---|---|---|---|---|---|---|---|"]
    for r in rows:
        lines.append(f"| {r['group']} | {r['total']} | {r['live']} | {r['COMPLETED']} | {r['INTEGRATED']} | {r['WRITING']} | "
                     f"{r['REVIEW_NEEDED']} | {r['OUT_OF_SCOPE']} | {r['DELETED']} | {r['rate']:.1f}% |")
    lines += ["", f"- 시험범위 밖 조문(상법 제4편 보험·제5편 해상·제6편 항공운송): {len(out_of_scope)}개", ""]
    lines += ["### 검토 필요 조문", ""] + [f"- {a['lawShortName']} {a['articleId']}({a['title']}): {a['note']}" for a in review]
    lines += ["", "### 학습 콘텐츠 작성 중(개념·문항 미연결) 조문", ""]
    for name, law, part in GROUPS:
        ws = [a for a in writing if a["lawShortName"] == law and (part is None or a["part"].startswith(part))]
        lines.append(f"- {name} ({len(ws)}개): " + (", ".join(f"{a['articleId']}" for a in ws) or "없음"))
    lines += ["", "## 기업법 실전 비중(40문항)", "", "| 영역 | 공고 비중 | 모의 배정 | 보유 문항 |", "|---|---|---|---|"]
    for e in exam_areas:
        lines.append(f"| {e['name']} | {e['minPercent']}~{e['maxPercent']}% | {e['mockQuestions']} | {e['questionCount']} |")
    return dict(articles=articles, examAreas=exam_areas, lawVersions=law_versions, reportLines=lines, rows=rows)

#!/usr/bin/env python3
"""국가법령정보센터(law.go.kr) 법령 본문 HTML(lsInfoR.do 응답)을 조문 단위 JSON 으로 변환한다.

사용법: python3 tools/law/parse_law.py <html> <out.json> --name 상법 --law-no 21044 --promulgated 2025-09-09 --effective 2026-09-10 --url <sourceUrl>

출력: {lawName, lawNo, promulgationDate, effectiveDate, sourceUrl, fetchedAt, articles:[{articleId, articleNo, branch, title,
        part, chapter, section, subsection, text, deleted, amendmentNotes}], addenda:[...]}
"""
import argparse
import datetime
import html
import json
import re
import sys

HEAD_RE = re.compile(r"^제(\d+)(편|장|절|관)\s*(.*)$")
ART_RE = re.compile(r"^제(\d+)조(?:의(\d+))?\s*\(([^)]*)\)")


def _alt(m):
    """표·수식 이미지의 대체텍스트(세율표 등)를 본문으로 살린다."""
    a = html.unescape(m.group(1))
    a = a.replace("@@LATEX@@", " ").replace("@@/LATEX@@", " ").replace("\\left(", "(").replace("\\right)", ")")
    return " " + a + " "


def clean(fragment):
    t = re.sub(r'<img[^>]*alt="([^"]*)"[^>]*>', _alt, fragment)
    t = re.sub(r"<[^>]+>", " ", t)
    t = html.unescape(t)
    return re.sub(r"\s+", " ", t).strip()


def parse(src):
    body = src
    # 부칙 시작 위치(본문과 분리)
    add_idx = body.find('id="arDivArea"')
    main = body if add_idx < 0 else body[:add_idx]
    addenda_html = "" if add_idx < 0 else body[add_idx:]
    blocks = re.split(r'<div class="pgroup"', main)
    cur = {"편": None, "장": None, "절": None, "관": None}
    arts = []
    for b in blocks[1:]:
        gt = re.search(r'<p class="gtit"[^>]*>(.*?)</p>', b, re.S)
        if gt:
            h = clean(gt.group(1))
            m = HEAD_RE.match(h)
            if m:
                level = m.group(2)
                cur[level] = h
                order = ["편", "장", "절", "관"]
                for lv in order[order.index(level) + 1:]:
                    cur[lv] = None
            continue
        lc = re.search(r'<div class="lawcon">(.*)', b, re.S)
        if not lc:
            continue
        text = clean(lc.group(1))
        m = ART_RE.match(text)
        if not m:
            # 삭제 조문: "제12조 삭제 <...>"
            md = re.match(r"^제(\d+)조(?:의(\d+))?\s*(삭제.*)$", text)
            if md:
                no, br = int(md.group(1)), int(md.group(2) or 0)
                arts.append(dict(articleNo=no, branch=br, title="", text=md.group(3), deleted=True))
                arts[-1].update(part=cur["편"], chapter=cur["장"], section=cur["절"], subsection=cur["관"])
            continue
        no, br, title = int(m.group(1)), int(m.group(2) or 0), m.group(3).strip()
        rest = text[m.end():].strip()
        notes = re.findall(r"<(?:개정|신설|전문개정|본조신설|제목개정|종전)[^>]*>", text)
        arts.append(dict(articleNo=no, branch=br, title=title, text=rest, deleted=rest.startswith("삭제") and len(rest) < 40,
                         part=cur["편"], chapter=cur["장"], section=cur["절"], subsection=cur["관"], amendmentNotes=notes))
    for a in arts:
        a["articleId"] = f"제{a['articleNo']}조" + (f"의{a['branch']}" if a["branch"] else "")
    addenda = []
    for part in re.split(r'<div class="pgroup"', addenda_html)[1:]:
        t = clean(part)
        if t:
            addenda.append(t)
    return arts, addenda


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("html"); ap.add_argument("out")
    ap.add_argument("--name", required=True); ap.add_argument("--law-no", required=True)
    ap.add_argument("--promulgated", required=True); ap.add_argument("--effective", required=True)
    ap.add_argument("--url", required=True)
    a = ap.parse_args()
    src = open(a.html, encoding="utf-8").read()
    arts, addenda = parse(src)
    data = dict(lawName=a.name, lawNo=a.law_no, promulgationDate=a.promulgated, effectiveDate=a.effective,
                sourceUrl=a.url, fetchedAt=datetime.date.today().isoformat(), articleCount=len(arts),
                articles=arts, addenda=addenda)
    json.dump(data, open(a.out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(f"{a.name}: 조문 {len(arts)}개 (삭제 {sum(x['deleted'] for x in arts)}), 부칙 블록 {len(addenda)}개")


if __name__ == "__main__":
    sys.exit(main())

"""지정 학습범위 체크리스트.

scope_bullets.txt 는 사용자가 지정한 재고자산·유형자산·차입원가·농림어업 세부항목 목록(원문 목록 그대로)이다.
각 항목이 실제 학습 콘텐츠(개념 설명 또는 문항)에 반영되었는지 키워드로 확인하고,
가장 관련 있는 개념 ID 에 연결한다. 표현이 달라 기계적으로 찾을 수 없는 항목은 ALIASES 에
콘텐츠에서 사용한 표현을 '/' 로 등록한다(대안 중 하나라도 있으면 반영된 것으로 본다).
"""
import os
import re
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
AREA_CHAPTER = {"A": "FA-L", "B": "FA-M", "C": "FA-N", "D": "FA-P", "E": "FA-Q", "농": "FA-R"}

# 항목명 → 콘텐츠 검색 표현. '/' = 대안(OR), '+' = 같은 개념 안에 함께 있어야 하는 표현(AND)
ALIASES = {
    "생산에 사용되는 재료와 소모품": "원재료+소모품",
    "매입채무와 재고자산 취득원가": "매입채무+순매입",
    "선급금 및 선급비용과 재고자산의 관계": "선급금+선급비용",
    "비정상적인 지출": "비정상+낭비",
    "판매비와 일반관리비": "판매원가/판매비+관리",
    "운송조건과 위험·통제 이전의 관계": "인도조건+통제",
    "매입상품의 기말 귀속": "미착품+매입",
    "매출상품의 기말 귀속": "도착지+판매자",
    "고객의 검수 또는 승인 조건": "검수",
    "매출 인식과 재고자산 제거의 관계": "매출원가+제거",
    "고객에게 인도되지 않은 상품": "미인도",
    "이미 매출 처리한 미인도상품": "미인도+매출",
    "거래의 기간귀속 오류": "기간귀속+오류/누락매입",
    "실사수량과 재무제표 수량의 차이": "실사수량+장부",
    "기초재고자산": "기초재고",
    "당기매입액": "당기매입/순매입",
    "매입시점별 원가층": "원가층",
    "매출시점별 원가층": "이동평균+판매",
    "원가흐름 가정과 실제 물품흐름의 차이": "원가흐름+물량흐름",
    "후입선출법 LIFO와 K-IFRS상 허용 여부": "후입선출",
    "원가계산방법의 일관성": "일관성+원가",
    "오류의 상쇄효과": "상쇄",
    "오류의 자동반전과 그 한계": "자동조정/자동",
    "두 회계기간 누적손익 영향": "두 기간/2개 기간/누적",
    "장부수량과 실사수량 차이": "장부수량+실사수량/장부수량+실제수량",
    "비정상감모손실의 별도 비용처리": "비정상감모+기타비용/비정상감모+별도",
    "실사재고액 산정": "실제수량+단가/실사수량+원가",
    "추가 가공비": "완성원가/추가원가",
    "계약판매가격": "확정판매계약/계약가격",
    "유사한 품목의 집단평가": "집단",
    "예상가격 하락": "가격+하락",
    "평가충당금 사용 회계처리": "평가충당금+판매",
    "평가손실의 환입과 신규 평가이익의 차이": "환입+원가",
    "평가손실과 감모손실이 동시에 발생한 경우": "감모+평가+동시",
    "취득단가": "단위원가/취득원가+단가",
    "매출수량": "판매수량/판매량",
    "두 이익률의 변환": "이익률+원가 기준",
    "기간별 매출총이익률 변동": "매출총이익률+변동/매출총이익률+한계",
    "과거 매출총이익률 적용 시 유의사항": "과거+매출총이익률",
    "소매재고법이 필요한 상황": "백화점/대형마트/소매",
    "원가 대비 매가": "원가율",
    "재고자산의 소매가액": "매가",
    "소매가액에서 원가로의 환산": "매가+원가율",
    "가격인상취소": "인상취소",
    "가격인하취소": "인하취소",
    "순가격인상": "순인상",
    "순가격인하": "순인하",
    "원가율 분모에서의 가격변동 처리": "분모+인하",
    "소매재고법에서 원가근사": "원가+근사/원가에 근사",
    "기존 원가층과 당기매입 원가층 구별": "선입선출+기초재고",
    "저가기준 소매재고법의 논리": "저가기준+인하",
    "판매가격 변경": "가격인상/가격인하",
    "표시가격과 실제판매가격": "종업원할인",
    "선입선출 소매재고법에서의 기초재고": "선입선출+기초재고",
    "가중평균 소매재고법에서의 기초재고": "평균원가+기초재고",
    "매가를 통한 재고수량 추정": "기말재고 매가/매가+추정",
    "원가율에 포함하는 항목과 제외하는 항목": "원가율+포함",
    "부산물 회계처리의 개념": "부산물",
    "관리목적 자산": "관리활동/관리 목적/관리목적",
    "유형자산과 무형자산의 차이": "무형자산+물리적",
    "일괄취득가격의 배분": "일괄취득+배분",
    "상대적 공정가치에 의한 배분": "공정가치+비율/상대적 공정가치",
}


def _norm(s):
    s = unicodedata.normalize("NFC", s or "").lower()
    return re.sub(r"[\s·・,.()\[\]'\"‘’“”/\-:~]", "", s)


def load_bullets():
    area = None
    out = []
    for line in open(os.path.join(HERE, "scope_bullets.txt"), encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip():
            continue
        if line.startswith("## "):
            area = line[3:].strip()
            continue
        out.append((area, line.strip()))
    return out


def _keywords(item):
    if item in ALIASES:
        return ALIASES[item].split("/")
    m = re.match(r"^(유형 [A-Q]):", item)
    if m:
        return ["[" + m.group(1)]
    kws = [item]
    # '선입선출법 FIFO' 처럼 한글+영문 병기 → 한글 부분, 영문 부분 각각 허용
    ko = re.sub(r"[A-Za-z][A-Za-z\- ]*", "", item).strip()
    if ko and ko != item:
        kws.append(ko)
    return kws


STOP = {"미치는", "대한", "및", "있는", "하는", "위한", "등", "그", "따른", "경우", "관련", "에서", "시점"}
PARTICLE = re.compile(r"(으로|에서|에게|에는|과의|와의|이란|으로의|로의|의|과|와|을|를|은|는|이|가|에|로|도)$")


def _tokens(item):
    """항목명을 핵심어로 분해한다(조사 제거). 모든 핵심어가 같은 개념 콘텐츠에 있으면 반영된 것으로 본다."""
    toks = []
    for w in re.split(r"[\s·,/()]+", item):
        w = w.strip()
        if not w or w in STOP:
            continue
        w2 = PARTICLE.sub("", w) if len(w) > 2 else w
        if w2 and w2 not in STOP:
            toks.append(_norm(w2))
    return [t for t in toks if t]


def build(concepts, questions):
    """반환: (items, misses). items 는 앱으로 내보낼 체크리스트."""
    by_concept = {}
    for c in concepts:
        text = " ".join(str(c.get(k) or "") for k in (
            "koreanName", "englishName", "definition", "whyItMatters", "detailedExplanation", "comparison",
            "example", "formula", "examTips", "commonMistakes", "journalEntries", "revisionNote"))
        by_concept[c["id"]] = [c["chapterId"], text]
    for qq in questions:
        extra = " ".join([qq["questionText"], qq["blankAnswer"], qq.get("explanation") or "",
                          qq.get("calculation") or "", " ".join(qq.get("choices") or [])])
        for cid in qq["conceptIds"]:
            if cid in by_concept:
                by_concept[cid][1] += " " + extra
    normed = {cid: (ch, _norm(t)) for cid, (ch, t) in by_concept.items()}
    items, misses = [], []
    for i, (area, item) in enumerate(load_bullets()):
        chap = AREA_CHAPTER.get(area[0])
        alts = [[_norm(p) for p in k.split("+")] for k in _keywords(item)]
        toks = [] if item in ALIASES or item.startswith("유형 ") else _tokens(item)
        best = None
        best_score = 0
        for cid, (ch, t) in normed.items():
            hit = sum(min(t.count(p) for p in ps) for ps in alts if ps and all(ps))
            if not hit and toks and all(tk in t for tk in toks):
                hit = 1
            if not hit:
                continue
            score = hit + (1000 if ch == chap else 0) + (500 if _norm(item) in _norm(cid) else 0)
            if score > best_score:
                best, best_score = cid, score
        if best is None:
            misses.append((area, item))
        items.append({"id": f"SC{i + 1:04d}", "area": area, "item": item, "conceptId": best,
                      "covered": best is not None, "sortOrder": i + 1})
    return items, misses

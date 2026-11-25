#!/usr/bin/env python3
"""Google Play 스토어 그래픽 생성·검증.

입력
  - 실제 앱 화면 스크린샷: app/build/reports/content/store/{phone,tablet7,tablet10}/*.png
    (StoreScreenshotTest 로 생성: ./gradlew testDebugUnitTest --tests '*StoreScreenshotTest*')
  - 글꼴: Noto Sans KR(SIL OFL 1.1) — 인자 --font 로 경로 지정
출력: play-store/graphics/
  icon/app-icon-512.png              512×512, 32비트 PNG(풀블리드 정사각형, 모서리·그림자 없음 — Play 가 마스크 적용)
  feature/feature-graphic-1024x500.png  1024×500, 24비트 PNG(알파 없음)
  screenshots/{phone,tablet7,tablet10}/raw/      실제 화면 그대로
  screenshots/{phone,tablet7,tablet10}/captioned/ 상단 설명 문구 + 실제 화면(같은 해상도)
규격 검증(Play Console, 2026-11 기준): 아이콘 512×512·1MB 이하, 피처 그래픽 1024×500·알파 없음·15MB 이하,
스크린샷 PNG/JPEG·한 변 320~3840px·긴 변 ≤ 짧은 변 × 2·8MB 이하, 휴대전화 2~8장.
"""
import argparse
import os
import shutil
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "app", "build", "reports", "content", "store")
OUT = os.path.join(ROOT, "play-store", "graphics")

NAVY = (11, 31, 58)          # 런처 아이콘 배경 #0B1F3A
NAVY2 = (20, 52, 96)
BLUE = (59, 130, 246)        # #3B82F6
PALE = (220, 230, 245)       # #DCE6F5
WHITE = (255, 255, 255)

CAPTIONS = {
    "01_home": ("5개 시험과목 학습현황을 한눈에", "회계학 · 경영학 · 경제원론 · 기업법 · 세법개론"),
    "02_subject_tax": ("세법개론 전 범위 학습", "현행 법률 조문 대조 · 세율표 · 단원별 진도"),
    "03_concept_summary": ("핵심만 빠르게, 간단 요약", "정의 · 핵심 원리 · 공식 · 시험 포인트"),
    "04_concept_detail": ("이해를 돕는 상세 학습", "계산 예제 · 분개 · 출제 포인트 · 함정"),
    "05_quiz": ("초성 힌트 빈칸으로 기본 다지기", "기본(초성 힌트) · 심화(힌트 없음) 모드"),
    "06_feedback": ("바로 확인하는 정답과 해설", "관련 개념 바로가기 · 콘텐츠 오류 신고"),
    "07_calc_ink": ("계산기와 투명 필기", "문제 위에 밑줄 치며 풀기"),
    "08_review_settings": ("나에게 맞는 복습 주기", "집중 · 표준 · 여유 · 복습 날짜 미리보기"),
}


def font(path, size, weight):
    f = ImageFont.truetype(path, size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


# ───────────────────────── 아이콘 ─────────────────────────
def draw_book(d, s, ox, oy):
    """런처 아이콘 벡터(108 viewport)의 책·체크 표시를 같은 좌표로 그린다. s=배율, (ox,oy)=viewport 원점 위치."""
    P = lambda pts: [(ox + x * s, oy + y * s) for x, y in pts]
    d.polygon(P([(30, 38), (52, 42), (52, 74), (30, 70)]), fill=WHITE)
    d.polygon(P([(56, 42), (78, 38), (78, 70), (56, 74)]), fill=PALE)
    w = int(4 * s)
    pts = P([(60, 57), (65, 62), (74, 50)])
    d.line(pts, fill=BLUE, width=w, joint="curve")
    for x, y in (pts[0], pts[-1]):   # 둥근 선 끝(strokeLineCap=round)
        d.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=BLUE)


def make_icon(path):
    S = 4                                   # 4배 슈퍼샘플링 후 축소(안티앨리어싱)
    size = 512 * S
    im = Image.new("RGBA", (size, size), NAVY + (255,))
    d = ImageDraw.Draw(im)
    # viewport 18~90(72 단위)을 512px 에 대응: 런처의 보이는 영역(안전 영역 포함)과 같은 비율
    s = size / 72
    draw_book(d, s, -18 * s, -20 * s)
    im = im.resize((512, 512), Image.LANCZOS)
    im.save(path, optimize=True)


# ───────────────────────── 피처 그래픽 ─────────────────────────
def gradient(w, h, c1, c2):
    g = Image.new("RGB", (w, h), c1)
    px = g.load()
    for x in range(w):
        t = x / (w - 1)
        col = tuple(int(c1[i] * (1 - t) + c2[i] * t) for i in range(3))
        for y in range(h):
            px[x, y] = col
    return g


def make_feature(path, fpath):
    W, H = 1024, 500
    S = 2
    im = gradient(W * S, H * S, NAVY, NAVY2)
    d = ImageDraw.Draw(im)
    # 왼쪽: 아이콘 모티프(책 + 체크)
    s = 5.2 * S
    draw_book(d, s, 205 * S - 54 * s, 250 * S - 56 * s)
    # 오른쪽: 이름·설명(가장자리에서 충분히 띄움)
    x = 420 * S
    d.text((x, 118 * S), "CPA Concept Lab", font=font(fpath, 62 * S, 800), fill=WHITE)
    d.text((x, 205 * S), "공인회계사 1차 개념 · 문제 학습", font=font(fpath, 34 * S, 600), fill=PALE)
    chips = ["회계학", "경영학", "경제원론", "기업법", "세법개론"]
    cx, cy = x, 285 * S
    cf = font(fpath, 22 * S, 600)
    for c in chips:
        tw = d.textlength(c, font=cf)
        d.rounded_rectangle([cx, cy, cx + tw + 32 * S, cy + 44 * S], radius=22 * S, fill=None, outline=PALE, width=2 * S)
        d.text((cx + 16 * S, cy + 7 * S), c, font=cf, fill=WHITE)
        cx += tw + 44 * S
    d.text((x, 366 * S), "무료 · 오프라인 학습 · 광고 없음", font=font(fpath, 24 * S, 500), fill=(170, 190, 220))
    im = im.resize((W, H), Image.LANCZOS).convert("RGB")
    im.save(path, optimize=True)


# ───────────────────────── 설명 문구 스크린샷 ─────────────────────────
def captioned(src, dst, title, sub, fpath):
    shot = Image.open(src).convert("RGB")
    W, H = shot.size
    top = int(H * 0.17)                    # 상단 문구 영역
    canvas = gradient(W, H, NAVY, NAVY2)
    d = ImageDraw.Draw(canvas)
    tf = font(fpath, int(W * 0.064), 800)
    sf = font(fpath, int(W * 0.034), 500)
    tw = d.textlength(title, font=tf)
    d.text(((W - tw) / 2, top * 0.30), title, font=tf, fill=WHITE)
    sw = d.textlength(sub, font=sf)
    d.text(((W - sw) / 2, top * 0.30 + W * 0.095), sub, font=sf, fill=PALE)
    # 실제 화면을 축소해 아래에 배치(둥근 모서리 + 그림자)
    avail_h = H - top - int(H * 0.025)
    scale = min(avail_h / H, (W * 0.86) / W)
    sw_, sh_ = int(W * scale), int(H * scale)
    small = shot.resize((sw_, sh_), Image.LANCZOS)
    r = int(W * 0.04)
    mask = Image.new("L", (sw_, sh_), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw_ - 1, sh_ - 1], radius=r, fill=255)
    x, y = (W - sw_) // 2, top
    shadow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(shadow).rounded_rectangle([x + 6, y + 10, x + sw_ + 6, y + sh_ + 10], radius=r, fill=120)
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    canvas.paste((0, 0, 0), (0, 0), shadow)
    canvas.paste(small, (x, y), mask)
    canvas.save(dst, optimize=True)


# ───────────────────────── 검증 ─────────────────────────
def check(path, kind):
    im = Image.open(path)
    w, h = im.size
    size = os.path.getsize(path)
    errs = []
    if kind == "icon":
        if (w, h) != (512, 512): errs.append("512×512 아님")
        if size > 1024 * 1024: errs.append("1MB 초과")
        if im.format != "PNG": errs.append("PNG 아님")
    elif kind == "feature":
        if (w, h) != (1024, 500): errs.append("1024×500 아님")
        if im.mode not in ("RGB",): errs.append(f"알파 포함({im.mode})")
        if size > 15 * 1024 * 1024: errs.append("15MB 초과")
    else:
        if not (320 <= min(w, h) and max(w, h) <= 3840): errs.append("한 변 320~3840px 벗어남")
        if max(w, h) > 2 * min(w, h): errs.append("긴 변이 짧은 변의 2배 초과")
        if size > 8 * 1024 * 1024: errs.append("8MB 초과")
        if im.format not in ("PNG", "JPEG"): errs.append("PNG/JPEG 아님")
        if im.mode != "RGB": errs.append(f"24비트 아님({im.mode})")
    return f"{os.path.relpath(path, ROOT)}  {w}×{h}  {im.mode}  {size // 1024}KB  " + ("OK" if not errs else "✗ " + ", ".join(errs)), not errs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--font", required=True)
    a = ap.parse_args()
    for sub in ("icon", "feature"):
        os.makedirs(os.path.join(OUT, sub), exist_ok=True)
    results = []
    icon = os.path.join(OUT, "icon", "app-icon-512.png")
    make_icon(icon)
    results.append(check(icon, "icon"))
    feat = os.path.join(OUT, "feature", "feature-graphic-1024x500.png")
    make_feature(feat, a.font)
    results.append(check(feat, "feature"))
    for dev in ("phone", "tablet7", "tablet10"):
        src_dir = os.path.join(SRC, dev)
        if not os.path.isdir(src_dir):
            print(f"스크린샷 없음: {src_dir} — StoreScreenshotTest 를 먼저 실행", file=sys.stderr)
            sys.exit(1)
        raw = os.path.join(OUT, "screenshots", dev, "raw")
        cap = os.path.join(OUT, "screenshots", dev, "captioned")
        shutil.rmtree(raw, ignore_errors=True); shutil.rmtree(cap, ignore_errors=True)
        os.makedirs(raw); os.makedirs(cap)
        names = sorted(f for f in os.listdir(src_dir) if f.endswith(".png"))
        # 같은 화면이 두 번 찍힌 경우(태블릿은 스크롤 없이 예제가 보여 03·04 가 같다) 중복 제거
        seen, uniq = set(), []
        for n in names:
            h = Image.open(os.path.join(src_dir, n)).tobytes()
            if h in seen:
                continue
            seen.add(h); uniq.append(n)
        names = uniq
        for n in names:
            key = n[:-4]
            Image.open(os.path.join(src_dir, n)).convert("RGB").save(os.path.join(raw, n), optimize=True)   # 24비트(알파 없음)
            results.append(check(os.path.join(raw, n), "shot"))
            t, s = CAPTIONS[key]
            captioned(os.path.join(src_dir, n), os.path.join(cap, n), t, s, a.font)
            results.append(check(os.path.join(cap, n), "shot"))
        if dev == "phone" and not (2 <= len(names) <= 8):
            results.append((f"휴대전화 스크린샷 {len(names)}장(2~8장 필요)", False))
    for line, ok in results:
        print(line)
    bad = [r for r in results if not r[1]]
    print(f"\n검증: {len(results) - len(bad)}/{len(results)} 통과")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()

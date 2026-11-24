"""원본 문제 출처 레지스트리 (NEW-INV-001 ~ NEW-INV-042).

원본 이미지 파일은 작업 환경에 없어 이 레지스트리 작성자가 직접 판독하지 않았다(readingConfidence NONE).
사용자가 2026-11-24 에 '01~42번 이미지의 내용은 기존 APK(콘텐츠 v3)에 모두 반영되어 있다'고 확인했다.
따라서 각 출처를 해당 단원 전체 개념과 연결하고 상태를 USER_CONFIRMED_COVERED 로 둔다.
문항별 세부 논점·수치는 판독하지 않았으므로 기록하지 않으며, VERIFIED(원본 대조 완료)로 표시하지 않는다.
- 01~37: 재고자산(대단원 L 매입·기말조정, M 감모·평가손실, N 특수한 원가배분)
- 38~42: 농림어업(대단원 R)
"""
from dsl import source

NOTE = "사용자 확인(2026-11-24): 원본 01~42 내용이 기존 콘텐츠에 반영됨. 앱 작성자는 원본 이미지를 직접 판독하지 않음."

for n in range(1, 43):
    sid = f"NEW-INV-{n:03d}"
    agri = n >= 38
    source(
        sid,
        sourceFile=None,
        sourcePage=None,
        originalNumber=f"{n:02d}",
        topic="농림어업(사용자 확인: 기존 콘텐츠에 반영)" if agri else "재고자산(사용자 확인: 기존 콘텐츠에 반영)",
        conceptIds=[],          # build 단계에서 연결 단원 개념으로 채운다
        linkChapterId="FA-R" if agri else "FA-L FA-M FA-N",
        formula=None,
        solutionSteps=None,
        keyTrap=None,
        readingConfidence="NONE",
        verificationStatus="USER_CONFIRMED_COVERED",
        reviewRequired=False,
        note=NOTE,
    )

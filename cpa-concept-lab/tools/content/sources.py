"""원본 문제 출처 레지스트리 (NEW-INV-001 ~ NEW-INV-042).

작업 시점에 프로젝트 저장소와 작업 환경에서 source_images 디렉터리나 압축파일을 찾을 수 없었다.
따라서 원본 이미지를 판독하지 않았으며, 문제별 주제·정답·수치를 추측하지 않는다.
- 01~37: 재고자산 문제라는 사용자 설명만 있으므로 세부 주제·연결 개념을 비워 두고 SOURCE_NOT_PROVIDED 로 둔다.
- 38~42: 사용자가 '농림어업 관련 학습논점'이라고 밝힌 문항이므로 농림어업(R) 단원 전체와 연결하되,
  문항별 세부 논점은 원본 확인 전까지 특정하지 않는다(readingConfidence NONE).
원본 이미지가 제공되면 이 파일의 topic·conceptIds·formula·steps·trap 을 채우고 verificationStatus 를 갱신한다.
"""
from dsl import source

NOTE = "원본 이미지 미제공 — 판독하지 않음. 원본 확인 후 세부 논점·연결 개념 등록 필요."

for n in range(1, 43):
    sid = f"NEW-INV-{n:03d}"
    agri = n >= 38
    source(
        sid,
        sourceFile=None,
        sourcePage=None,
        originalNumber=f"{n:02d}",
        topic="농림어업(사용자 설명 기준, 세부 논점 미확인)" if agri else "재고자산(사용자 설명 기준, 세부 논점 미확인)",
        conceptIds=[],          # build 단계에서 38~42 는 R 단원 개념으로 채운다
        linkChapterId="FA-R" if agri else None,
        formula=None,
        solutionSteps=None,
        keyTrap=None,
        readingConfidence="NONE",
        verificationStatus="SOURCE_NOT_PROVIDED",
        reviewRequired=True,
        note=NOTE,
    )

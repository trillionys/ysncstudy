# CPA Concept Lab

CPA 재무회계 개념을 **개념 학습 → 초성 힌트 빈칸(기본) → 무힌트 빈칸(심화) → 오답·간격 복습** 순서로 장기기억에 남기기 위한 네이티브 Android 앱입니다. 모든 기능은 오프라인에서 동작합니다.

- 패키지: `com.cpaconceptlab.study` · 버전 1.0.1 (versionCode 2) · 콘텐츠 v2 · minSdk 26(Android 8.0) / targetSdk 35
- 기술: Kotlin 2.0, Jetpack Compose + Material 3, MVVM, Room(스키마 v2, 명시적 마이그레이션), Coroutines/Flow, DataStore, Navigation Compose, kotlinx.serialization, Storage Access Framework
- 설치 파일: [`dist/CPAConceptLab-1.0.1-debug.apk`](dist/CPAConceptLab-1.0.1-debug.apk) (디버그 서명)

## 1. 설치

1. 휴대폰에서 이 저장소의 `cpa-concept-lab/dist/CPAConceptLab-1.0.1-debug.apk`를 내려받습니다.
2. 파일을 열고 "출처를 알 수 없는 앱 설치"를 허용한 뒤 설치합니다.
3. 첫 실행 때 앱에 들어 있는 콘텐츠(개념 285개, 문항 600개)가 데이터베이스에 등록됩니다.

> 새 버전으로 업데이트할 때는 **앱을 삭제하지 말고** 새 APK를 그 위에 설치하세요. 그래야 학습기록이 유지됩니다. 자세한 내용은 [UPDATE_GUIDE.md](UPDATE_GUIDE.md)를 참고하세요.

## 2. 수록 콘텐츠(v2)

| 대단원 | 개념 | 문항 |
|---|---|---|
| A. 회계정보의 경제적 기능 | 13 | 28 |
| B. 회계기준과 국제회계기준 | 19 | 39 |
| C. 일반목적재무보고 | 24 | 51 |
| D. 재무정보의 질적특성 | 36 | 79 |
| E. 재무제표 요소 | 21 | 46 |
| F. 인식과 제거 | 20 | 41 |
| G. 자본과 자본유지 | 19 | 43 |
| H. 재무제표 요소의 측정 | 30 | 61 |
| I. 재무제표의 표시와 공시 | 45 | 92 |
| J. 유동·비유동 분류 | 26 | 52 |
| K. K-IFRS 제1118호 | 32 | 68 |
| **합계** | **285** | **600** (계산 59 · 정의 331 · 예외 51 · 상황 41 · 분류 40 · 비교 31 · 처리 24 · 수정 23) |

- 필수 개념 A01~K32 285개를 모두 독립 학습단위로 등록했습니다. 각 개념에는 정의, 필요한 이유, 핵심 원리, 유사 개념과의 차이, 사례, 공식(해당 시), 출제 포인트, 함정, 관련 개념이 들어 있습니다.
- 문항은 개념당 2~4개이며, 모든 문항을 기본(초성)·심화(무힌트) 두 모드로 풀 수 있습니다. 유형은 정의, 상황 판단, 비교, 예외, 잘못된 설명 수정, 분류, 처리방법, 계산입니다.
- 기출 01~46번 출제범위를 각각 하나 이상의 개념에 연결했고, 범위마다 3문항 이상이 연결되어 있습니다. 기출 원문은 수록하지 않았으며, 문제번호는 범위 추적용으로만 씁니다.
- 기준 표시: 각 개념·문항에 `[현재 기준]`, `[시행 예정 기준]`, `[과거 기준]`, `[개정사항 확인 필요]`를 표시합니다. K-IFRS 제1118호는 2027.1.1부터 시행되므로 2026년 기기 날짜에서는 "시행 예정"으로 보입니다. 원문 대조가 더 필요한 항목(제1118호 금융제공기업 선택권, 제1001호→제1008호 이관 조문 등)은 확정하지 않고 "개정사항 확인 필요"로 남겼습니다.
- 검증 결과: [docs/CONTENT_VALIDATION_REPORT.md](docs/CONTENT_VALIDATION_REPORT.md), [docs/content-validation.txt](docs/content-validation.txt)

### 변경 이력
- **1.0.1 (versionCode 2, 콘텐츠 v2)**: 600개 문항 전체에 관련 개념(relatedConceptIds)을 연결하고, 대단원별 기준 시행일(effectiveFrom)을 채웠습니다. 대단원 A는 회계기준이 아닌 경제 이론이라 시행일이 없습니다. 모든 항목의 revision을 2로 올려, 1.0.0 위에 설치하면 학습기록은 유지된 채 콘텐츠만 갱신됩니다.
- 1.0.0 (versionCode 1, 콘텐츠 v1): 최초 버전입니다.

## 3. 주요 기능

| 화면 | 내용 |
|---|---|
| 홈 | 오늘 복습할 문제, 미학습 개념, 권장 문제 수, 오늘 푼 문제, 연속 학습일 / 전체 학습현황 / 과목 / 빠른 시작(기본·심화·오답 복습·오늘의 복습·이어서 공부) |
| 과목 | 과목 → 대단원 → 중단원 → 세부 개념 계층, 개념 검색, 단원별 상태 |
| 개념 학습 | 개념 학습 11개 요소, 기준 상태 배지, 북마크, 개인 메모, 기본·심화 문제 시작 |
| 빈칸 문제 | 초성 힌트(기본)/무힌트(심화), 채점, 정답·핵심 개념·근거·헷갈리는 개념·함정·출제 포인트·계산과정·관련 개념 링크 |
| 출제 범위 | 전체, 대단원, 중단원, 개념, 기출범위, 미학습, 오답, 복습 예정, 랜덤 모의테스트 |
| 오답노트 | 과목·단원·개념·모드·최근순/횟수순·미해결 필터, 내 답·정답·해설·다시 풀기. 다시 맞혀도 과거 오답 이력은 보존 |
| 통계 | 학습일·연속일·누적 풀이·정답률(전체/기본/심화)·단원별 숙달도·취약 개념·오답 많은 개념·7일 막대그래프·30일 합계·숙달 단계 분포 |
| 기출범위별 학습현황 | 01~46번 범위별 개념 학습 수, 문항 풀이율, 정답률, 범위별 풀기 |
| 설정 | 글자 크기(상단 가−/가+ 버튼으로도 조절), 테마(시스템/라이트/다크), 권장 문제 수, 세트 문항 수, 복습 간격, 백업·복원, 콘텐츠 가져오기, 검증 리포트 |

### 출제 엔진(균등 출제)
`QuestionSelector`가 개념 단위로 우선순위를 매깁니다. 순서는 ① 한 번도 출제되지 않은 개념 ② 진도가 낮은 개념 ③ 오답이 많은 개념 ④ 복습일이 지난 개념 ⑤ 오래 학습하지 않은 개념 ⑥ 숙달한 개념입니다. 대단원별 출제 수는 개념 수 비율에 맞추고, 한 세트에서는 개념을 한 번씩만 출제합니다. 미출제 개념이 남아 있으면 이미 푼 개념은 다시 내지 않습니다.

### 숙달도와 간격 반복
- 숙달 단계: 미학습 → 학습 시작 → 기본 연습 중 → 기본 숙달 → 심화 연습 중 → 심화 숙달 → 완전 숙달. 복습일이 지나거나 숙달 뒤에 틀리면 "복습 필요"가 됩니다.
- 기본·심화 모드의 진도와 정답률은 따로 기록합니다.
- 복습 간격은 기본 1→3→7→14→30일이며 설정에서 바꿀 수 있습니다. 틀리면 첫 간격부터 다시 시작합니다. 정답이어도 답하는 데 60초를 넘기면 단계를 올리지 않고, 정답률이 60% 미만인 문항은 간격을 30% 줄입니다.

### 채점 규칙
공백·띄어쓰기, 유니코드 정규화(NFC/NFD), 영문 대소문자, 바깥 괄호, 끝 문장부호 차이는 허용합니다. 수치 문항은 쉼표와 단위(`1,000원`, `400억`, `12개월`)를 허용합니다. 동의어는 문항별로 등록된 것만 인정하며, 부분 일치나 유사어는 정답으로 처리하지 않습니다(예: `목적적합` ≠ `목적적합성`).

## 4. 프로젝트 구조

```
cpa-concept-lab/
  app/src/main/java/com/cpaconceptlab/study/
    data/local        Room Entity·DAO·AppDatabase(마이그레이션)
    data/model        콘텐츠 JSON DTO
    data/importer     ContentSource(번들/향후 HTTPS), 검증기, 트랜잭션 가져오기
    data/backup       백업·복원(SHA-256 무결성, 대체/병합)
    data/repository   Content/Study/Settings 저장소, 스냅샷
    domain            채점기, 모델(모드·범위·숙달·기준상태)
    domain/usecase    출제 엔진, 복습 스케줄러, 숙달도·통계 계산
    ui/…              home, subjects, concepts, quiz, review, statistics, settings, theme, components
    utils             초성 추출
  app/src/main/assets/content   manifest.json, structure_v2.json, concepts_v2.json, questions_v2.json
  app/schemas                   Room 스키마 1.json, 2.json
  tools/content                 콘텐츠 원본(파이썬 DSL)과 필수 개념 목록·출제범위 매핑
  tools/build_content.py        콘텐츠 검증과 JSON 생성
  keystore/debug-shared.keystore  공용 디버그 서명키(보안용 아님)
  dist/                         빌드한 APK
  docs/                         검증 리포트, 화면 캡처
```

도메인 계층은 Room Entity를 그대로 데이터 모델로 사용합니다. 콘텐츠 규모(수백~수천 문항)에서는 스냅샷을 메모리에 올려 두는 편이 단순하고 빠르기 때문입니다.

## 5. 빌드와 테스트

필요한 것: JDK 17 이상, Android SDK(platform 35, build-tools 35). Android Studio로 폴더를 열어도 됩니다.

```bash
cd cpa-concept-lab
echo "sdk.dir=/path/to/Android/sdk" > local.properties   # Android Studio 에서는 자동 생성
python3 tools/build_content.py      # 콘텐츠 검증과 JSON 생성(콘텐츠를 수정한 경우)
./gradlew testDebugUnitTest         # 단위·DB·UI 테스트(Robolectric)
./gradlew assembleDebug             # → app/build/outputs/apk/debug/app-debug.apk
```

### 테스트 결과(이번 빌드): 46개 중 46개 통과

| 테스트 | 내용 |
|---|---|
| ChosungTest(7) | 한글 초성 추출, 쌍자음, 띄어쓰기, 숫자 가림, 영문, 기호, NFD |
| AnswerGraderTest(5) | 정규화, 등록 동의어만 허용, 다른 개념 오인 방지, 수치·단위 표기 |
| ContentCoverageTest(11) | 필수 개념 285개 누락 0, 개념당 2문항 이상, 01~46 범위별 3문항 이상, 빈칸·정답·해설, 계산 변형문제, 문장 중복 감지, 동의어 모순·미존재 ID 감지, 비활성 문항 집계 제외, 잘못된 JSON 처리, 가져오기 검증 |
| QuestionSelectorTest(8) | 전체 개념 커버리지, 반복 없는 순환, 대단원 비중 균등, 우선순위, 오답 복습, 복습 예정, 모의테스트 |
| ReviewSchedulerTest(2), MasteryAndStandardTest(2) | 간격 반복, 숙달 단계 전이, 기준 상태 판정 |
| DatabaseTest(9) | 번들 로딩, 같은 콘텐츠 2회 가져오기 시 중복 없음, 새 개념·문항 추가 후 기록 유지, 잘못된 팩 거부 시 DB 불변, 오답 이력 보존, 백업→재설치→복원, 손상 백업 처리, **스키마 v1→v2 마이그레이션**, 업데이트 후 진도 유지 |
| AppUiTest(1) | MainActivity 실행 → 콘텐츠 로딩 → 첫 문제와 초성 표시 → 정답 채점·저장 → 오답 저장 |
| ScreenshotTest(1) | 주요 7개 화면 캡처([docs/screenshots](docs/screenshots)) |

이 환경에는 에뮬레이터(KVM)가 없어 실제 기기 설치는 직접 하지 못했습니다. 대신 Robolectric으로 실제 Activity와 Compose UI를 실행해 위 흐름을 검증했습니다.

## 6. 문서
- 콘텐츠 추가 방법: [CONTENT_GUIDE.md](CONTENT_GUIDE.md)
- APK 업데이트와 서명: [UPDATE_GUIDE.md](UPDATE_GUIDE.md)

## 7. 콘텐츠 출처와 주의
설명과 문항은 모두 학습용으로 새로 작성한 문장입니다. 기준서 본문이나 상용 교재를 복제하지 않았습니다. 기준 원문은 한국회계기준원(kasb.or.kr)과 IFRS 재단(ifrs.org)에서 확인하세요. 2027년 CPA 1차 시험의 실제 출제기준은 금융감독원 공인회계사시험 공고(cpa.fss.or.kr)에서 확인해야 합니다.

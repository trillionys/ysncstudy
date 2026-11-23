# CONTENT_GUIDE — 새 개념·문제 추가 방법

콘텐츠를 추가하는 방법은 두 가지입니다.

| 방법 | 언제 사용 | 결과 |
|---|---|---|
| A. 원본(DSL) 수정 후 새 APK 빌드 | 정식 버전 업데이트 | APK에 포함, 모든 기기에 동일하게 적용 |
| B. JSON 콘텐츠 팩을 앱에서 가져오기 | APK를 다시 만들지 않고 바로 추가 | 그 기기에만 적용, 전체 백업에 포함 |

---

## A. 원본(DSL) 수정 → 검증 → 빌드

콘텐츠 원본은 `tools/content/ch_*.py` 파일입니다(대단원마다 1개).

### 1) 개념 추가
```python
concept("D37", "개념명", "English name",
    d="정확한 정의",
    why="개념이 필요한 이유",
    core="핵심 원리",
    cmp="유사 개념과의 차이",
    ex="실제 회계 사례",
    tip="CPA 출제 포인트",
    trap="자주 틀리는 함정",
    f="공식과 계산과정(계산 개념만)",
    rel="D03 D09",              # 관련 개념 ID(공백 구분)
    parent="D03",               # 상위 개념(선택)
    std="근거 기준서", eff="2027-01-01",   # 시행일(선택)
    review=True, note="개정 메모")         # 확인이 필요한 경우
```
- `section("중단원명")` 아래에 쓴 개념은 그 중단원에 속합니다.
- 새 대단원을 만들 때는 `chapter("L", "제목", "English")`로 시작하는 새 파일을 만들고 `build_content.py`의 import 목록에 추가합니다.

### 2) 문항 추가(개념 바로 아래에)
```python
q("D37", "문장 속 핵심 개념은 (______)이다.", "정답",
  alt=["허용 동의어"],          # 명시적으로 등록한 표기만 정답 처리
  exp="왜 이 답이 정답인지",
  conf="헷갈리기 쉬운 개념",     # 선택
  t="definition",              # definition/situation/comparison/exception/correction/classification/treatment/formula/calculation
  lv=2,                        # 난이도 1~3
  also="D03",                  # 함께 연결할 개념(선택)
  unit="원", calc="공식·대입·계산 순서·결과")   # 계산문제
```

작성 규칙(검증기가 자동으로 확인합니다):
1. 빈칸 `(______)`은 문장마다 **정확히 1개**입니다.
2. 정답에는 조사나 문장부호를 넣지 않습니다(`자산을` ✕ → `자산` ○).
3. 정답이 문제 문장 안에 그대로 드러나면 오류입니다.
4. 동의어에 **다른 개념의 이름**을 넣으면 오류입니다(예: 목적적합성 문제에 '충실한 표현').
5. 단어 순서만 바꾼 중복 문장은 오류입니다.
6. 계산문제(`t="calculation"`)에는 `calc`를 반드시 씁니다. 숫자만 바꾼 변형문제는 서로 다른 문항으로 인정됩니다.
7. 개념마다 기본 문항이 2개 이상이어야 합니다.

### 3) 기출 출제범위 연결
`tools/content/exam_scopes.py`에 번호와 conceptId를 등록합니다. 범위마다 관련 문항이 3개 이상이어야 합니다.

### 4) 검증과 생성
```bash
python3 tools/build_content.py
```
- 검증에 실패하면 JSON을 만들지 않고 오류 목록을 출력합니다.
- 통과하면 `app/src/main/assets/content/*.json`과 `docs/CONTENT_VALIDATION_REPORT.md`가 갱신됩니다.
- 이어서 `./gradlew testDebugUnitTest`로 Kotlin 쪽 검증기(ContentCoverageTest)까지 확인합니다.

### 5) 콘텐츠 버전 올리기
`tools/build_content.py`의 `CONTENT_VERSION`을 1 올립니다(파일명이 `concepts_v2.json` 등으로 바뀝니다). 앱은 시작할 때 저장된 버전보다 번들 버전이 높으면 새 항목만 추가하거나 갱신합니다. 이어서 [UPDATE_GUIDE.md](UPDATE_GUIDE.md)에 따라 APK를 빌드합니다.

---

## v3 확장 문항 형식과 계산문항(재고자산·유형자산·차입원가)

`tools/content/dsl.py` 의 도우미를 쓴다. **OX·참거짓·예/아니요형은 만들지 않는다**(빌드 검증기 [13]과 앱 가져오기 검증기가 거부한다. '적격자산 / 적격자산 아님'처럼 긍정·부정 두 보기만 있는 분류도 같은 이유로 거부된다).

```python
from dsl import q, mc, slot_q, order_q, cq

mc("L035", "K-IFRS에서 허용되지 않는 원가결정방법은?", ["후입선출법", "선입선출법", "가중평균법", "개별법"], "후입선출법",
   exp="...", lv=3)                                     # 4지선다(보기 정확히 4개)
slot_q("R002", "각 항목을 분류하시오.", [("젖소", "생물자산"), ("원유", "수확물"), ("치즈", "수확 후 가공품")],
       exp="...", t="classification", options=["생물자산", "수확물", "수확 후 가공품"])   # 매칭·분류·분개 계정
order_q("Q012", "자본화 개시 판단 순서를 배열하시오.", ["지출 확인", "차입원가 확인", "활동 확인"], exp="...")
cq("P044", "원가 {cost}원, 잔존가치 {rv}원, 내용연수 {life}년 ... (______)원이다.",
   "dep_sl", dict(cost=1000000, rv=100000, life=5, months=12), "설명")       # 계산문항
```

- `cq` 는 정답을 손으로 쓰지 않는다. `tools/content/calc.py` 의 공식으로 계산하고, 문항에 `calcSpec {formulaId, params}` 를 저장한다. 앱의 `FormulaEngine`(Kotlin, 별도 구현)이 같은 값을 다시 계산해 `FormulaVerificationTest` 와 설정의 검증 리포트에서 대조한다. 새 공식은 calc.py 와 FormulaEngine.kt 양쪽에 추가해야 한다.
- 난이도: 1 정의·용어, 2 개념 구별, 3 사례 적용, 4 계산, 5 개념 연결 응용. 신규 개념마다 난이도 1~2 문항, 3~5 문항, 구별·응용형 문항이 각각 1개 이상 있어야 빌드가 통과한다([14]).
- 교재 관행(예: 정상·비정상 감모의 손익 구분, 소매재고법 원가율 공식, 외화차입금 한도 계산)은 기준서 규정처럼 쓰지 말고 `review=True, note="..."` 로 표시한다.
- 지정 학습범위: `tools/content/scope_bullets.txt` 의 세부항목이 개념·문항 본문에 반영되었는지 `scope_items.py` 가 검사한다([17]). 표현이 다르면 `ALIASES` 에 실제 사용한 표현을 등록한다(`+` = 같은 개념 안에 함께, `/` = 대안).
- 원본 문제 출처: `tools/content/sources.py` (NEW-INV-001~042). 원본 이미지를 실제로 판독한 경우에만 `readingConfidence`, `verificationStatus` 를 올린다. 판독할 수 없는 숫자로 정답을 만들지 않는다.
- 세부 설명 보강: `tools/content/addenda.py` 가 개념 핵심 원리 끝에 [세부] 문단을 덧붙인다.

## ID와 수정 정책(중요)

- conceptId(`D37`), questionId(`D37-Q01`)는 **영구 식별자**입니다. 학습기록은 이 ID에만 연결됩니다.
- 이미 사용한 ID를 **다른 개념이나 다른 문제에 재사용하지 않습니다.** `tools/content/id_registry.json`이 발급한 ID를 기록하며, 기존 ID의 개념명이나 정답이 바뀌면 빌드가 실패합니다.
- 문항 ID는 개념 안에서 쓴 순서대로 자동 부여됩니다. **새 문항은 해당 개념 문항 목록의 맨 뒤에 추가하고**, 기존 문항의 순서를 바꾸거나 지우지 마세요.
- 설명이나 오탈자만 고칠 때는 내용을 수정하고 `revision`을 올립니다. 앱은 더 높은 revision만 갱신하며, 학습기록은 그대로 둡니다.
- 문제의 의미나 정답이 바뀔 정도로 고칠 때는 새 questionId로 추가하고, 기존 문항은 `isActive=False`로 비활성화합니다. 비활성 문항은 출제와 진도율 분모에서 빠지며, 기존 기록은 보존됩니다.
- 개념은 삭제하지 않습니다. 필요하면 비활성화합니다.

---

## B. JSON 콘텐츠 팩 가져오기(앱: 설정 → 학습 콘텐츠 가져오기)

```json
{
  "formatVersion": 1,
  "contentVersion": 2,
  "packId": "my-pack-001",
  "chapters": [],
  "sections": [],
  "concepts": [
    {
      "id": "D37", "subjectId": "FA", "chapterId": "FA-D", "sectionId": "FA-D-S02",
      "koreanName": "개념명", "englishName": "Name",
      "definition": "정의", "whyItMatters": "필요한 이유", "detailedExplanation": "핵심 원리",
      "comparison": "유사 개념 차이", "example": "사례", "examTips": "출제 포인트", "commonMistakes": "함정",
      "relatedConceptIds": ["D03"], "revision": 1
    }
  ],
  "questions": [
    {
      "id": "D37-Q01", "primaryConceptId": "D37", "conceptIds": ["D37"],
      "subjectId": "FA", "chapterId": "FA-D", "questionType": "definition",
      "questionText": "문장 속 핵심 개념은 (______)이다.",
      "blankAnswer": "정답", "acceptedAnswers": ["정답", "동의어"],
      "explanation": "해설", "difficulty": 1, "revision": 1
    }
  ]
}
```

가져오기 절차(자동):
1. JSON 구조 검증. 손상되거나 형식이 틀리면 오류 메시지만 표시하고 앱은 종료되지 않습니다.
2. 형식 버전과 콘텐츠 버전 확인.
3. conceptId·questionId 중복과 형식 검사.
4. 정답·해설 존재, 빈칸 1개, 개념 연결, 대단원·중단원 존재 확인.
5. 오류가 하나도 없을 때만 **하나의 트랜잭션으로** 일괄 반영합니다. 실패하면 기존 데이터는 그대로입니다.
6. 이미 같은 revision으로 존재하는 ID는 건너뜁니다. 같은 팩을 두 번 가져와도 중복이 생기지 않습니다.
7. 결과 요약(추가·갱신·건너뜀 개수)을 표시합니다.

- 다른 과목을 추가하려면 `subjects`에 과목을, `chapters`에 대단원을, `sections`에 중단원을 함께 넣습니다. 미리 등록해 둔 과목 ID는 IA(중급), AA(고급), CM(원가관리), FM(재무관리), EC(경제), BL(기업법), TX(세법)이며, 콘텐츠가 들어오면 활성화됩니다.
- 가져온 팩의 원본은 DB에 보관되며 **전체 백업**에 포함되어 재설치 후에도 복원됩니다.
- 대단원·중단원 ID 형식: `FA-D`, `FA-D-S02`. 현재 ID 목록은 `app/src/main/assets/content/structure_v2.json`에 있습니다.
- 향후 HTTPS 업데이트를 붙일 때는 `ContentSource` 인터페이스를 구현해 같은 `ContentImporter`에 연결하면 됩니다.

## 검증 리포트 확인
- 빌드 시: `docs/CONTENT_VALIDATION_REPORT.md`
- 테스트 시: `app/build/reports/content/content-validation.txt`
- 앱에서: 설정 → 개념 누락 검증 리포트

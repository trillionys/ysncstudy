# UPDATE_GUIDE — 새 버전 APK 만들기와 학습기록 보존

## 핵심 원칙
1. `applicationId = "com.cpaconceptlab.study"`는 **절대 바꾸지 않습니다.**
2. 새 APK를 낼 때마다 `app/build.gradle.kts`의 `versionCode`를 **1 이상 올립니다**(`versionName`도 함께, 예: 1.0.0 → 1.1.0).
3. **이전 버전과 같은 서명키**로 서명합니다. 키가 다르면 Android가 업데이트 설치를 거부하고, 앱을 삭제해야만 설치할 수 있습니다(이 경우 기기 안의 기록이 사라집니다).
4. 설치는 **기존 앱 위에 덮어쓰기**로 합니다. 앱을 삭제하지 마세요.

## 업데이트 순서
```bash
cd cpa-concept-lab
# 1) 콘텐츠 수정 (CONTENT_GUIDE.md 참고) + tools/build_content.py 의 CONTENT_VERSION +1
python3 tools/build_content.py
# 2) app/build.gradle.kts : versionCode +1, versionName 변경
# 3) 테스트와 빌드
./gradlew testDebugUnitTest assembleDebug
# 4) 설치 전 휴대폰 앱에서: 설정 → 전체 데이터 백업 (안전장치)
# 5) 새 APK 를 기존 앱 위에 설치
```

새 버전을 처음 실행하면 앱이 다음을 자동으로 수행합니다.
1. 앱에 포함된 콘텐츠 버전 확인 → 2. 저장된 콘텐츠 버전 확인 → 3~6. 새 개념, 새 문항, revision이 높아진 설명만 추가하거나 갱신(같은 항목은 건너뜀) → 7~8. 학습기록 테이블은 건드리지 않고 새 개념에만 초기 상태 생성 → 9. 진도율과 통계 재계산(분모는 활성화된 전체 개념·문항 수).

이 전체 과정은 Room 트랜잭션으로 실행됩니다. 중간에 실패하면 이전 상태가 그대로 유지됩니다.

## 1.0.1 → 1.0.2 업데이트에서 일어나는 일

1. 같은 applicationId(`com.cpaconceptlab.study`)·같은 공유 디버그 키로 서명되고 versionCode 가 2 → 3 으로 올라가므로 기존 앱 위에 설치된다(삭제 불필요).
2. Room 이 `MIGRATION_2_3` 을 실행한다. 콘텐츠 테이블에 컬럼만 추가하고(chapters.partTitle, concepts 6개, questions 7개, 모두 기본값 지정) 새 콘텐츠 테이블 `problem_sources`, `scope_items` 를 만든다. **학습기록 테이블(question_attempts, question_progress, concept_progress, wrong_notes, study_days)은 건드리지 않는다.**
3. 첫 실행 동기화에서 콘텐츠 버전 2 < 3 이므로 새 개념 177개·문항 527개를 추가한다. 기존 285개 개념·600개 문항은 revision 이 같아 건너뛴다(중복 없음). 새 개념에만 '미학습' 초기 상태를 만든다.
4. 복습 간격: 이전 기본값(1,3,7,14,30)을 그대로 쓰던 사용자는 새 기본값(1,3,7,14,30,60)으로 올라가고, 직접 바꾼 간격은 유지된다.
5. 검증: `DatabaseTest.migration2To3KeepsLearningRecordsAndAddsNewContent` 가 v2 스키마 DB 에 학습기록·오답·북마크·메모·복습일을 넣고 마이그레이션 + v3 동기화 후 모두 남아 있는지 확인한다.

콘텐츠 버전(contentVersion, 현재 3)과 DB 스키마 버전(AppDatabase.VERSION, 현재 3)과 앱 버전(versionCode 3 / 1.0.2)은 서로 독립적으로 관리한다. 콘텐츠만 바뀌면 DB 스키마는 올리지 않는다.

## 데이터베이스 스키마를 바꿀 때
- 학습기록 테이블(`question_attempts`, `question_progress`, `concept_progress`, `wrong_notes`, `study_days`)과 콘텐츠 테이블은 분리되어 있습니다. 학습기록 테이블에는 콘텐츠 외래키를 걸지 않았기 때문에, 콘텐츠를 수정해도 기록이 연쇄 삭제되지 않습니다.
- 엔티티를 바꾸면 `AppDatabase.VERSION`을 올리고 **명시적 `Migration`을 추가**합니다(예: `MIGRATION_1_2`가 `concept_progress.memo` 컬럼을 추가). `fallbackToDestructiveMigration`은 사용하지 않습니다.
- 빌드하면 `app/schemas/<버전>.json`이 생성됩니다. 이 파일을 커밋하고, `DatabaseTest.migration1To2KeepsData`와 같은 방식으로 마이그레이션 테스트를 추가합니다.

## 서명
### 디버그 APK(현재 배포본)
- 저장소에 포함된 `keystore/debug-shared.keystore`(비밀번호 `android`)로 서명합니다. 어느 PC에서 빌드해도 같은 서명이 되므로 디버그 APK끼리는 덮어쓰기 업데이트가 됩니다.
- 이 키는 **보안용이 아닙니다**(공개 저장소에 있으므로 누구나 같은 서명의 APK를 만들 수 있습니다). 개인 학습용으로만 사용하세요.
- 서명 인증서 SHA-256: `fa:7d:77:c6:ed:cf:a0:a6:c8:92:32:d8:31:3e:aa:96:e1:03:bb:5f:c7:1e:34:25:b2:ff:99:dd:34:37:fc:e8`

### 릴리스 APK(선택)
릴리스 키는 저장소에 포함되어 있지 않습니다. 직접 만들고 안전하게 보관해야 합니다.
```bash
keytool -genkeypair -v -keystore my-release.jks -alias cpalab -keyalg RSA -keysize 2048 -validity 10000
```
`cpa-concept-lab/keystore.properties`(커밋 금지, .gitignore에 포함):
```
storeFile=my-release.jks
storePassword=****
keyAlias=cpalab
keyPassword=****
```
```bash
./gradlew assembleRelease   # → app/build/outputs/apk/release/app-release.apk
```
- 디버그 서명 앱에서 릴리스 서명 앱으로 바꾸면 서명이 달라 덮어쓰기가 되지 않습니다. 이때는 **전체 데이터 백업 → 앱 삭제 → 릴리스 설치 → 백업 복원** 순서로 옮깁니다.
- 릴리스 키를 잃어버리면 이후 업데이트를 설치할 수 없습니다. 키 파일과 비밀번호를 따로 백업해 두세요.

## 백업과 복원(설정 → 백업·복원)
| 기능 | 내용 |
|---|---|
| 전체 데이터 백업 | 학습기록, 설정, 직접 가져온 콘텐츠 팩 → JSON 파일(저장 위치는 직접 선택) |
| 백업 데이터 복원 | 현재 학습기록을 백업 내용으로 **대체**. 복원 전에 현재 데이터를 백업하도록 안내 |
| 학습기록 내보내기 | 학습기록만 저장 |
| 학습기록 가져오기 | 현재 기록과 **병합**(중복 풀이 제거, 더 최근 기록 우선) |

- 백업 파일에는 형식 버전, 콘텐츠 버전, 개념·문항 진도, 풀이기록, 오답노트, 복습 예정일, 학습일, 설정, SHA-256 체크섬이 들어 있습니다.
- 복원할 때 파일 형식, 백업 버전, 무결성(체크섬), 중복 기록, 존재하지 않는 ID, 앱 호환성을 검사합니다. 파일이 손상되었으면 앱이 종료되지 않고 오류 메시지만 표시합니다.
- 현재 콘텐츠에 없는 ID의 기록은 버리지 않고 보존합니다(통계에서는 제외). 나중에 해당 콘텐츠가 추가되면 자동으로 연결됩니다.
- Android 자동 백업(Auto Backup)도 켜 두었지만, 자동 복원은 기기와 계정 환경에 따라 보장되지 않습니다. **정기적으로 수동 백업 파일을 Google Drive 등에 보관하세요.**

## 빌드 환경 메모
- `settings.gradle.kts`에 Maven Central 미러(Google)를 추가했고, 단위 테스트의 Robolectric 런타임 다운로드도 이 미러를 사용합니다(요청 제한 대비). 다른 저장소를 쓰려면 `-ProbolectricRepo=<URL>`을 지정합니다.
- 테스트 메서드 이름은 영문으로 짓습니다. 한글 메서드명은 시스템 파일 인코딩이 UTF-8이 아닌 환경에서 컴파일 오류를 일으킵니다.

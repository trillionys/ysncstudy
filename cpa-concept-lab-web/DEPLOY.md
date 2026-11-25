# 배포·설정 안내(개발 경험이 없어도 따라 할 수 있게)

> 2026-11-25 이 작업 환경에서 Firebase 프로젝트 `cpa-concept-lab` 상태를 읽기 전용으로 확인한 결과:
> **Authentication 은 아직 시작되지 않았고**(API 응답 `CONFIGURATION_NOT_FOUND`), **Cloud Firestore 는 아직 만들어지지 않았습니다**(Firestore API 비활성).
> 따라서 아래 2~5단계를 마쳐야 로그인·의견 저장이 동작합니다. 보안 규칙도 아직 **게시되지 않았습니다**(파일만 작성·테스트됨).

## 1. 홈페이지 코드 위치
GitHub 저장소 `trillionys/ysncstudy` 의 **`cpa-concept-lab-web/`** 폴더입니다(Android 앱은 `cpa-concept-lab/`, 서로 독립).

로컬 실행(선택): Node.js 22 설치 → 터미널에서
```
cd cpa-concept-lab-web
npm install
cp .env.example .env     # 값 채우기(아래 4절 값)
npm run dev              # 브라우저에서 http://localhost:4321
```

## 2. Firebase Authentication — Google 로그인 켜기
1. https://console.firebase.google.com → 프로젝트 **cpa-concept-lab**
2. 왼쪽 **빌드 → Authentication → 시작하기**
3. **Sign-in method(로그인 방법)** 탭 → **Google** → **사용 설정** 켜기
4. **프로젝트 지원 이메일** 선택(Google 로그인 화면에 표시됨) → 저장
5. **설정 → 승인된 도메인(Authorized domains)**: 기본으로 `localhost`, `cpa-concept-lab.firebaseapp.com` 이 있습니다. Netlify 주소가 정해지면 6절에서 추가합니다.

## 3. Cloud Firestore 만들기
1. 왼쪽 **빌드 → Firestore Database → 데이터베이스 만들기**
2. 버전은 기본값(Standard), 위치는 **asia-northeast3 (서울)** 권장 — 한 번 정하면 바꿀 수 없습니다.
3. 시작 모드: **프로덕션 모드**(모든 접근 거부로 시작) 선택
4. 만든 위치를 `src/config/site.ts` 의 `firestoreLocation` 에 적습니다(예: `'asia-northeast3'`) — 개인정보 처리방침 국외이전 항목에 표시됩니다.

## 4. 보안 규칙 게시(필수)
1. **Firestore Database → 규칙(Rules)** 탭
2. ⚠️ 이미 다른 규칙이 있으면 지우지 말고, 이 폴더의 `firestore.rules` 에서 `match /feedback/{userId} { ... }` 블록만 기존 `match /databases/{database}/documents { }` 안에 넣습니다. 새로 만든 데이터베이스라면 `firestore.rules` 전체를 붙여넣습니다.
3. **게시(Publish)** → 상단에 게시 시각이 표시되면 적용된 것입니다.
4. (선택) 명령줄로 게시: `npx firebase login` → `npx firebase deploy --only firestore:rules`
5. 확인: 규칙 탭의 **규칙 플레이그라운드**에서 로그인 없이 `feedback/abc/items/x` get → **거부**되면 정상.

## 5. Netlify 에 올리기
### 방법 A — GitHub 연결(권장, 이후 커밋하면 자동 배포)
1. https://app.netlify.com → **Add new site → Import an existing project → GitHub** → `trillionys/ysncstudy`
2. 빌드 설정:
   - **Branch**: 배포할 브랜치(예: `main` — 이 작업 브랜치를 main 에 합친 뒤)
   - **Base directory**: `cpa-concept-lab-web`  ← 꼭 지정
   - Build command `npm run build`, Publish directory `dist` (netlify.toml 에서 자동)
3. **Environment variables(환경변수)** 에 아래 6개 추가(Site configuration → Environment variables에서도 가능):
   ```
   PUBLIC_FIREBASE_API_KEY             = AIzaSyCFV6XaRb6tmUi2IPWluhAfhtHrFyjkXkc
   PUBLIC_FIREBASE_AUTH_DOMAIN         = cpa-concept-lab.firebaseapp.com
   PUBLIC_FIREBASE_PROJECT_ID          = cpa-concept-lab
   PUBLIC_FIREBASE_STORAGE_BUCKET      = cpa-concept-lab.firebasestorage.app
   PUBLIC_FIREBASE_MESSAGING_SENDER_ID = 712678058101
   PUBLIC_FIREBASE_APP_ID              = 1:712678058101:web:c4311442d05845e97bf03c
   ```
   (`measurementId` 는 Analytics 를 쓰지 않으므로 넣지 않습니다. `PUBLIC_FIREBASE_USE_EMULATORS` 는 넣지 마세요.)
4. **Deploy** → 몇 분 뒤 `https://<이름>.netlify.app` 주소가 생깁니다. Site configuration → Change site name 으로 이름을 바꿀 수 있습니다(예: 비어 있다면 `cpaconceptlab`).
### 방법 B — 직접 올리기(드래그 앤 드롭)
1. 내 컴퓨터에서 `.env` 를 채우고 `npm install && npm run build`
2. https://app.netlify.com/drop 에 `dist` 폴더를 끌어다 놓기
   (이 방법은 Netlify 환경변수가 아니라 내 컴퓨터의 `.env` 값으로 빌드됩니다. 고칠 때마다 다시 빌드해 올려야 합니다.)

### 환경변수를 바꾼 뒤
환경변수는 **빌드할 때** 코드에 들어갑니다. 바꾼 뒤에는 Netlify **Deploys → Trigger deploy → Deploy site**(가능하면 *Clear cache and deploy site*)로 다시 배포해야 반영됩니다.

## 6. Netlify 주소를 Firebase 에 등록
1. Firebase Console → **Authentication → 설정 → 승인된 도메인 → 도메인 추가**
2. 실제로 생긴 주소의 도메인만 입력(예: `cpaconceptlab.netlify.app` — `https://`·경로 없이). 직접 산 도메인을 연결했다면 그 도메인도 추가.
3. 등록하지 않으면 로그인할 때 “이 사이트 주소가 아직 로그인 허용 목록에 없습니다”가 표시됩니다.

**authDomain 은 그대로 `cpa-concept-lab.firebaseapp.com` 으로 둡니다.** 이 사이트는 팝업 로그인을 기본으로 쓰므로 이 설정으로 동작합니다. 팝업이 막힌 경우에만 쓰는 ‘팝업 없이 로그인’(리다이렉트)은 Safari 등 제3자 저장소를 막는 브라우저에서 실패할 수 있습니다. 이를 해결하려면(선택, Firebase 공식 가이드 “리다이렉트 로그인 모범 사례”의 프록시 방식):
1. `netlify.toml` 에 이미 `/__/auth/*` 프록시가 들어 있습니다.
2. Google Cloud Console → API 및 서비스 → 사용자 인증 정보 → 웹 OAuth 클라이언트(자동 생성됨)의 **승인된 리디렉션 URI** 에 `https://<내 도메인>/__/auth/handler` 추가
3. Netlify 환경변수 `PUBLIC_FIREBASE_AUTH_DOMAIN` 을 `<내 도메인>` 으로 바꾸고 다시 배포
4. 데스크톱·모바일에서 팝업/리다이렉트 로그인을 모두 확인. 문제가 있으면 원래 값으로 되돌리면 됩니다.

## 7. 운영자 이름·이메일·Play 주소 입력
모두 **`src/config/site.ts`** 한 곳에서 입력합니다.
| 항목 | 의미 |
|---|---|
| `supportEmail` | 공개 고객지원 이메일(푸터·고객지원·처리방침·삭제 안내에 표시) |
| `privacyContactEmail` | 개인정보 문의 이메일(비우면 supportEmail) |
| `developerDisplayName` | 운영자 표시명(Play Console 개발자 이름과 같게) |
| `developerLegalName` | 법적 명칭(개인정보 보호책임자 표시) |
| `privacyEffectiveDate` / `termsEffectiveDate` | 게시 시행일(YYYY-MM-DD) |
| `firestoreLocation` | 3절의 Firestore 위치 |
| `googlePlayUrl` + `releaseStatus: 'released'` | **Play 출시 후** 입력하면 다운로드 버튼이 켜짐 |
| `premiumEnabled` | 유료 이용권이 앱에 실제 구현·가격 확정된 뒤에만 true |

입력 후 `npm run check:config` 로 빠진 항목을 확인하고, 커밋하면(방법 A) 자동 배포됩니다.

## 8. Play Console 에 개인정보 처리방침 URL 입력
Play Console → 앱 선택 → **정책 및 프로그램 → 앱 콘텐츠 → 개인정보처리방침** → `https://<내 도메인>/privacy` 입력 → 저장.
그 밖에 쓸 수 있는 URL(등록정보·데이터 보안 양식 등):
| 용도 | URL |
|---|---|
| 개인정보처리방침 | `https://<내 도메인>/privacy` |
| 웹사이트(스토어 등록정보 → 연락처 세부정보) | `https://<내 도메인>/` |
| 데이터 삭제 안내(데이터 보안 양식에서 요구하는 경우) | `https://<내 도메인>/data-deletion` |
| 고객지원 | `https://<내 도메인>/support` |
| 이용약관 | `https://<내 도메인>/terms` |

## 9. 고객 의견 확인·처리
Firebase Console → **Firestore Database → 데이터** → `feedback` → (사용자 UID) → `items` → 각 문서.
- 처리 상태 바꾸기: 문서의 `status` 값을 `in_progress`(검토 중) 또는 `resolved`(처리 완료)로 수정 → 이용자의 ‘내가 보낸 의견’에 반영
- 작성자 이메일이 필요하면 Authentication → 사용자 목록에서 같은 UID 를 찾습니다.
- `feedback/{UID}` 문서 자체(lastSubmittedAt, lastItemId)는 30초 제출 간격 제한용 기록입니다.

## 10. 로그인 테스트(배포 후 직접)
1. 시크릿 창으로 `/privacy`, `/terms`, `/data-deletion` 이 로그인 없이 열리는지
2. `/support` → Google 계정으로 로그인 → 이름·사진 표시
3. 의견 작성 → “의견이 정상적으로 접수되었습니다.” → Firestore 에 문서 생성 확인
4. ‘내가 보낸 의견’ 목록 → 로그아웃 → 다른 Google 계정으로 로그인 → 앞 계정의 의견이 안 보이는지
5. 휴대폰(Chrome, Safari, 삼성 인터넷)에서 같은 과정 반복. 팝업이 막히면 ‘팝업 없이 로그인’ 확인
6. 로그인 창에서 취소 → “로그인을 취소했습니다” 안내

## 11. 사용량·비용 확인
- Firebase Console → 왼쪽 아래 **요금제(Spark/Blaze)** 와 **사용량 및 결제** 메뉴. Spark(무료) 요금제는 결제 수단이 없어 요금이 청구되지 않고, 무료 한도를 넘으면 해당 기능이 멈춥니다.
- Firestore 사용량: Firestore Database → **사용량** 탭(읽기·쓰기·삭제 횟수)
- 공식 요금표: https://firebase.google.com/pricing · Firestore: https://cloud.google.com/firestore/pricing · Authentication 한도: https://firebase.google.com/docs/auth/limits
- Netlify: https://www.netlify.com/pricing/ , 사이트의 **Usage** 화면
- 이 사이트의 사용량 특성: 의견 1건 제출 = 쓰기 2회(의견 + 제출 간격 기록), 목록 열기 = 읽기 최대 10회(10개씩), 공개 페이지 방문은 Firebase 를 전혀 쓰지 않습니다.
- (무료 한도 수치는 요금제 개편으로 바뀔 수 있어 이 문서에 적지 않았습니다. 위 공식 페이지를 확인하세요.)
- 유료 서비스(Blaze, App Check 의 reCAPTCHA Enterprise, Cloud Functions 등)는 활성화하지 않았습니다.

## 12. 스팸·남용 대응
적용됨: 로그인 필수, 입력 길이·형식 서버 검증, 계정별 30초 제출 간격(보안 규칙, 서버 시각), 중복 클릭 방지, 본인 데이터만 접근.
한계: 여러 Google 계정을 만드는 공격까지 막지는 못합니다.
추가 선택지(필요할 때):
- **App Check**(reCAPTCHA v3 또는 Enterprise): 정상 사이트에서 온 요청만 허용. reCAPTCHA Enterprise 는 무료 사용량을 넘으면 Google Cloud 결제가 필요할 수 있으니 공식 요금을 확인한 뒤 결정하세요. 적용하면 코드에 App Check 초기화와 Console 의 ‘적용(Enforce)’이 필요합니다.
- **Cloud Functions 로 일일 제출 횟수 제한**: Blaze(종량제) 요금제가 필요합니다.

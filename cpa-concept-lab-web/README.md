# CPA Concept Lab 공식 홈페이지

Astro 정적 사이트(Netlify 배포) + Firebase Authentication(Google 로그인) + Cloud Firestore(고객지원 의견).

- 공개 페이지(로그인 불필요): `/` `/privacy` `/terms` `/updates` `/data-deletion` `/support`(안내·연락처·FAQ)
- 로그인 필요: `/support` 의 의견 작성, 내가 보낸 의견 조회·삭제, 계정 삭제
- Firebase JavaScript 는 `/support` 에서만 로드됩니다. 다른 페이지는 외부 스크립트가 없습니다.
- Google Analytics 등 분석·광고 도구 없음. 글꼴(Pretendard, SIL OFL 1.1)은 자체 호스팅.

배포·Firebase 설정 단계별 안내: **[DEPLOY.md](DEPLOY.md)** · 공개 전 확인 목록: **[docs/PRELAUNCH.md](docs/PRELAUNCH.md)**

## 폴더 구조
```
src/config/site.ts          운영 정보(운영자·이메일·Play 주소·출시 상태·가격·시행일) ← 여기만 고치면 전체 반영
src/lib/firebase.client.ts  Firebase 초기화(브라우저 전용, Analytics 없음)
src/lib/feedback-schema.ts  의견 유형·길이 제한(보안 규칙과 같은 값)
src/scripts/support.ts      로그인·의견 작성·목록·삭제·계정 삭제
src/pages/                  각 페이지(index, privacy, terms, support, updates, data-deletion, 404, sitemap.xml, robots.txt)
src/data/updates.json       업데이트 내역 데이터
firestore.rules             Firestore 보안 규칙(Firebase Console 에 게시해야 적용됨)
tests/rules/                보안 규칙 테스트(에뮬레이터)
tests/e2e/run.mjs           브라우저 E2E 테스트(에뮬레이터)
netlify.toml                Netlify 빌드·헤더·인증 경로 프록시
```

## 명령어
| 명령 | 내용 |
|---|---|
| `npm install` | 의존성 설치(Node 22 이상) |
| `npm run dev` | 로컬 개발 서버 http://localhost:4321 |
| `npm run build` | 정적 빌드 → `dist/` |
| `npm run check:config` | 공개 전 필수 운영 정보·환경변수 점검 |
| `npm run test:unit` | 입력 검증·규칙 값 일치 테스트 |
| `npm run test:rules` | Firestore 보안 규칙 테스트(에뮬레이터, Java 21 필요) |
| `npm run test:e2e` | 브라우저 E2E(에뮬레이터 + Playwright Chromium) |

## 환경변수
`.env.example` 을 `.env` 로 복사해 값을 넣습니다(`.env` 는 Git 에 올라가지 않음). 배포 시에는 Netlify 환경변수에 같은 이름으로 등록합니다.
`PUBLIC_` 값은 브라우저에 공개되는 클라이언트 구성정보입니다. 서비스 계정 키·Admin SDK 비밀키·OAuth Client Secret 은 절대 넣지 마세요.

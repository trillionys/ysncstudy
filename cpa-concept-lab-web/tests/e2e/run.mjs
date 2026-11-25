/**
 * 브라우저 E2E 테스트(Playwright + Firebase 에뮬레이터). scripts/e2e.mjs 가 실행합니다.
 * 실제 Firebase 프로젝트·Google 계정에는 접속하지 않습니다.
 * Google 로그인 팝업 대신, 에뮬레이터 빌드에만 들어 있는 테스트용 로그인(__e2eSignIn)으로
 * Firebase Auth 에뮬레이터에 Google 계정으로 로그인합니다.
 */
import { createRequire } from 'node:module';
import { serve } from '../../scripts/serve.mjs';

const require = createRequire(import.meta.url);
const { chromium, devices } = require('playwright');

const BASE = 'http://127.0.0.1:4399';
const PROJECT = 'demo-cpa-concept-lab';
const FS = `http://127.0.0.1:8080/v1/projects/${PROJECT}/databases/(default)/documents`;

const results = [];
let failed = 0;
async function test(name, fn) {
  try {
    await fn();
    results.push(`✔ ${name}`);
  } catch (e) {
    failed++;
    results.push(`✘ ${name}\n    ${String(e?.message ?? e).split('\n').slice(0, 4).join('\n    ')}`);
  }
}
function assert(cond, msg) {
  if (!cond) throw new Error(msg);
}
const adminGet = async (path) => (await fetch(`${FS}/${path}`, { headers: { Authorization: 'Bearer owner' } })).json();

const server = await serve('dist-e2e', 4399);
const browser = await chromium.launch();

// ───────────── 공개 페이지(로그인 없이) ─────────────
const publicPages = [
  ['/', '어려운 공부도'],
  ['/privacy', 'CPA Concept Lab 개인정보 처리방침'],
  ['/terms', '이용약관'],
  ['/updates', '업데이트 내역'],
  ['/data-deletion', '데이터 삭제 안내'],
  ['/support', '고객지원 및 의견 보내기'],
];

for (const [label, opts] of [
  ['데스크톱', { viewport: { width: 1280, height: 900 } }],
  ['모바일(Pixel 7)', devices['Pixel 7']],
  ['모바일(iPhone 13, WebKit 대신 Chromium 화면 크기)', { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 1 }],
]) {
  const ctx = await browser.newContext(opts); // 새 컨텍스트 = 저장소·쿠키 없는 시크릿 상태
  const page = await ctx.newPage();
  for (const [path, h1] of publicPages) {
    await test(`[${label}] ${path} 로그인 없이 열람`, async () => {
      const res = await page.goto(BASE + path);
      assert(res.status() === 200, `HTTP ${res.status()}`);
      const text = await page.locator('h1').first().innerText();
      assert(text.replace(/\s+/g, ' ').includes(h1), `h1 = ${text}`);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      assert(overflow <= 0, `가로 스크롤 발생(${overflow}px)`);
      const scripts = await page.locator('script[src]').evaluateAll((s) => s.map((x) => x.src));
      if (path !== '/support') assert(!scripts.some((s) => s.includes('support')), `Firebase 스크립트 로드됨: ${scripts}`);
      await page.locator('footer a[href="/privacy"]').waitFor();
    });
  }
  if (label !== '데스크톱') {
    await test(`[${label}] 햄버거 메뉴로 고객지원 이동`, async () => {
      await page.goto(BASE + '/');
      const nav = page.locator('#site-nav');
      assert(!(await nav.isVisible()), '메뉴가 처음부터 열려 있음');
      await page.getByRole('button', { name: '메뉴 열기' }).click();
      await page.locator('#site-nav').getByRole('link', { name: '고객지원' }).click();
      await page.waitForURL('**/support');
    });
  } else {
    await test('[데스크톱] 헤더 메뉴 링크 이동(주요 기능 → 섹션)', async () => {
      await page.goto(BASE + '/privacy');
      await page.locator('#site-nav').getByRole('link', { name: '주요 기능' }).click();
      await page.waitForURL('**/#features');
      assert(await page.locator('#features').isVisible(), '주요 기능 섹션 없음');
    });
  }
  await ctx.close();
}

await test('없는 주소는 404 페이지(상태 404)', async () => {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  const res = await page.goto(BASE + '/no-such-page');
  assert(res.status() === 404, `HTTP ${res.status()}`);
  assert((await page.locator('h1').innerText()).includes('찾을 수 없습니다'), '404 제목');
  await ctx.close();
});

await test('sitemap.xml·robots.txt', async () => {
  const sm = await (await fetch(BASE + '/sitemap.xml')).text();
  for (const p of ['/privacy', '/terms', '/updates', '/data-deletion', '/support']) assert(sm.includes(p + '</loc>'), `sitemap 에 ${p} 없음`);
  const rb = await (await fetch(BASE + '/robots.txt')).text();
  assert(rb.includes('Sitemap:'), 'robots.txt');
});

await test('홈 퀴즈 미리보기: 정답·오답·초성 힌트·계산문제', async () => {
  const ctx = await browser.newContext(devices['Pixel 7']);
  const page = await ctx.newPage();
  await page.goto(BASE + '/#try');
  const q1 = page.locator('form[data-q="q1"]');
  await q1.locator('input').fill('부채');
  await q1.getByRole('button', { name: '확인' }).click();
  assert((await q1.locator('.verdict').innerText()).includes('아쉬워요'), '오답 판정');
  await q1.getByRole('button', { name: '초성 힌트' }).click();
  assert((await q1.locator('.hint').innerText()) === 'ㅈㅂ', '초성 힌트');
  await q1.locator('input').fill(' 자본 ');
  await q1.getByRole('button', { name: '확인' }).click();
  assert((await q1.locator('.verdict').innerText()).includes('정답'), '정답 판정');
  const q2 = page.locator('form[data-q="q2"]');
  await q2.locator('input').fill('낮은');
  await q2.getByRole('button', { name: '확인' }).click();
  assert((await q2.locator('.verdict').innerText()).includes('정답'), 'q2');
  const q3 = page.locator('form[data-q="q3"]');
  await q3.locator('input').fill('300,000원');
  await q3.getByRole('button', { name: '확인' }).click();
  assert((await q3.locator('.verdict').innerText()).includes('정답'), 'q3 원 단위');
  await q3.locator('input').fill('30만원');
  await q3.getByRole('button', { name: '확인' }).click();
  assert((await q3.locator('.verdict').innerText()).includes('정답'), 'q3 만원 단위');
  assert((await page.locator('[data-score]').innerText()) === '3', '점수 3');
  await ctx.close();
});

// ───────────── 고객지원(에뮬레이터) ─────────────
const ctxA = await browser.newContext(devices['Pixel 7']);
const page = await ctxA.newPage();
const pageErrors = [];
page.on('pageerror', (e) => pageErrors.push(String(e)));
const signIn = (sub, name) => page.evaluate(([s, n]) => window.__e2eSignIn(s, n).then((c) => c.user.uid), [sub, name]);
const form = page.locator('[data-el="form"]');
let aliceUid = '';

await test('로그인 전: 로그인 버튼·안내·연락처·FAQ 표시, 작성 폼 숨김', async () => {
  await page.goto(BASE + '/support');
  await page.getByRole('button', { name: 'Google 계정으로 로그인' }).waitFor();
  assert(await page.getByText('피드백 작성 및 제출 내역 확인을 위해 Google 로그인이 필요합니다.').isVisible(), '안내 문구');
  assert(!(await form.isVisible()), '로그인 전 폼이 보임');
  assert(!(await page.getByRole('button', { name: '내가 보낸 의견' }).isVisible()), '로그인 전 목록 탭이 보임');
  assert(await page.locator('#contact-title').isVisible(), '연락처');
  assert((await page.locator('.faq details').count()) >= 5, 'FAQ');
  assert(await page.locator('[data-view="signed-out"] a[href="/privacy"]').isVisible(), '개인정보 처리방침 링크');
});

await test('Firebase(Google) 접속 실패 시 오류 안내 — 이 환경은 apis.google.com 차단', async () => {
  await page.route('https://apis.google.com/**', (r) => r.abort());
  const btn = page.getByRole('button', { name: 'Google 계정으로 로그인' });
  const popupPromise = ctxA.waitForEvent('page', { timeout: 15000 }).catch(() => null);
  await btn.click();
  const popup = await popupPromise;
  // 팝업이 열렸다면 닫아서 "취소"로 처리되는지 확인, 열리지 않으면 오류 문구 확인
  if (popup) await popup.close();
  const msg = page.locator('[data-el="auth-msg"]');
  await msg.waitFor({ state: 'visible', timeout: 30000 });
  const t = await msg.innerText();
  assert(/로그인|네트워크|취소/.test(t), `메시지: ${t}`);
  assert(!(await form.isVisible()), '실패 후 폼이 보임');
  results.push(`    (표시된 문구: ${t})`);
  await page.unroute('https://apis.google.com/**');
});

await test('Google 로그인(에뮬레이터) → 프로필·로그아웃·작성 폼 표시', async () => {
  aliceUid = await signIn('alice-google-id', '김앨리스');
  await page.locator('[data-el="name"]', { hasText: '김앨리스' }).waitFor();
  assert(await page.getByRole('button', { name: '로그아웃' }).isVisible(), '로그아웃 버튼');
  assert(await form.isVisible(), '폼');
  assert(!(await page.getByRole('button', { name: 'Google 계정으로 로그인' }).isVisible()), '로그인 버튼이 남아 있음');
  const body = await page.locator('body').innerText();
  assert(!body.includes('alice-google-id@example.com'), '이메일이 화면에 노출됨');
});

await test('잘못된 입력은 한국어 안내 후 저장하지 않음', async () => {
  await page.getByRole('button', { name: '의견 보내기' }).click();
  assert((await page.locator('[data-err="category"]').innerText()).includes('선택'), '유형 오류');
  assert((await page.locator('[data-err="title"]').innerText()).includes('제목'), '제목 오류');
  assert((await page.locator('[data-err="message"]').innerText()).includes('내용'), '내용 오류');
  const r = await adminGet(`feedback/${aliceUid}/items`);
  assert(!r.documents, '저장됨');
});

await test('의견 작성 → 서버 저장 후에만 완료 표시, Firestore 에 status=new·서버 시각 저장', async () => {
  await form.locator('select').selectOption('content_error');
  await form.locator('#f-title').fill('감가상각 해설 오타');
  await form.locator('#f-message').fill('정액법 해설에서 내용연수 표기가 잘못되었습니다.\n두 번째 줄.');
  await form.locator('#f-qid').fill('IA-0001-Q1');
  await form.locator('#f-ver').fill('1.0.8');
  const t0 = Date.now();
  await page.getByRole('button', { name: '의견 보내기' }).click();
  await page.getByText('의견이 정상적으로 접수되었습니다.').waitFor();
  const r = await adminGet(`feedback/${aliceUid}/items`);
  assert(r.documents?.length === 1, `문서 수 ${r.documents?.length}`);
  const f = r.documents[0].fields;
  assert(f.status.stringValue === 'new', 'status');
  assert(f.userId.stringValue === aliceUid, 'userId');
  assert(f.category.stringValue === 'content_error', 'category');
  assert(f.relatedQuestionId.stringValue === 'IA-0001-Q1' && f.appVersion.stringValue === '1.0.8', '선택 항목');
  const created = Date.parse(f.createdAt.timestampValue);
  assert(Math.abs(created - t0) < 60_000, `createdAt ${f.createdAt.timestampValue}`);
  assert(Object.keys(f).sort().join() === 'appVersion,category,createdAt,message,relatedQuestionId,status,title,userId', `필드 ${Object.keys(f)}`);
});

await test('30초 안에 다시 제출하면 서버 규칙이 거부하고 대기 시간 안내', async () => {
  await page.getByRole('button', { name: '새 의견 작성' }).click();
  await form.locator('select').selectOption('feature');
  await form.locator('#f-title').fill('두 번째 의견');
  await form.locator('#f-message').fill('바로 다시 보냄');
  await page.getByRole('button', { name: '의견 보내기' }).click();
  const msg = page.locator('[data-el="form-msg"]');
  await msg.filter({ hasText: '초에 한 번' }).waitFor({ timeout: 15000 });
  const r = await adminGet(`feedback/${aliceUid}/items`);
  assert(r.documents.length === 1, '두 번째가 저장됨');
  assert(await page.getByRole('button', { name: '의견 보내기' }).isEnabled(), '버튼이 계속 비활성');
});

await test('중복 제출 방지: 제출 중 버튼 비활성', async () => {
  // 네트워크를 느리게 해서 제출 중 상태를 관찰
  await page.route('**/127.0.0.1:8080/**', async (r) => {
    await new Promise((res) => setTimeout(res, 800));
    await r.continue();
  });
  await page.getByRole('button', { name: '의견 보내기' }).click();
  const busy = page.getByRole('button', { name: '보내는 중…' });
  await busy.waitFor();
  assert(await busy.isDisabled(), '제출 중인데 버튼이 활성');
  await page.getByRole('button', { name: '의견 보내기' }).waitFor({ timeout: 15000 });
  await page.unroute('**/127.0.0.1:8080/**');
});

await test('내가 보낸 의견: 제목·유형·날짜·처리 상태(접수됨) 표시', async () => {
  await page.getByRole('button', { name: '내가 보낸 의견' }).click();
  const item = page.locator('.fb-item').first();
  await item.waitFor();
  const s = await item.locator('summary').innerText();
  assert(s.includes('감가상각 해설 오타') && s.includes('문제 및 해설 오류') && s.includes('접수됨') && /\d{4}/.test(s), s);
  await item.locator('summary').click();
  assert((await item.locator('.fb-message').innerText()).includes('두 번째 줄'), '상세 내용');
  assert((await page.locator('.fb-item').count()) === 1, '목록 수');
});

await test('운영자가 Console 에서 바꾼 처리 상태(검토 중)가 목록에 반영', async () => {
  const r = await adminGet(`feedback/${aliceUid}/items`);
  const name = r.documents[0].name;
  await fetch(`http://127.0.0.1:8080/v1/${name}?updateMask.fieldPaths=status`, {
    method: 'PATCH',
    headers: { Authorization: 'Bearer owner', 'Content-Type': 'application/json' },
    body: JSON.stringify({ fields: { status: { stringValue: 'in_progress' } } }),
  });
  await page.getByRole('button', { name: '새로고침' }).click();
  await page.locator('.fb-item summary', { hasText: '검토 중' }).waitFor();
});

await test('로그아웃 → 폼·목록 숨김', async () => {
  await page.getByRole('button', { name: '로그아웃' }).click();
  await page.getByRole('button', { name: 'Google 계정으로 로그인' }).waitFor();
  assert(!(await form.isVisible()) && !(await page.locator('.fb-item').first().isVisible().catch(() => false)), '로그아웃 후 데이터가 보임');
});

await test('같은 계정 재로그인 → 이전 의견 확인', async () => {
  const uid = await signIn('alice-google-id', '김앨리스');
  assert(uid === aliceUid, 'UID 가 바뀜');
  await page.getByRole('button', { name: '내가 보낸 의견' }).click();
  await page.locator('.fb-item summary', { hasText: '감가상각 해설 오타' }).waitFor();
});

await test('로그인 상태 유지: 새로고침 후에도 로그인', async () => {
  await page.reload();
  await page.locator('[data-el="name"]', { hasText: '김앨리스' }).waitFor({ timeout: 15000 });
});

await test('다른 이용자(밥)로 로그인하면 앨리스 의견이 보이지 않음 + 브라우저에서 앨리스 경로 직접 조회 거부', async () => {
  await page.getByRole('button', { name: '로그아웃' }).click();
  await page.getByRole('button', { name: 'Google 계정으로 로그인' }).waitFor();
  const bobUid = await signIn('bob-google-id', '이밥');
  await page.getByRole('button', { name: '내가 보낸 의견' }).click();
  await page.getByText('아직 작성한 의견이 없습니다.').waitFor();
  // 로그인한 밥의 ID 토큰으로 앨리스 경로를 직접 요청(클라이언트에서 경로를 조작하는 상황)
  const token = await page.evaluate(async () => {
    const db = await new Promise((res) => {
      const req = indexedDB.open('firebaseLocalStorageDb');
      req.onsuccess = () => res(req.result);
    });
    const all = await new Promise((res) => {
      const r = db.transaction('firebaseLocalStorage').objectStore('firebaseLocalStorage').getAll();
      r.onsuccess = () => res(r.result);
    });
    return all.find((x) => x.value?.stsTokenManager)?.value.stsTokenManager.accessToken;
  });
  assert(token, '토큰 없음');
  const res = await fetch(`${FS}/feedback/${aliceUid}/items`, { headers: { Authorization: `Bearer ${token}` } });
  assert(res.status === 403, `앨리스 목록 조회 상태 ${res.status}`);
  const del = await fetch(`${FS}/feedback/${aliceUid}`, { method: 'DELETE', headers: { Authorization: `Bearer ${token}` } });
  assert(del.status === 403, `앨리스 기록 삭제 상태 ${del.status}`);
  assert(bobUid !== aliceUid, 'UID 같음');
});

await test('네트워크 끊김: 저장하지 않고 오류 안내', async () => {
  await page.getByRole('button', { name: '새로운 의견 작성' }).click();
  await form.locator('select').selectOption('usability');
  await form.locator('#f-title').fill('오프라인 테스트');
  await form.locator('#f-message').fill('연결 없음');
  await ctxA.setOffline(true);
  await page.getByRole('button', { name: '의견 보내기' }).click();
  await page.locator('[data-el="form-msg"]', { hasText: '인터넷' }).waitFor();
  await ctxA.setOffline(false);
  assert(!(await page.getByText('의견이 정상적으로 접수되었습니다.').isVisible()), '오프라인인데 접수 표시');
});

await test('본인 의견 삭제(확인창) — 밥이 의견 제출 후 삭제', async () => {
  await page.getByRole('button', { name: '의견 보내기' }).click();
  await page.getByText('의견이 정상적으로 접수되었습니다.').waitFor();
  await page.getByRole('button', { name: '내가 보낸 의견 확인' }).click();
  const item = page.locator('.fb-item').first();
  await item.waitFor();
  await item.locator('summary').click();
  let dialogText = '';
  page.once('dialog', (d) => {
    dialogText = d.message();
    d.dismiss();
  });
  await item.getByRole('button', { name: '이 의견 삭제' }).click();
  assert(dialogText.includes('삭제'), '확인창 없음');
  assert((await page.locator('.fb-item').count()) === 1, '취소했는데 삭제됨');
  page.once('dialog', (d) => d.accept());
  await item.getByRole('button', { name: '이 의견 삭제' }).click();
  await page.getByText('아직 작성한 의견이 없습니다.').waitFor();
  const r = await (await fetch(`${FS}:runQuery`, {
    method: 'POST',
    headers: { Authorization: 'Bearer owner', 'Content-Type': 'application/json' },
    body: JSON.stringify({ structuredQuery: { from: [{ collectionId: 'items', allDescendants: true }], where: { fieldFilter: { field: { fieldPath: 'title' }, op: 'EQUAL', value: { stringValue: '오프라인 테스트' } } } } }),
  })).json();
  assert(!r.some((x) => x.document), 'Firestore 에 남아 있음');
});

await test('계정 삭제: 의견 모두 삭제 후 로그인 계정 삭제(제출 직후에는 대기 안내)', async () => {
  // 밥은 방금 의견을 제출했으므로 30초 제한 안내
  page.once('dialog', (d) => d.accept());
  await page.getByRole('button', { name: '계정과 의견 모두 삭제' }).click();
  await page.locator('[data-el="account-msg"]', { hasText: '초 후' }).waitFor({ timeout: 15000 });
  await page.waitForTimeout(32_000);
  page.once('dialog', (d) => d.accept());
  await page.getByRole('button', { name: '계정과 의견 모두 삭제' }).click();
  await page.getByText('계정과 의견을 모두 삭제했습니다.').waitFor({ timeout: 20000 });
  const accounts = await (await fetch(`http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/projects/${PROJECT}/accounts:query`, {
    method: 'POST',
    headers: { Authorization: 'Bearer owner', 'Content-Type': 'application/json' },
    body: '{}',
  })).json();
  const names = (accounts.userInfo ?? []).map((u) => u.displayName);
  assert(!names.includes('이밥') && names.includes('김앨리스'), `남은 계정: ${names}`);
});

await test('고객지원 페이지 스크립트 오류 없음', async () => {
  assert(pageErrors.length === 0, pageErrors.join('\n'));
});

await ctxA.close();
await browser.close();
server.close();

console.log(results.join('\n'));
console.log(`\n${results.filter((r) => r.startsWith('✔')).length} 통과, ${failed} 실패`);
process.exit(failed ? 1 : 0);

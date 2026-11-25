/**
 * 고객지원 페이지(브라우저 전용). Firebase 는 이 페이지에서만 로드됩니다.
 * 화면에서 폼을 숨기는 것은 편의일 뿐이며, 실제 접근 통제는 firestore.rules 가 담당합니다.
 */
import {
  GoogleAuthProvider,
  deleteUser,
  getRedirectResult,
  onAuthStateChanged,
  reauthenticateWithPopup,
  signInWithPopup,
  signInWithCredential,
  signInWithRedirect,
  signOut,
  type User,
} from 'firebase/auth';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  startAfter,
  writeBatch,
  type QueryDocumentSnapshot,
} from 'firebase/firestore/lite';
import { firebase, missingFirebaseConfig } from '../lib/firebase.client';
import {
  LIMITS,
  SUBMIT_INTERVAL_SECONDS,
  categoryLabel,
  charCount,
  statusLabel,
  validateFeedback,
  type FeedbackInput,
} from '../lib/feedback-schema';

const PAGE_SIZE = 10;

// ── DOM ─────────────────────────────────────────────
const $ = <T extends HTMLElement = HTMLElement>(sel: string) => document.querySelector<T>(sel)!;
const panel = $('#auth-panel');
const views = {
  loading: $('[data-view="loading"]'),
  config: $('[data-view="config"]'),
  signedOut: $('[data-view="signed-out"]'),
  signedIn: $('[data-view="signed-in"]'),
  member: $('[data-view="member"]'),
};
const authMsg = $('[data-el="auth-msg"]');
const popupHelp = $('[data-el="popup-help"]');
const form = $<HTMLFormElement>('[data-el="form"]');
const formMsg = $('[data-el="form-msg"]');
const submitBtn = $<HTMLButtonElement>('[data-el="submit"]');
const done = $('[data-el="done"]');
const list = $('[data-el="list"]');
const listMsg = $('[data-el="list-msg"]');
const empty = $('[data-el="empty"]');
const moreBtn = $<HTMLButtonElement>('[data-action="more"]');
const accountMsg = $('[data-el="account-msg"]');

function show(el: HTMLElement, on: boolean) {
  el.hidden = !on;
}
function message(el: HTMLElement, text: string, kind: 'error' | 'success' | 'warn' | 'info' = 'info') {
  el.textContent = text;
  el.className = `notice small ${kind === 'info' ? '' : kind}`.trim();
  el.hidden = !text;
}
function setView(state: 'loading' | 'config' | 'signed-out' | 'signed-in') {
  show(views.loading, state === 'loading');
  show(views.config, state === 'config');
  show(views.signedOut, state === 'signed-out');
  show(views.signedIn, state === 'signed-in');
  show(views.member, state === 'signed-in');
  panel.setAttribute('aria-busy', String(state === 'loading'));
}

// ── 오류 메시지(한국어) ──────────────────────────────
function authError(e: unknown): string | null {
  const code = (e as { code?: string })?.code ?? '';
  switch (code) {
    case 'auth/popup-closed-by-user':
    case 'auth/cancelled-popup-request':
    case 'auth/user-cancelled':
      return '로그인을 취소했습니다. 의견을 보내려면 다시 로그인해 주세요.';
    case 'auth/popup-blocked':
      return null; // 별도 안내(popup-help)
    case 'auth/network-request-failed':
    case 'auth/internal-error':
    case 'auth/timeout':
      return '네트워크에 연결할 수 없습니다. 인터넷 연결을 확인한 뒤 다시 시도해 주세요.';
    case 'auth/unauthorized-domain':
      return '이 사이트 주소가 아직 로그인 허용 목록에 없습니다. 운영자가 설정을 마칠 때까지 이메일로 문의해 주세요.';
    case 'auth/operation-not-allowed':
    case 'auth/configuration-not-found':
    case 'auth/invalid-api-key':
    case 'auth/api-key-not-valid.-please-pass-a-valid-api-key.':
      return 'Google 로그인이 아직 준비되지 않았습니다. 운영자가 설정을 마칠 때까지 이메일로 문의해 주세요.';
    case 'auth/too-many-requests':
      return '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.';
    case 'auth/web-storage-unsupported':
      return '이 브라우저 설정에서는 로그인 상태를 저장할 수 없습니다. 시크릿 모드나 저장공간 차단을 해제하거나 다른 브라우저를 이용해 주세요.';
    case 'auth/requires-recent-login':
      return '보안을 위해 다시 로그인한 뒤 시도해 주세요.';
    case 'auth/account-exists-with-different-credential':
      return '같은 이메일로 다른 로그인 방식이 연결되어 있습니다. 이메일로 문의해 주세요.';
    default:
      return '로그인 중 문제가 생겼습니다. 잠시 후 다시 시도해 주세요.';
  }
}
function dbError(e: unknown): string {
  const code = (e as { code?: string })?.code ?? '';
  if (code === 'permission-denied') return '권한이 없습니다. 로그아웃 후 다시 로그인해 주세요.';
  if (code === 'unavailable' || code === 'deadline-exceeded') return '서버에 연결할 수 없습니다. 인터넷 연결을 확인한 뒤 다시 시도해 주세요.';
  if (code === 'resource-exhausted') return '현재 요청이 많아 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.';
  if (code === 'failed-precondition' || code === 'not-found') return '고객지원 저장소가 아직 준비되지 않았습니다. 이메일로 문의해 주세요.';
  return '처리 중 문제가 생겼습니다. 잠시 후 다시 시도해 주세요.';
}

function safeSession(op: 'get' | 'set' | 'remove', key: string): string | null {
  try {
    if (op === 'get') return sessionStorage.getItem(key);
    if (op === 'set') sessionStorage.setItem(key, '1');
    else sessionStorage.removeItem(key);
  } catch {
    /* 저장공간 차단 시 무시 */
  }
  return null;
}

// ── 초기화 ───────────────────────────────────────────
const missing = missingFirebaseConfig();
if (missing.length) {
  console.warn('Firebase 설정 누락:', missing.join(', '));
  setView('config');
} else {
  start();
}

function start() {
  const { auth, db } = firebase();
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });
  let user: User | null = null;
  let lastDoc: QueryDocumentSnapshot | null = null;
  let loadedOnce = false;

  // 리다이렉트 로그인에서 돌아온 경우에만 결과·오류 확인(평소에는 Google 인증 iframe 을 불러오지 않음)
  const REDIRECT_FLAG = 'cpa-support-redirect';
  if (safeSession('get', REDIRECT_FLAG)) {
    safeSession('remove', REDIRECT_FLAG);
    getRedirectResult(auth).catch((e) => {
      const m = authError(e);
      if (m) message(authMsg, m, 'error');
    });
  }

  // 에뮬레이터 테스트 전용 로그인(빌드 시 PUBLIC_FIREBASE_USE_EMULATORS=true 일 때만 포함, 배포 빌드에서는 제거됨)
  if (import.meta.env.PUBLIC_FIREBASE_USE_EMULATORS === 'true') {
    (window as unknown as Record<string, unknown>).__e2eSignIn = (sub: string, name: string) =>
      signInWithCredential(auth, GoogleAuthProvider.credential(JSON.stringify({ sub, email: `${sub}@example.com`, email_verified: true, name })));
  }

  onAuthStateChanged(auth, (u) => {
    user = u;
    message(authMsg, '');
    if (!u) {
      setView('signed-out');
      list.replaceChildren();
      lastDoc = null;
      loadedOnce = false;
      return;
    }
    $('[data-el="name"]').textContent = u.displayName || 'Google 사용자';
    const avatar = $<HTMLImageElement>('[data-el="avatar"]');
    if (u.photoURL) {
      avatar.src = u.photoURL;
      avatar.hidden = false;
      avatar.onerror = () => (avatar.hidden = true);
    } else avatar.hidden = true;
    setView('signed-in');
    if (location.hash === '#mine') selectTab('mine');
    else if (location.hash === '#account') document.getElementById('account')?.scrollIntoView();
  });

  // ── 로그인/로그아웃 ──
  const loginBtn = $<HTMLButtonElement>('[data-action="login"]');
  loginBtn.addEventListener('click', async () => {
    message(authMsg, '');
    show(popupHelp, false);
    loginBtn.disabled = true;
    try {
      await signInWithPopup(auth, provider);
    } catch (e) {
      if ((e as { code?: string }).code === 'auth/popup-blocked') show(popupHelp, true);
      const m = authError(e);
      if (m) message(authMsg, m, (e as { code?: string }).code?.includes('cancel') || (e as { code?: string }).code === 'auth/popup-closed-by-user' ? 'warn' : 'error');
    } finally {
      loginBtn.disabled = false;
    }
  });
  $('[data-action="login-redirect"]').addEventListener('click', () => {
    safeSession('set', REDIRECT_FLAG);
    signInWithRedirect(auth, provider).catch((e) => {
      safeSession('remove', REDIRECT_FLAG);
      const m = authError(e);
      if (m) message(authMsg, m, 'error');
    });
  });
  $('[data-action="logout"]').addEventListener('click', async () => {
    try {
      await signOut(auth);
      resetForm();
    } catch {
      message(authMsg, '로그아웃하지 못했습니다. 다시 시도해 주세요.', 'error');
    }
  });

  // ── 탭 ──
  function selectTab(tab: 'write' | 'mine') {
    document.querySelectorAll<HTMLButtonElement>('[data-tab]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.tab === tab)));
    document.querySelectorAll<HTMLElement>('[data-pane]').forEach((p) => (p.hidden = p.dataset.pane !== tab));
    if (tab === 'mine' && !loadedOnce) loadList(true);
  }
  document.querySelectorAll<HTMLButtonElement>('[data-tab]').forEach((b) =>
    b.addEventListener('click', () => selectTab(b.dataset.tab as 'write' | 'mine')),
  );

  // ── 글자 수 표시 ──
  (['title', 'message'] as const).forEach((name) => {
    const el = form.elements.namedItem(name) as HTMLInputElement;
    const counter = form.querySelector(`[data-count="${name}"]`)!;
    el.addEventListener('input', () => {
      counter.textContent = `${charCount(el.value).toLocaleString()} / ${LIMITS[name].toLocaleString()}`;
    });
  });

  function readForm(): FeedbackInput {
    const v = (n: string) => (form.elements.namedItem(n) as HTMLInputElement).value;
    return { category: v('category'), title: v('title'), message: v('message'), relatedQuestionId: v('relatedQuestionId'), appVersion: v('appVersion') };
  }
  function showErrors(errors: Partial<Record<keyof FeedbackInput, string>>) {
    form.querySelectorAll<HTMLElement>('[data-err]').forEach((p) => {
      const key = p.dataset.err as keyof FeedbackInput;
      p.textContent = errors[key] ?? '';
      const input = form.elements.namedItem(key) as HTMLElement | null;
      input?.setAttribute('aria-invalid', String(!!errors[key]));
    });
  }
  function resetForm() {
    form.reset();
    showErrors({});
    message(formMsg, '');
    form.querySelectorAll('[data-count]').forEach((c) => (c.textContent = c.textContent!.replace(/^[\d,]+/, '0')));
    show(form, true);
    show(done, false);
  }

  // ── 제출 ──
  let submitting = false;
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (submitting || !user) return;
    message(formMsg, '');
    const { ok, errors, value } = validateFeedback(readForm());
    showErrors(errors);
    if (!ok) {
      message(formMsg, '입력 내용을 확인해 주세요.', 'error');
      const first = Object.keys(errors)[0];
      (form.elements.namedItem(first) as HTMLElement | null)?.focus();
      return;
    }
    if (!navigator.onLine) {
      message(formMsg, '인터넷에 연결되어 있지 않습니다. 연결을 확인한 뒤 다시 보내 주세요.', 'error');
      return;
    }
    const u = user.uid;
    submitting = true;
    submitBtn.disabled = true;
    submitBtn.textContent = '보내는 중…';
    const slow = setTimeout(() => message(formMsg, '서버 응답이 늦어지고 있습니다. 창을 닫지 말고 잠시 기다려 주세요.', 'warn'), 10_000);
    try {
      const itemRef = doc(collection(db, 'feedback', u, 'items'));
      const batch = writeBatch(db);
      batch.set(itemRef, { userId: u, ...value, status: 'new', createdAt: serverTimestamp() });
      batch.set(doc(db, 'feedback', u), { lastSubmittedAt: serverTimestamp(), lastItemId: itemRef.id });
      await batch.commit(); // 서버가 저장을 확인한 뒤에만 완료
      clearTimeout(slow);
      message(formMsg, '');
      show(form, false);
      show(done, true);
      done.focus();
      loadedOnce = false; // 목록 다시 읽기
    } catch (err) {
      clearTimeout(slow);
      let m = dbError(err);
      if ((err as { code?: string }).code === 'permission-denied') {
        const wait = await secondsUntilAllowed(u);
        if (wait > 0) m = `의견은 ${SUBMIT_INTERVAL_SECONDS}초에 한 번 보낼 수 있습니다. ${wait}초 후 다시 보내 주세요.`;
      }
      message(formMsg, m, 'error');
    } finally {
      submitting = false;
      submitBtn.disabled = false;
      submitBtn.textContent = '의견 보내기';
    }
  });
  /** 다음 제출까지 남은 초(서버의 마지막 제출 시각 기준). exists: 제출 간격 기록 문서가 있는지 */
  async function submitState(u: string): Promise<{ wait: number; exists: boolean }> {
    const snap = await getDoc(doc(db, 'feedback', u));
    const t = snap.data()?.lastSubmittedAt?.toMillis?.();
    // 기기 시계가 틀려도 과하게 기다리지 않도록 최대 간격으로 제한(+1초 여유)
    const wait = t ? Math.min(SUBMIT_INTERVAL_SECONDS + 1, Math.max(0, Math.ceil((t + (SUBMIT_INTERVAL_SECONDS + 1) * 1000 - Date.now()) / 1000))) : 0;
    return { wait, exists: snap.exists() };
  }
  async function secondsUntilAllowed(u: string): Promise<number> {
    try {
      return (await submitState(u)).wait;
    } catch {
      return 0;
    }
  }

  $('[data-action="go-mine"]').addEventListener('click', () => selectTab('mine'));
  $('[data-action="new"]').addEventListener('click', () => {
    resetForm();
    (form.elements.namedItem('category') as HTMLElement).focus();
  });

  // ── 내가 보낸 의견(본인 경로만 조회, 최신순 10개씩) ──
  async function loadList(reset: boolean) {
    if (!user) return;
    const u = user.uid;
    message(listMsg, '');
    moreBtn.disabled = true;
    if (reset) {
      lastDoc = null;
      list.replaceChildren();
      show(empty, false);
      message(listMsg, '불러오는 중…');
    }
    try {
      const base = collection(db, 'feedback', u, 'items');
      const q = lastDoc
        ? query(base, orderBy('createdAt', 'desc'), startAfter(lastDoc), limit(PAGE_SIZE))
        : query(base, orderBy('createdAt', 'desc'), limit(PAGE_SIZE));
      const snap = await getDocs(q);
      if (user?.uid !== u) return; // 그 사이 로그아웃·계정 전환
      message(listMsg, '');
      snap.docs.forEach((d) => list.append(renderItem(d)));
      lastDoc = snap.docs.at(-1) ?? lastDoc;
      loadedOnce = true;
      show(empty, list.children.length === 0);
      show(moreBtn, snap.docs.length === PAGE_SIZE);
    } catch (e) {
      message(listMsg, dbError(e), 'error');
    } finally {
      moreBtn.disabled = false;
    }
  }
  moreBtn.addEventListener('click', () => loadList(false));
  $('[data-action="refresh"]').addEventListener('click', () => loadList(true));

  const fmt = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' });

  function renderItem(d: QueryDocumentSnapshot): HTMLLIElement {
    const data = d.data();
    const li = document.createElement('li');
    const det = document.createElement('details');
    det.className = 'fb-item';
    const sum = document.createElement('summary');
    const title = document.createElement('span');
    title.className = 'fb-title';
    title.textContent = String(data.title ?? '');
    const meta = document.createElement('span');
    meta.className = 'fb-meta';
    const status = String(data.status ?? 'new');
    const badge = document.createElement('span');
    badge.className = `badge status-${['new', 'in_progress', 'resolved'].includes(status) ? status : 'new'}`;
    badge.textContent = statusLabel(status);
    const cat = document.createElement('span');
    cat.textContent = categoryLabel(String(data.category ?? ''));
    const date = document.createElement('time');
    const created = data.createdAt?.toDate?.() as Date | undefined;
    if (created) date.dateTime = created.toISOString();
    date.textContent = created ? fmt.format(created) : '';
    meta.append(badge, cat, date);
    sum.append(title, meta);

    const body = document.createElement('div');
    body.className = 'fb-body';
    const msg = document.createElement('p');
    msg.className = 'fb-message';
    msg.textContent = String(data.message ?? '');
    const dl = document.createElement('dl');
    const add = (k: string, v: string) => {
      if (!v) return;
      const dt = document.createElement('dt');
      dt.textContent = k;
      const dd = document.createElement('dd');
      dd.textContent = v;
      dl.append(dt, dd);
    };
    add('관련 문제 ID', String(data.relatedQuestionId ?? ''));
    add('앱 버전', String(data.appVersion ?? ''));
    const del = document.createElement('button');
    del.type = 'button';
    del.className = 'btn btn-danger btn-sm';
    del.textContent = '이 의견 삭제';
    del.addEventListener('click', async () => {
      if (!user) return;
      if (!confirm(`‘${data.title}’ 의견을 삭제할까요?\n삭제하면 되돌릴 수 없습니다.`)) return;
      del.disabled = true;
      try {
        await deleteDoc(doc(db, 'feedback', user.uid, 'items', d.id));
        li.remove();
        show(empty, list.children.length === 0);
        message(listMsg, '의견을 삭제했습니다.', 'success');
      } catch (e) {
        del.disabled = false;
        message(listMsg, dbError(e), 'error');
      }
    });
    body.append(msg, dl, del);
    det.append(sum, body);
    li.append(det);
    return li;
  }

  // ── 계정 삭제: 의견 전부 삭제 → 제출 간격 기록 삭제 → 로그인 계정 삭제 ──
  const delAccBtn = $<HTMLButtonElement>('[data-action="delete-account"]');
  delAccBtn.addEventListener('click', async () => {
    if (!user) return;
    if (!confirm('로그인 계정과 내가 보낸 의견을 모두 삭제할까요?\n삭제하면 되돌릴 수 없습니다.')) return;
    const u = user;
    delAccBtn.disabled = true;
    message(accountMsg, '삭제하는 중입니다…');
    try {
      const { wait, exists } = await submitState(u.uid);
      if (wait > 0) {
        message(accountMsg, `마지막 의견을 보낸 직후에는 삭제할 수 없습니다. ${wait}초 후 다시 시도해 주세요.`, 'warn');
        return;
      }
      const items = collection(db, 'feedback', u.uid, 'items');
      for (;;) {
        const snap = await getDocs(query(items, limit(100)));
        if (snap.empty) break;
        const b = writeBatch(db);
        snap.docs.forEach((d) => b.delete(d.ref));
        await b.commit();
      }
      if (exists) await deleteDoc(doc(db, 'feedback', u.uid));
      try {
        await deleteUser(u);
      } catch (e) {
        if ((e as { code?: string }).code !== 'auth/requires-recent-login') throw e;
        message(accountMsg, '보안을 위해 Google 로그인을 한 번 더 진행해 주세요.', 'warn');
        await reauthenticateWithPopup(u, provider);
        await deleteUser(u);
      }
      resetForm();
      message(authMsg, '계정과 의견을 모두 삭제했습니다. 이용해 주셔서 감사합니다.', 'success');
    } catch (e) {
      const code = (e as { code?: string }).code ?? '';
      message(accountMsg, code.startsWith('auth/') ? `${authError(e) ?? '로그인 팝업이 차단되었습니다. 팝업을 허용한 뒤 다시 시도해 주세요.'} (의견은 이미 삭제되었을 수 있습니다. 다시 시도하면 계정 삭제를 이어서 진행합니다.)` : dbError(e), 'error');
    } finally {
      delAccBtn.disabled = false;
    }
  });
}

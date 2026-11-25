/**
 * Firestore 보안 규칙 테스트 — Firebase 에뮬레이터에서 실행(실제 프로젝트에 접속하지 않음).
 *   npm run test:rules
 */
import { readFileSync } from 'node:fs';
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
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
  setDoc,
  Timestamp,
  updateDoc,
  writeBatch,
  type Firestore,
} from 'firebase/firestore';

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-cpa-concept-lab',
    firestore: { rules: readFileSync('firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
  });
});
afterAll(async () => env?.cleanup());
beforeEach(async () => env.clearFirestore());

// rules-unit-testing 은 compat 타입을 돌려주지만 모듈형 API 와 같은 인스턴스로 동작
const fs = (x: unknown) => x as Firestore;
const alice = () => fs(env.authenticatedContext('alice').firestore());
const bob = () => fs(env.authenticatedContext('bob').firestore());
const guest = () => fs(env.unauthenticatedContext().firestore());

const base = (uid: string) => ({
  userId: uid,
  category: 'feature',
  title: '복습 알림 기능',
  message: '복습 날짜에 알림을 받고 싶어요.',
  relatedQuestionId: '',
  appVersion: '1.0.8',
  status: 'new',
  createdAt: serverTimestamp(),
});

/** 앱과 같은 방식(의견 + 제출 간격 기록을 한 번에)으로 제출 */
function submit(db: Firestore, pathUid: string, data: Record<string, unknown>, metaUid = pathUid) {
  const item = doc(collection(db, 'feedback', pathUid, 'items'));
  const b = writeBatch(db);
  b.set(item, data);
  b.set(doc(db, 'feedback', metaUid), { lastSubmittedAt: serverTimestamp(), lastItemId: item.id });
  return { commit: () => b.commit(), id: item.id };
}

/** 규칙을 건너뛰고 기존 데이터 준비(관리자 권한) */
async function seed(uid: string, id: string, extra: Record<string, unknown> = {}, lastAgoSec = 3600) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = fs(ctx.firestore());
    await setDoc(doc(db, 'feedback', uid, 'items', id), { ...base(uid), createdAt: Timestamp.now(), ...extra });
    await setDoc(doc(db, 'feedback', uid), {
      lastSubmittedAt: Timestamp.fromMillis(Date.now() - lastAgoSec * 1000),
      lastItemId: id,
    });
  });
}

describe('제출(create)', () => {
  it('비로그인 제출 차단', async () => {
    await assertFails(submit(guest(), 'alice', base('alice')).commit());
  });

  it('본인 피드백 제출 허용', async () => {
    await assertSucceeds(submit(alice(), 'alice', base('alice')).commit());
  });

  it('선택 항목을 채운 제출 허용(한글 3,000자 본문, 100자 제목)', async () => {
    await assertSucceeds(
      submit(alice(), 'alice', {
        ...base('alice'),
        title: '가'.repeat(100),
        message: '나'.repeat(3000),
        relatedQuestionId: 'IA-0123-Q2',
        appVersion: '1.0.8 (9)',
        category: 'content_error',
      }).commit(),
    );
  });

  it('타인 UID 경로로 제출 차단', async () => {
    await assertFails(submit(alice(), 'bob', base('bob')).commit());
  });

  it('문서 안의 userId 를 타인으로 위조하면 차단', async () => {
    await assertFails(submit(alice(), 'alice', base('bob')).commit());
  });

  it('허용되지 않은 필드 차단', async () => {
    await assertFails(submit(alice(), 'alice', { ...base('alice'), email: 'a@example.com' }).commit());
  });

  it('필수 필드 누락 차단', async () => {
    const { appVersion: _omit, ...rest } = base('alice');
    await assertFails(submit(alice(), 'alice', rest).commit());
  });

  it('허용되지 않은 유형 차단', async () => {
    await assertFails(submit(alice(), 'alice', { ...base('alice'), category: 'spam' }).commit());
  });

  it('status 위조 차단(resolved 로 제출)', async () => {
    await assertFails(submit(alice(), 'alice', { ...base('alice'), status: 'resolved' }).commit());
  });

  it('작성 시각 위조 차단(클라이언트 시각)', async () => {
    await assertFails(submit(alice(), 'alice', { ...base('alice'), createdAt: Timestamp.fromDate(new Date('2020-01-01')) }).commit());
  });

  it('비정상적으로 긴 내용 차단(3,001자)', async () => {
    await assertFails(submit(alice(), 'alice', { ...base('alice'), message: '가'.repeat(3001) }).commit());
  });

  it('긴 제목(101자)·관련 문제 ID(101자)·앱 버전(51자) 차단', async () => {
    await assertFails(submit(alice(), 'alice', { ...base('alice'), title: 'a'.repeat(101) }).commit());
    await assertFails(submit(alice(), 'alice', { ...base('alice'), relatedQuestionId: 'a'.repeat(101) }).commit());
    await assertFails(submit(alice(), 'alice', { ...base('alice'), appVersion: 'a'.repeat(51) }).commit());
  });

  it('빈 제목·공백뿐인 내용 차단', async () => {
    await assertFails(submit(alice(), 'alice', { ...base('alice'), title: '' }).commit());
    await assertFails(submit(alice(), 'alice', { ...base('alice'), message: '   \n ' }).commit());
  });

  it('문자열이 아닌 값 차단', async () => {
    await assertFails(submit(alice(), 'alice', { ...base('alice'), title: 123 }).commit());
    await assertFails(submit(alice(), 'alice', { ...base('alice'), relatedQuestionId: null }).commit());
  });

  it('제출 간격 기록 없이 의견만 쓰면 차단', async () => {
    await assertFails(setDoc(doc(collection(alice(), 'feedback', 'alice', 'items')), base('alice')));
  });

  it('30초 안에 연속 제출 차단, 30초 후 허용', async () => {
    await seed('alice', 'old', {}, 5); // 5초 전 제출
    await assertFails(submit(alice(), 'alice', base('alice')).commit());
    await seed('alice', 'old', {}, 31); // 31초 전 제출
    await assertSucceeds(submit(alice(), 'alice', base('alice')).commit());
  });

  it('한 번에 두 건 넣기 차단', async () => {
    const db = alice();
    const a = doc(collection(db, 'feedback', 'alice', 'items'));
    const c = doc(collection(db, 'feedback', 'alice', 'items'));
    const b = writeBatch(db);
    b.set(a, base('alice'));
    b.set(c, base('alice'));
    b.set(doc(db, 'feedback', 'alice'), { lastSubmittedAt: serverTimestamp(), lastItemId: a.id });
    await assertFails(b.commit());
  });

  it('제출 간격 기록 삭제로 제한 우회 차단(30초 안에는 삭제 불가)', async () => {
    await seed('alice', 'old', {}, 5);
    await assertFails(deleteDoc(doc(alice(), 'feedback', 'alice')));
  });
});

describe('조회(read)', () => {
  it('비로그인 조회 차단', async () => {
    await seed('alice', 'a1');
    await assertFails(getDoc(doc(guest(), 'feedback', 'alice', 'items', 'a1')));
    await assertFails(getDocs(collection(guest(), 'feedback', 'alice', 'items')));
  });

  it('본인 조회 허용(목록 최신순 + 상세)', async () => {
    await seed('alice', 'a1');
    await assertSucceeds(getDocs(query(collection(alice(), 'feedback', 'alice', 'items'), orderBy('createdAt', 'desc'), limit(10))));
    await assertSucceeds(getDoc(doc(alice(), 'feedback', 'alice', 'items', 'a1')));
  });

  it('타인 피드백 조회 차단(경로의 UID 를 바꿔도)', async () => {
    await seed('alice', 'a1');
    await assertFails(getDoc(doc(bob(), 'feedback', 'alice', 'items', 'a1')));
    await assertFails(getDocs(collection(bob(), 'feedback', 'alice', 'items')));
    await assertFails(getDoc(doc(bob(), 'feedback', 'alice')));
  });

  it('전체 사용자 목록 조회 차단', async () => {
    await seed('alice', 'a1');
    await assertFails(getDocs(collection(alice(), 'feedback')));
  });

  it('다른 컬렉션 접근 차단', async () => {
    await assertFails(getDoc(doc(alice(), 'users', 'alice')));
    await assertFails(setDoc(doc(alice(), 'users', 'alice'), { a: 1 }));
  });
});

describe('수정·삭제', () => {
  it('일반 이용자의 status 변경 차단', async () => {
    await seed('alice', 'a1');
    await assertFails(updateDoc(doc(alice(), 'feedback', 'alice', 'items', 'a1'), { status: 'resolved' }));
  });

  it('일반 이용자의 임의 내용 수정 차단', async () => {
    await seed('alice', 'a1');
    await assertFails(updateDoc(doc(alice(), 'feedback', 'alice', 'items', 'a1'), { title: '바꾼 제목' }));
    await assertFails(setDoc(doc(alice(), 'feedback', 'alice', 'items', 'a1'), { ...base('alice') }));
  });

  it('본인 피드백 삭제 허용', async () => {
    await seed('alice', 'a1');
    await assertSucceeds(deleteDoc(doc(alice(), 'feedback', 'alice', 'items', 'a1')));
  });

  it('타인 피드백 삭제 차단', async () => {
    await seed('alice', 'a1');
    await assertFails(deleteDoc(doc(bob(), 'feedback', 'alice', 'items', 'a1')));
    await assertFails(deleteDoc(doc(guest(), 'feedback', 'alice', 'items', 'a1')));
  });

  it('계정 정리: 30초가 지난 본인 제출 간격 기록은 삭제 허용', async () => {
    await seed('alice', 'a1', {}, 60);
    await assertSucceeds(deleteDoc(doc(alice(), 'feedback', 'alice')));
  });
});

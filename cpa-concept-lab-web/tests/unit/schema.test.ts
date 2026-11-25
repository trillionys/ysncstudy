import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { CATEGORIES, LIMITS, SUBMIT_INTERVAL_SECONDS, validateFeedback, charCount } from '../../src/lib/feedback-schema';

const rules = readFileSync('firestore.rules', 'utf8');

describe('화면 검증과 보안 규칙이 같은 값을 쓰는지', () => {
  it('유형 목록', () => {
    const m = rules.match(/d\.category in \[([^\]]+)\]/);
    expect(m).toBeTruthy();
    const inRules = m![1].split(',').map((s) => s.trim().replace(/'/g, ''));
    expect(inRules).toEqual(CATEGORIES.map((c) => c.value));
  });
  it('길이 제한', () => {
    expect(rules).toContain(`textOk(d.title, 1, ${LIMITS.title})`);
    expect(rules).toContain(`textOk(d.message, 1, ${LIMITS.message})`);
    expect(rules).toContain(`textOk(d.relatedQuestionId, 0, ${LIMITS.relatedQuestionId})`);
    expect(rules).toContain(`textOk(d.appVersion, 0, ${LIMITS.appVersion})`);
  });
  it('제출 간격', () => {
    expect(rules).toContain(`duration.value(${SUBMIT_INTERVAL_SECONDS}, 's')`);
  });
});

describe('validateFeedback', () => {
  const ok = { category: 'feature', title: ' 제목 ', message: '내용', relatedQuestionId: '', appVersion: '' };
  it('정상 입력은 앞뒤 공백을 지우고 통과', () => {
    const r = validateFeedback(ok);
    expect(r.ok).toBe(true);
    expect(r.value.title).toBe('제목');
  });
  it('필수 항목 누락', () => {
    const r = validateFeedback({ ...ok, category: '', title: '  ', message: '' });
    expect(Object.keys(r.errors).sort()).toEqual(['category', 'message', 'title']);
  });
  it('길이 초과', () => {
    expect(validateFeedback({ ...ok, title: '가'.repeat(101) }).errors.title).toContain('100자');
    expect(validateFeedback({ ...ok, message: '가'.repeat(3001) }).errors.message).toContain('3,000자');
    expect(validateFeedback({ ...ok, relatedQuestionId: 'a'.repeat(101) }).ok).toBe(false);
    expect(validateFeedback({ ...ok, appVersion: 'a'.repeat(51) }).ok).toBe(false);
    expect(validateFeedback({ ...ok, title: '가'.repeat(100), message: '나'.repeat(3000) }).ok).toBe(true);
  });
  it('이모지는 1자로 센다', () => {
    expect(charCount('👍👍')).toBe(2);
  });
  it('정의되지 않은 유형 거부', () => {
    expect(validateFeedback({ ...ok, category: 'spam' }).ok).toBe(false);
  });
});

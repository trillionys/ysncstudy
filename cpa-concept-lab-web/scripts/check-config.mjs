// 공개 전 점검: src/config/site.ts 의 필수 운영 정보와 Firebase 환경변수가 채워졌는지 확인
import { readFileSync, existsSync } from 'node:fs';

const src = readFileSync('src/config/site.ts', 'utf8');
const val = (k) => (src.match(new RegExp(`\\n\\s*${k}:\\s*'([^']*)'`)) || [])[1] ?? null;
const required = {
  supportEmail: '고객지원 이메일',
  developerDisplayName: '운영자 표시명',
  developerLegalName: '운영자 법적 명칭(개인정보 보호책임자)',
  privacyEffectiveDate: '개인정보 처리방침 시행일',
  termsEffectiveDate: '이용약관 시행일',
  firestoreLocation: 'Firestore 위치',
};
const problems = [];
for (const [k, label] of Object.entries(required)) if (!val(k)) problems.push(`src/config/site.ts → ${k} (${label}) 비어 있음`);
const status = val('releaseStatus') ?? (src.match(/releaseStatus:\s*'(\w+)'/) || [])[1];
if (status === 'released' && !val('googlePlayUrl')) problems.push("releaseStatus 가 'released' 인데 googlePlayUrl 이 비어 있음");

const env = { ...process.env };
if (existsSync('.env')) for (const line of readFileSync('.env', 'utf8').split('\n')) {
  const m = line.match(/^\s*([A-Z_]+)\s*=\s*(.*)\s*$/);
  if (m && !env[m[1]]) env[m[1]] = m[2];
}
for (const k of ['PUBLIC_FIREBASE_API_KEY', 'PUBLIC_FIREBASE_AUTH_DOMAIN', 'PUBLIC_FIREBASE_PROJECT_ID', 'PUBLIC_FIREBASE_APP_ID', 'PUBLIC_FIREBASE_MESSAGING_SENDER_ID', 'PUBLIC_FIREBASE_STORAGE_BUCKET'])
  if (!env[k]) problems.push(`환경변수 ${k} 없음(.env 또는 Netlify 환경변수)`);
if (env.PUBLIC_FIREBASE_USE_EMULATORS === 'true') problems.push('PUBLIC_FIREBASE_USE_EMULATORS=true — 배포 환경에서는 지우세요');

if (problems.length) {
  console.log('공개 전 확인 필요:\n - ' + problems.join('\n - '));
  process.exit(1);
}
console.log('운영 정보·Firebase 설정 확인 완료');

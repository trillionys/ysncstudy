// E2E: 에뮬레이터용으로 dist-e2e 에 빌드한 뒤, Auth·Firestore 에뮬레이터를 띄우고 브라우저 테스트 실행
import { execSync } from 'node:child_process';

const env = {
  ...process.env,
  ASTRO_OUT_DIR: 'dist-e2e',
  PUBLIC_FIREBASE_USE_EMULATORS: 'true',
  PUBLIC_FIREBASE_PROJECT_ID: 'demo-cpa-concept-lab',
  PUBLIC_FIREBASE_API_KEY: 'fake-api-key',
  PUBLIC_FIREBASE_AUTH_DOMAIN: '127.0.0.1',
  PUBLIC_FIREBASE_APP_ID: '1:1:web:1',
  PUBLIC_FIREBASE_MESSAGING_SENDER_ID: '1',
  PUBLIC_FIREBASE_STORAGE_BUCKET: 'demo-cpa-concept-lab.appspot.com',
};
const run = (cmd) => execSync(cmd, { stdio: 'inherit', env });
run('npx astro build');
run('npx firebase emulators:exec --only auth,firestore --project demo-cpa-concept-lab "node tests/e2e/run.mjs"');

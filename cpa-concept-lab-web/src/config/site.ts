/**
 * CPA Concept Lab 홈페이지 운영 정보 — 이 파일 한 곳에서만 관리합니다.
 *
 * ⚠️ 공개 전에 빈 값('')을 반드시 채우세요. `npm run check:config` 로 확인할 수 있습니다.
 *    빈 값은 화면에서 "준비 중"으로 표시되며, 가짜 값을 만들어 넣지 않습니다.
 */

export type ReleaseStatus = 'prelaunch' | 'released';

export const site = {
  /** 앱 이름 */
  appName: 'CPA Concept Lab',

  /**
   * 사이트 주소. 비워 두면 Netlify 가 빌드할 때 알려 주는 실제 사이트 주소(URL 환경변수)를 씁니다.
   * 직접 산 도메인을 연결했다면 여기 또는 Netlify 환경변수 PUBLIC_SITE_URL 에 입력하세요.
   * (실제 값은 astro.config.mjs 에서 정해져 import.meta.env.SITE 로 들어옵니다.)
   */
  siteUrl: import.meta.env.SITE as string,

  /** 고객지원 이메일(공개) — 예: support@도메인. 공개 전 입력 필수 */
  supportEmail: '',
  /** 개인정보 문의 이메일(공개). 비우면 supportEmail 을 사용 */
  privacyContactEmail: '',
  /** 화면에 표시할 개발자/운영자 이름(Play Console 개발자 이름과 같게). 공개 전 입력 필수 */
  developerDisplayName: '',
  /** 법적 명칭(개인이면 성명, 사업자면 상호·대표자). 개인정보 보호책임자 표시에 사용. 공개 전 입력 필수 */
  developerLegalName: '',

  /** Google Play 앱 주소. 출시 후 입력하고 releaseStatus 를 'released' 로 바꾸면 다운로드 버튼이 켜집니다. */
  googlePlayUrl: '',
  releaseStatus: 'prelaunch' as ReleaseStatus,

  /** 유료 이용권 — 앱에 실제 구현되고 가격이 확정되기 전에는 false 유지(구매 버튼 비활성) */
  premiumEnabled: false,
  /** 예정 가격(확정 아님) */
  premiumPriceKRW: 19900,
  /** 예정 무료 이용 한도(하루 문제 수, 확정 아님) */
  freeDailyLimitPlanned: 100,

  /** 개인정보 처리방침·이용약관 시행일(YYYY-MM-DD). 실제 게시일로 입력 */
  privacyEffectiveDate: '',
  termsEffectiveDate: '',

  /** Firestore 데이터베이스 위치(Firebase Console 에서 만든 값, 예: asia-northeast3 = 서울). 개인정보 처리방침 국외이전 항목에 표시 */
  firestoreLocation: '',
  /** 고객지원 피드백 보유기간 문구. 비우면 "이용자가 삭제할 때까지"로 표시 */
  feedbackRetention: '',
} as const;

export const contactEmail = () => site.supportEmail || '';
export const privacyEmail = () => site.privacyContactEmail || site.supportEmail || '';
export const operatorName = () => site.developerDisplayName || '';
export const isReleased = () => site.releaseStatus === 'released' && !!site.googlePlayUrl;

/** 필수 운영 정보 중 비어 있는 항목(공개 전 확인용) */
export function missingConfig(): string[] {
  const m: string[] = [];
  if (!site.supportEmail) m.push('supportEmail(고객지원 이메일)');
  if (!site.developerDisplayName) m.push('developerDisplayName(운영자 표시명)');
  if (!site.developerLegalName) m.push('developerLegalName(운영자 법적 명칭)');
  if (!site.privacyEffectiveDate) m.push('privacyEffectiveDate(개인정보 처리방침 시행일)');
  if (!site.termsEffectiveDate) m.push('termsEffectiveDate(이용약관 시행일)');
  if (!site.firestoreLocation) m.push('firestoreLocation(Firestore 위치)');
  return m;
}

export function formatDate(iso: string): string {
  if (!iso) return '';
  const [y, mo, d] = iso.split('-').map(Number);
  return `${y}년 ${mo}월 ${d}일`;
}

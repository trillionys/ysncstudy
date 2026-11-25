// @ts-check
import { defineConfig } from 'astro/config';

/**
 * 사이트 주소: PUBLIC_SITE_URL(직접 지정) → Netlify 빌드가 넣어 주는 URL(실제 사이트 주소) → 로컬 주소 순.
 * canonical 주소·sitemap.xml·Open Graph 이미지 주소에 쓰입니다.
 */
const site = process.env.PUBLIC_SITE_URL || process.env.URL || 'http://localhost:4321';

export default defineConfig({
  site,
  output: 'static',
  outDir: process.env.ASTRO_OUT_DIR || 'dist', // e2e 테스트는 dist-e2e 에 따로 빌드
  trailingSlash: 'never',
  build: { format: 'file' }, // /privacy → privacy.html (Netlify 가 확장자 없는 주소로 제공)
  compressHTML: true,
});

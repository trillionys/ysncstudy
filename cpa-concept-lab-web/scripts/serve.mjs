// 정적 빌드 결과를 Netlify 와 같은 방식으로 제공하는 테스트용 서버
//  - /privacy → privacy.html (확장자 없는 주소), 없는 주소 → 404.html(상태 404)
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';

const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.webp': 'image/webp', '.woff2': 'font/woff2', '.xml': 'application/xml', '.txt': 'text/plain; charset=utf-8', '.json': 'application/json' };

export function serve(root, port) {
  const exists = async (p) => (await stat(p).catch(() => null))?.isFile();
  const server = createServer(async (req, res) => {
    const path = decodeURIComponent(new URL(req.url, 'http://x').pathname);
    const safe = normalize(path).replace(/^(\.\.[/\\])+/, '');
    const candidates = safe.endsWith('/') ? [join(root, safe, 'index.html')] : [join(root, safe), join(root, `${safe}.html`)];
    for (const c of candidates) {
      if (await exists(c)) {
        res.writeHead(200, { 'Content-Type': TYPES[extname(c)] ?? 'application/octet-stream' });
        return res.end(await readFile(c));
      }
    }
    res.writeHead(404, { 'Content-Type': TYPES['.html'] });
    res.end(await readFile(join(root, '404.html')));
  });
  return new Promise((r) => server.listen(port, '127.0.0.1', () => r(server)));
}

if (import.meta.url === `file://${process.argv[1]}` && process.argv[2]) serve(process.argv[2], Number(process.argv[3] || 4321)).then(() => console.log('serving', process.argv[2]));

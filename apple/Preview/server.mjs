import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, extname, relative } from 'node:path';
const root = dirname(fileURLToPath(import.meta.url));
const port = Number(process.env.PORT || process.argv[process.argv.indexOf('--port') + 1]) || 4173;
const types = {'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.mjs':'text/javascript; charset=utf-8','.json':'application/json; charset=utf-8','.png':'image/png'};
const server = createServer(async (request, response) => {
  try {
    if (request.method !== 'GET' && request.method !== 'HEAD') { response.writeHead(405); response.end(); return; }
    const path = decodeURIComponent(new URL(request.url, `http://127.0.0.1:${port}`).pathname);
    const file = path === '/Fixtures/demo.json' ? resolve(root, '../Fixtures/demo.json') : resolve(root, `.${path === '/' ? '/index.html' : path}`);
    if (path !== '/Fixtures/demo.json' && (relative(root, file).startsWith('..') || !types[extname(file)])) { response.writeHead(403); response.end('Forbidden'); return; }
    const content = await readFile(file);
    response.writeHead(200, {'Content-Type': types[extname(file)] || 'application/octet-stream', 'Cache-Control':'no-store', 'X-Content-Type-Options':'nosniff', 'Content-Security-Policy':"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'"});
    response.end(request.method === 'HEAD' ? undefined : content);
  } catch { response.writeHead(404); response.end('Not found'); }
});
server.listen(port, '127.0.0.1', () => console.log(`Classing Apple interaction preview: http://127.0.0.1:${port} (not native SwiftUI)`));

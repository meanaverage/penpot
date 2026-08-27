import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import { extname, resolve, sep } from 'node:path';
import process from 'node:process';
import { URL } from 'node:url';

const options = parseArguments(process.argv.slice(2));
const root = resolve(options.root);
const mimeTypes = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.svg', 'image/svg+xml'],
]);

const server = createServer(async (request, response) => {
  response.setHeader('Access-Control-Allow-Origin', '*');
  response.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
  response.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  response.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');
  response.setHeader('Cache-Control', 'no-store');

  if (request.method === 'OPTIONS') {
    response.writeHead(204).end();
    return;
  }
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    response.writeHead(405).end('Method not allowed');
    return;
  }

  try {
    const url = new URL(request.url ?? '/', 'http://localhost');
    const pathname = decodeURIComponent(url.pathname);
    const relative = pathname === '/' ? 'index.html' : pathname.slice(1);
    const filename = resolve(root, relative);
    if (filename !== root && !filename.startsWith(`${root}${sep}`)) {
      response.writeHead(403).end('Forbidden');
      return;
    }
    const file = await stat(filename);
    if (!file.isFile()) throw new Error('Not a file');
    response.setHeader(
      'Content-Type',
      mimeTypes.get(extname(filename)) ?? 'application/octet-stream',
    );
    response.setHeader('Content-Length', file.size);
    response.writeHead(200);
    if (request.method === 'HEAD') response.end();
    else createReadStream(filename).pipe(response);
  } catch {
    response.writeHead(404).end('Not found');
  }
});

server.listen(options.port, options.host, () => {
  process.stdout.write(
    `SayHi importer preview serving ${root} at http://${options.host}:${options.port}\n`,
  );
});

function parseArguments(args) {
  const result = {
    root: '../../dist/apps/sayhi-component-importer-plugin',
    host: '127.0.0.1',
    port: 4189,
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    const value = args[index + 1];
    if (key === '--root' && value) result.root = value;
    if (key === '--host' && value) result.host = value;
    if (key === '--port' && value) result.port = Number(value);
    if (key?.startsWith('--')) index += 1;
  }
  if (
    !Number.isInteger(result.port) ||
    result.port < 1 ||
    result.port > 65535
  ) {
    throw new Error('Preview port must be an integer between 1 and 65535.');
  }
  return result;
}

// Same-origin proxy to the backend gateway, the Cloudflare Worker equivalent
// of the /api/ location in nginx.conf.template. Strips the /api prefix so the
// gateway sees the same paths it gets in local dev. Response bodies are
// streamed through, so SSE (chat, notifications) keeps working.

interface Env {
  UPSTREAM_GATEWAY: string;
}

export async function proxyApi(request: Request, env: Env): Promise<Response> {
  const upstream = env.UPSTREAM_GATEWAY?.replace(/\/+$/, '');
  if (!upstream) {
    return new Response('UPSTREAM_GATEWAY is not configured', { status: 500 });
  }

  const url = new URL(request.url);
  const target = upstream + url.pathname.replace(/^\/api/, '') + url.search;

  const headers = new Headers(request.headers);
  headers.set('X-Forwarded-Host', url.host);
  headers.set('X-Forwarded-Proto', url.protocol.replace(':', ''));
  const clientIp = request.headers.get('CF-Connecting-IP');
  if (clientIp) {
    headers.set('X-Real-IP', clientIp);
    headers.set('X-Forwarded-For', clientIp);
  }

  return fetch(target, {
    method: request.method,
    headers,
    body: request.method === 'GET' || request.method === 'HEAD' ? undefined : request.body,
    redirect: 'manual',
  });
}

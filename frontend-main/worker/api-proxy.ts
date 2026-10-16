// Same-origin proxy to the backend gateway, the Cloudflare Worker equivalent
// of the /api/ location in nginx.conf.template. Strips the /api prefix so the
// gateway sees the same paths it gets in local dev. Response bodies are
// streamed through, so SSE (chat, notifications) keeps working.

interface Env {
  UPSTREAM_GATEWAY: string;
}

// The value shipped in wrangler.jsonc until a real backend URL is configured.
const PLACEHOLDER_HOST = 'api.example.com';

/** RFC 7807 body in the shape the SPA's parseProblemDetail expects. */
function backendUnavailable(detail: string): Response {
  const body = {
    type: 'about:blank',
    title: 'Service unavailable',
    status: 503,
    code: 'BACKEND_UNAVAILABLE',
    detail,
    correlationId: crypto.randomUUID(),
    timestamp: new Date().toISOString(),
  };
  return new Response(JSON.stringify(body), {
    status: 503,
    headers: { 'Content-Type': 'application/problem+json', 'Cache-Control': 'no-store' },
  });
}

export async function proxyApi(request: Request, env: Env): Promise<Response> {
  const upstream = env.UPSTREAM_GATEWAY?.replace(/\/+$/, '');
  if (!upstream) {
    return new Response('UPSTREAM_GATEWAY is not configured', { status: 500 });
  }
  let upstreamHost = '';
  try {
    upstreamHost = new URL(upstream).hostname;
  } catch {
    /* handled below as unavailable */
  }
  // Fail fast with a readable message instead of Cloudflare's raw error page.
  if (!upstreamHost || upstreamHost === PLACEHOLDER_HOST) {
    return backendUnavailable('The school portal server is not connected yet. Please try again later or contact the school office.');
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

  let res: Response;
  try {
    res = await fetch(target, {
      method: request.method,
      headers,
      body: request.method === 'GET' || request.method === 'HEAD' ? undefined : request.body,
      redirect: 'manual',
    });
  } catch {
    return backendUnavailable('Could not reach the school portal server. Please try again in a moment.');
  }
  // 520-530 are Cloudflare's own "origin unreachable / DNS / timeout" errors, not answers from the backend.
  if (res.status >= 520 && res.status <= 530) {
    return backendUnavailable('The school portal server is not responding. Please try again in a moment.');
  }
  return res;
}

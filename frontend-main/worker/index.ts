// Worker entry: routes the few server-side paths, everything else is a static
// asset from dist/ (with index.html as the SPA fallback, see wrangler.jsonc).

import { proxyApi } from './api-proxy';
import { handleEnquiry } from './enquiry';
import { robots } from './robots';
import { sitemap } from './sitemap';

export interface Env {
  ASSETS: Fetcher;
  UPSTREAM_GATEWAY: string;
  ENQUIRY_WEBHOOK_URL?: string;
  ENQUIRY_WEBHOOK_TOKEN?: string;
}

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url);

    if (pathname === '/api' || pathname.startsWith('/api/')) return proxyApi(request, env);
    if (pathname === '/forms/enquiry') {
      if (request.method !== 'POST') return new Response('Method Not Allowed', { status: 405, headers: { Allow: 'POST' } });
      return handleEnquiry(request, env);
    }
    if (pathname === '/robots.txt' && request.method === 'GET') return robots(request);
    if (pathname === '/sitemap.xml' && request.method === 'GET') return sitemap(request);

    return env.ASSETS.fetch(request);
  },
} satisfies ExportedHandler<Env>;

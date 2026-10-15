// GET /robots.txt — allow the public website, keep crawlers out of the
// staff portal and API.

const DISALLOW = ['/api/', '/forms/', '/login', '/change-password', '/visit'];

export const onRequestGet: PagesFunction = ({ request }) => {
  const origin = new URL(request.url).origin;
  const body = ['User-agent: *', ...DISALLOW.map((p) => `Disallow: ${p}`), '', `Sitemap: ${origin}/sitemap.xml`, ''].join(
    '\n',
  );
  return new Response(body, {
    headers: { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'public, max-age=3600' },
  });
};

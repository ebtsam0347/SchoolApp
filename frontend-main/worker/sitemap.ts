// GET /sitemap.xml — public website pages, absolute URLs built from the
// request origin so it is correct on every deployment. Keep in sync with
// the routes under src/routes/_site/.

const PAGES = [
  '/home',
  '/our-philosophy',
  '/learning-approach',
  '/programmes',
  '/a-day-at-school',
  '/admissions',
  '/contact',
  '/privacy',
  '/accessibility',
];

export function sitemap(request: Request): Response {
  const origin = new URL(request.url).origin;
  const urls = PAGES.map((p) => `  <url><loc>${origin}${p}</loc></url>`).join('\n');
  const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`;
  return new Response(xml, {
    headers: { 'Content-Type': 'application/xml; charset=utf-8', 'Cache-Control': 'public, max-age=3600' },
  });
}

# Deploying The Spirit of Islam School portal to Cloudflare Pages

The SPA is served by Cloudflare Pages; `/api/*` is proxied by the Pages Function
in `functions/api/[[path]].ts` to the backend gateway (same as `nginx.conf.template`).

The Java backend (gateway + services + Postgres) cannot run on Cloudflare Pages.
Host it on a server/VPS and expose the gateway over HTTPS (directly, or with a
Cloudflare Tunnel: `cloudflared tunnel --url http://localhost:8080`).

## One-time setup
1. `npx wrangler login`
2. Set `UPSTREAM_GATEWAY` in `wrangler.toml` to the gateway's public URL
   (or in the dashboard: Pages project -> Settings -> Variables and Secrets).

## Deploy from the CLI
    npm run deploy

## Or deploy from Git (dashboard)
Workers & Pages -> Create -> Pages -> Connect to Git, then:
- Root directory: `frontend-main`
- Build command: `npm run build`
- Build output directory: `dist`
- Environment variable: `UPSTREAM_GATEWAY=https://<your-gateway>`

## Local test with the Cloudflare runtime
    npm run cf:dev

## Public website (enquiry form, sitemap)

The public school website lives at `/home`, `/our-philosophy`, `/learning-approach`,
`/programmes`, `/a-day-at-school`, `/admissions`, `/contact`, `/privacy` and
`/accessibility` (source: `src/routes/_site/`, `src/site/`). School details such as
phone, email, address and social links are edited in `src/site/config.ts`.

The admissions/contact form posts to the Pages Function `functions/forms/enquiry.ts`,
which validates the submission and forwards it to the school's form service:

| Variable | Type | Purpose |
| --- | --- | --- |
| `ENQUIRY_WEBHOOK_URL` | Secret (required) | HTTPS endpoint that receives each enquiry as JSON (CRM, Google Apps Script, Zapier/Make, email relay…) |
| `ENQUIRY_WEBHOOK_TOKEN` | Secret (optional) | Sent as `Authorization: Bearer <token>` |
| `VITE_SITE_URL` | Build variable (optional) | Public origin used in canonical / Open Graph URLs |

Until `ENQUIRY_WEBHOOK_URL` is set, the form tells visitors their enquiry was **not**
sent; it never shows a false success. `/sitemap.xml` and `/robots.txt` are served by
functions and use the request origin automatically.

# Deploying The Spirit of Islam School portal to Cloudflare Workers

The SPA is built to `dist/` and served as Workers static assets; `/api/*` is
proxied by the Worker in `worker/api-proxy.ts` to the backend gateway (same as
`nginx.conf.template`). Config lives in `wrangler.jsonc`; its `build.command`
runs `npm run build` before every deploy, so the source is never uploaded as-is.

The Java backend (gateway + services + Postgres) cannot run on Cloudflare Workers.
Host it on a server/VPS and expose the gateway over HTTPS (directly, or with a
Cloudflare Tunnel: `cloudflared tunnel --url http://localhost:8080`).

## One-time setup
1. `npx wrangler login`
2. Set `UPSTREAM_GATEWAY` in `wrangler.jsonc` to the gateway's public URL
   (or in the dashboard: Worker -> Settings -> Variables and Secrets).

## Deploy from the CLI
    npm run deploy

## Or deploy from Git (dashboard)
Workers & Pages -> the `schoolapp` Worker -> Settings -> Build, then:
- Root directory: `frontend-main`
- Build command: leave empty (wrangler runs `npm run build` itself)
- Deploy command: `npx wrangler deploy`
- Variable: `UPSTREAM_GATEWAY=https://<your-gateway>`

## Local test with the Cloudflare runtime
    npm run cf:dev

## Public website (enquiry form, sitemap)

The public school website lives at `/home`, `/our-philosophy`, `/learning-approach`,
`/programmes`, `/a-day-at-school`, `/admissions`, `/contact`, `/privacy` and
`/accessibility` (source: `src/routes/_site/`, `src/site/`). School details such as
phone, email, address and social links are edited in `src/site/config.ts`.

The admissions/contact form posts to the Worker route in `worker/enquiry.ts`,
which validates the submission and forwards it to the school's form service:

| Variable | Type | Purpose |
| --- | --- | --- |
| `ENQUIRY_WEBHOOK_URL` | Secret (required) | HTTPS endpoint that receives each enquiry as JSON (CRM, Google Apps Script, Zapier/Make, email relay…) |
| `ENQUIRY_WEBHOOK_TOKEN` | Secret (optional) | Sent as `Authorization: Bearer <token>` |
| `VITE_SITE_URL` | Build variable (optional) | Public origin used in canonical / Open Graph URLs |

Until `ENQUIRY_WEBHOOK_URL` is set, the form tells visitors their enquiry was **not**
sent; it never shows a false success. `/sitemap.xml` and `/robots.txt` are served by
the Worker and use the request origin automatically.

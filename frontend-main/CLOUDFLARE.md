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
   (editing it in the dashboard is overwritten by the next deploy, so change `wrangler.jsonc`).

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

## Website images (Gallery admin)

Admins manage the photos on the public website from the staff portal, signed in
with the normal admin login (role `ADMIN`):

- **Admin → Website images → Page images** — pick a page and section, then choose a
  gallery image or upload a new one and publish it.
- **Admin → Website images → Gallery** — upload, search, edit details, replace,
  reorder and delete images.

How it works (all in the Worker, `worker/media.ts`):

| Piece | Where |
| --- | --- |
| Image files | Workers KV namespace `schoolapp-media` (binding `MEDIA`), free tier: 1 GB, 1,000 writes/day, 100,000 reads/day |
| Titles, alt text, categories, order, which slot shows which image | D1 database `schoolapp-media` (binding `DB`); tables are created automatically by `worker/schema.ts` |
| Who may change images | Worker verifies the backend's RS256 access token against `<UPSTREAM_GATEWAY>/auth/.well-known/jwks.json` and requires the `ADMIN` role on every `/media/admin/*` call |
| Public site | `GET /media/slots` (cached ~30 s) tells each `PhotoSlot` which image to show; files at `/media/file/<id>.<ext>` are immutable and cached for a year (a replacement gets a new URL) |

Slots (page, section, label) are listed in `src/site/photo-slots.ts` and must match
the keys in `src/site/content.ts`. Uploads: JPG, PNG, WebP up to 5 MB, validated by file
content, stored under generated names.

Setup (once): create the KV namespace and the D1 database, put their ids in
`wrangler.jsonc`, then deploy. (Both are on Cloudflare's free plan; no card needed.)

    npx wrangler kv namespace create schoolapp-media
    npx wrangler d1 create schoolapp-media
    npm run deploy

### Photo-only sign-in (`/photos`, no backend needed)

`https://<your-site>/photos` has its own sign-in for managing website photos, so it
works even while the school portal backend is offline. It is protected by one secret,
the photo password. Set it once in Cloudflare (Workers & Pages → schoolapp → Settings →
Variables and Secrets → Add → type *Secret*, name `GALLERY_PASSWORD`) or with
`npx wrangler secret put GALLERY_PASSWORD`. Until it is set, `/photos` shows
"Photo sign-in is not set up yet".

- Sessions last 8 hours; changing the secret signs everyone out.
- 5 wrong passwords from one address lock that address out for 10 minutes.
- Choose a long password (a few random words). Anyone who has it can change the website photos.
- The portal's `ADMIN` login keeps working for Admin → Website images.

Optional Worker variables: `JWKS_URL` / `JWKS_JSON` (override where the signing key
comes from), `JWT_ISSUER`, `JWT_AUDIENCE` (extra token checks). Admin sign-in needs
`UPSTREAM_GATEWAY` to point at the running backend. Until it does, the public site
still works and simply shows the default illustrations.

Tests: `npm run test:worker` (auth, validation, upload, replace, delete, permissions)

KV is eventually consistent, so a just-published image can take up to a minute to appear
in every region; the site shows the default illustration until then..

## Public website (enquiry form, sitemap)

The public school website lives at `/home`, `/our-philosophy`, `/learning-approach`,
`/programmes`, `/a-day-at-school`, `/admissions`, `/contact`, `/privacy` and
`/accessibility` (source: `src/routes/_site/`, `src/site/`). School details such as
phone, email, address and social links are edited in `src/site/config.ts`.

The public site is available in English and Urdu. On the first visit it asks which
language to use; the header has a switch to change it later. Every visible string goes
through `tr()` (`src/site/i18n.ts`) and its Urdu text lives in `src/site/i18n-ur.ts`,
keyed by the exact English. When you change or add English copy, add the Urdu entry too:
`npm run test:worker` fails if any string is missing a translation.

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

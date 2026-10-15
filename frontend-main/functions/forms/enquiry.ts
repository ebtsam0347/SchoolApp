// POST /forms/enquiry — website admissions / contact enquiries.
//
// Validates the submission and forwards it as JSON to the school's form
// service (any HTTPS webhook: a CRM, Google Apps Script, Zapier/Make, an
// email relay…). Nothing is stored here and nothing is exposed publicly.
//
// Configure in Cloudflare Pages → Settings → Variables and Secrets:
//   ENQUIRY_WEBHOOK_URL    (secret, required) — HTTPS endpoint to receive enquiries
//   ENQUIRY_WEBHOOK_TOKEN  (secret, optional) — sent as "Authorization: Bearer <token>"
//
// Without ENQUIRY_WEBHOOK_URL the endpoint answers 503 { error: "not_configured" }
// and the website tells the visitor the message was not sent.

interface Env {
  ENQUIRY_WEBHOOK_URL?: string;
  ENQUIRY_WEBHOOK_TOKEN?: string;
}

const TYPES = ['admissions', 'visit', 'general'];
const METHODS = ['email', 'phone', 'whatsapp'];
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE = /^[+\d][\d\s-]{6,18}$/;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' },
  });
}

function str(v: unknown, max: number): string | null {
  if (typeof v !== 'string') return null;
  const t = v.trim();
  return t.length > 0 && t.length <= max ? t : null;
}

export const onRequestPost: PagesFunction<Env> = async ({ request, env }) => {
  const webhook = env.ENQUIRY_WEBHOOK_URL;
  if (!webhook || !webhook.startsWith('https://')) {
    return json({ ok: false, error: 'not_configured' }, 503);
  }

  if (!request.headers.get('Content-Type')?.includes('application/json')) {
    return json({ ok: false, error: 'invalid' }, 415);
  }
  const raw = await request.text();
  if (raw.length > 10_000) return json({ ok: false, error: 'invalid' }, 413);

  let body: Record<string, unknown>;
  try {
    body = JSON.parse(raw);
  } catch {
    return json({ ok: false, error: 'invalid' }, 400);
  }

  // Honeypot filled → pretend success to the bot, forward nothing.
  if (typeof body.website === 'string' && body.website.length > 0) return json({ ok: true });

  const parentName = str(body.parentName, 100);
  const email = str(body.email, 200);
  const phone = body.phone ? str(body.phone, 30) : null;
  const message = body.message ? str(body.message, 1000) : null;
  const ageGroup = body.ageGroup ? str(body.ageGroup, 40) : null;
  const enquiryType = typeof body.enquiryType === 'string' && TYPES.includes(body.enquiryType) ? body.enquiryType : null;
  const contactMethod =
    typeof body.contactMethod === 'string' && METHODS.includes(body.contactMethod) ? body.contactMethod : null;

  if (
    !parentName ||
    !email ||
    !EMAIL.test(email) ||
    (phone && !PHONE.test(phone)) ||
    !enquiryType ||
    !contactMethod ||
    (contactMethod !== 'email' && !phone) ||
    body.consent !== true
  ) {
    return json({ ok: false, error: 'invalid' }, 422);
  }

  const payload = {
    source: 'website',
    receivedAt: new Date().toISOString(),
    enquiryType,
    parentName,
    email,
    phone,
    ageGroup,
    contactMethod,
    message,
    consent: true,
  };

  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (env.ENQUIRY_WEBHOOK_TOKEN) headers.Authorization = `Bearer ${env.ENQUIRY_WEBHOOK_TOKEN}`;

  try {
    const res = await fetch(webhook, { method: 'POST', headers, body: JSON.stringify(payload) });
    if (!res.ok) return json({ ok: false, error: 'upstream' }, 502);
  } catch {
    return json({ ok: false, error: 'upstream' }, 502);
  }
  return json({ ok: true });
};

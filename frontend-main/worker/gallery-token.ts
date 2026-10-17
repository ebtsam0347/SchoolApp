// Session tokens for the standalone photo-admin login (`/photos`), used while
// the school portal backend is not connected, or by anyone who only manages
// website photos. The only secret is the Worker secret GALLERY_PASSWORD:
// tokens are HMAC-signed with a key derived from it, so changing the password
// signs everyone out. Nothing is stored server side.
//
// Token: g1.<base64url(JSON {exp})>.<base64url(HMAC-SHA256)>

export const GALLERY_TOKEN_PREFIX = 'g1.';
export const GALLERY_TOKEN_TTL_MS = 8 * 60 * 60 * 1000;

const enc = new TextEncoder();

function b64url(bytes: ArrayBuffer | Uint8Array): string {
  return btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function fromB64url(s: string): Uint8Array {
  const pad = s.length % 4 === 0 ? '' : '='.repeat(4 - (s.length % 4));
  return Uint8Array.from(atob((s + pad).replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));
}

async function hmacKey(password: string): Promise<CryptoKey> {
  const material = await crypto.subtle.digest('SHA-256', enc.encode(`gallery-session|${password}`));
  return crypto.subtle.importKey('raw', material, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign', 'verify']);
}

/** Constant-time password check (compares fixed-length digests). */
export async function passwordMatches(candidate: string, expected: string): Promise<boolean> {
  const [a, b] = await Promise.all([crypto.subtle.digest('SHA-256', enc.encode(candidate)), crypto.subtle.digest('SHA-256', enc.encode(expected))]);
  const x = new Uint8Array(a);
  const y = new Uint8Array(b);
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
  return diff === 0;
}

export async function issueGalleryToken(password: string, now = Date.now()): Promise<{ token: string; expiresAt: string }> {
  const exp = now + GALLERY_TOKEN_TTL_MS;
  const payload = b64url(enc.encode(JSON.stringify({ exp })));
  const sig = await crypto.subtle.sign('HMAC', await hmacKey(password), enc.encode(payload));
  return { token: `${GALLERY_TOKEN_PREFIX}${payload}.${b64url(sig)}`, expiresAt: new Date(exp).toISOString() };
}

/** True only for an unexpired token signed with the current password. */
export async function verifyGalleryToken(token: string, password: string, now = Date.now()): Promise<boolean> {
  if (!token.startsWith(GALLERY_TOKEN_PREFIX)) return false;
  const [payload, sig, extra] = token.slice(GALLERY_TOKEN_PREFIX.length).split('.');
  if (!payload || !sig || extra !== undefined) return false;
  try {
    const ok = await crypto.subtle.verify('HMAC', await hmacKey(password), fromB64url(sig), enc.encode(payload));
    if (!ok) return false;
    const { exp } = JSON.parse(new TextDecoder().decode(fromB64url(payload))) as { exp?: number };
    return typeof exp === 'number' && exp > now;
  } catch {
    return false;
  }
}

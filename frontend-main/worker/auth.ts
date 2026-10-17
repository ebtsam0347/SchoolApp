import { GALLERY_TOKEN_PREFIX, verifyGalleryToken } from './gallery-token';

// Verifies the backend's access tokens (RS256 JWTs issued by the auth service)
// so website-image management reuses the existing admin login. Nothing is
// issued here: the Worker only checks signature, expiry and the ADMIN role.
//
// Public keys come from the auth service's JWKS endpoint
// (<UPSTREAM_GATEWAY>/auth/.well-known/jwks.json, public via the gateway) or,
// if set, from JWKS_JSON / JWKS_URL.

export interface AuthEnv {
  UPSTREAM_GATEWAY?: string;
  /** Optional override: full JWKS URL. */
  JWKS_URL?: string;
  /** Optional override: pinned JWKS document, used instead of any fetch. */
  JWKS_JSON?: string;
  /** Optional: reject tokens whose `iss` differs. */
  JWT_ISSUER?: string;
  /** Optional: reject tokens whose `aud` does not include this value. */
  JWT_AUDIENCE?: string;
  /** Secret for the standalone photo-admin login (/photos). Unset = that login is disabled. */
  GALLERY_PASSWORD?: string;
}

export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

export type Admin = { username: string; roles: string[] };

type Jwk = JsonWebKey & { kid?: string };

const KEY_TTL_MS = 10 * 60_000;
const MISS_REFETCH_MS = 60_000;
let cache: { keys: Map<string, CryptoKey>; fetchedAt: number } | null = null;
let lastRefetchAt = 0;

export function resetKeyCache(): void {
  cache = null;
  lastRefetchAt = 0;
}

function b64urlToBytes(s: string): Uint8Array {
  const pad = s.length % 4 === 0 ? '' : '='.repeat(4 - (s.length % 4));
  const bin = atob((s + pad).replace(/-/g, '+').replace(/_/g, '/'));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

function decodeJson<T>(part: string): T {
  return JSON.parse(new TextDecoder().decode(b64urlToBytes(part))) as T;
}

async function loadJwks(env: AuthEnv): Promise<Jwk[]> {
  let doc: { keys?: Jwk[] };
  if (env.JWKS_JSON) {
    doc = JSON.parse(env.JWKS_JSON);
  } else {
    const url =
      env.JWKS_URL ?? (env.UPSTREAM_GATEWAY ? `${env.UPSTREAM_GATEWAY.replace(/\/+$/, '')}/auth/.well-known/jwks.json` : '');
    if (!url) throw new HttpError(503, 'auth_not_configured', 'Admin sign-in is not configured on this site.');
    let res: Response;
    try {
      res = await fetch(url, { headers: { Accept: 'application/json' } });
    } catch {
      throw new HttpError(503, 'auth_unavailable', 'Could not reach the sign-in service.');
    }
    if (!res.ok) throw new HttpError(503, 'auth_unavailable', 'Could not load the sign-in keys.');
    doc = (await res.json()) as { keys?: Jwk[] };
  }
  return (doc.keys ?? []).filter((k) => k.kty === 'RSA');
}

async function importKeys(jwks: Jwk[]): Promise<Map<string, CryptoKey>> {
  const out = new Map<string, CryptoKey>();
  for (const jwk of jwks) {
    const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
    out.set(jwk.kid ?? '', key);
  }
  return out;
}

async function keyFor(kid: string | undefined, env: AuthEnv): Promise<CryptoKey | null> {
  const now = Date.now();
  const stale = !cache || now - cache.fetchedAt > KEY_TTL_MS;
  if (stale) {
    cache = { keys: await importKeys(await loadJwks(env)), fetchedAt: now };
  }
  const found = cache!.keys.get(kid ?? '') ?? (cache!.keys.size === 1 && !kid ? [...cache!.keys.values()][0] : undefined);
  if (found) return found;
  // Unknown kid: the signing key may have rotated. Refetch, but rate-limited.
  if (now - lastRefetchAt > MISS_REFETCH_MS) {
    lastRefetchAt = now;
    cache = { keys: await importKeys(await loadJwks(env)), fetchedAt: now };
    return cache.keys.get(kid ?? '') ?? null;
  }
  return null;
}

/**
 * Accepts either the backend's ADMIN access token or a photo-admin session
 * token from POST /media/login, and returns who is acting.
 */
export async function requireAdmin(request: Request, env: AuthEnv): Promise<Admin> {
  const header = request.headers.get('Authorization') ?? '';
  const match = /^Bearer\s+(\S+)$/i.exec(header);
  if (!match) throw new HttpError(401, 'unauthenticated', 'Sign in to manage website images.');

  if (match[1].startsWith(GALLERY_TOKEN_PREFIX)) {
    if (!env.GALLERY_PASSWORD || !(await verifyGalleryToken(match[1], env.GALLERY_PASSWORD))) {
      throw new HttpError(401, 'invalid_token', 'Your photo session has expired. Sign in again.');
    }
    return { username: 'photo-admin', roles: ['ADMIN'] };
  }

  const parts = match[1].split('.');
  if (parts.length !== 3) throw new HttpError(401, 'invalid_token', 'Invalid access token.');

  let head: { alg?: string; kid?: string };
  let claims: {
    exp?: number;
    nbf?: number;
    iss?: string;
    aud?: string | string[];
    username?: string;
    sub?: string;
    roles?: unknown;
    tokenType?: string;
    pwChangeRequired?: boolean;
  };
  try {
    head = decodeJson(parts[0]);
    claims = decodeJson(parts[1]);
  } catch {
    throw new HttpError(401, 'invalid_token', 'Invalid access token.');
  }
  if (head.alg !== 'RS256') throw new HttpError(401, 'invalid_token', 'Invalid access token.');

  const key = await keyFor(head.kid, env);
  if (!key) throw new HttpError(401, 'invalid_token', 'Invalid access token.');
  const ok = await crypto.subtle.verify(
    'RSASSA-PKCS1-v1_5',
    key,
    b64urlToBytes(parts[2]),
    new TextEncoder().encode(`${parts[0]}.${parts[1]}`),
  );
  if (!ok) throw new HttpError(401, 'invalid_token', 'Invalid access token.');

  const nowSec = Date.now() / 1000;
  if (typeof claims.exp !== 'number' || claims.exp <= nowSec) throw new HttpError(401, 'token_expired', 'Your session has expired.');
  if (typeof claims.nbf === 'number' && claims.nbf > nowSec + 30) throw new HttpError(401, 'invalid_token', 'Invalid access token.');
  if (env.JWT_ISSUER && claims.iss !== env.JWT_ISSUER) throw new HttpError(401, 'invalid_token', 'Invalid access token.');
  if (env.JWT_AUDIENCE) {
    const aud = Array.isArray(claims.aud) ? claims.aud : claims.aud ? [claims.aud] : [];
    if (!aud.includes(env.JWT_AUDIENCE)) throw new HttpError(401, 'invalid_token', 'Invalid access token.');
  }
  if (claims.tokenType === 'service' || claims.pwChangeRequired) {
    throw new HttpError(403, 'forbidden', 'This account cannot manage website images.');
  }

  const roles = Array.isArray(claims.roles) ? claims.roles.filter((r): r is string => typeof r === 'string') : [];
  if (!roles.includes('ADMIN')) throw new HttpError(403, 'forbidden', 'Only administrators can manage website images.');

  return { username: claims.username ?? claims.sub ?? 'admin', roles };
}

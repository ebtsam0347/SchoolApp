// Read the `exp` claim from a JWT as a millisecond epoch.
//
// We rely on this instead of the server-supplied `accessTokenExpiresAt`
// string because the auth service serializes Java `LocalDateTime` in
// server-local time (Asia/Karachi) without a zone marker. Treating that
// value as UTC pushes the computed expiry 5 hours into the future, so
// silent-refresh fires too late and 403s on dead tokens aren't recognized
// as "needs refresh". The JWT `exp` claim is unambiguous (epoch seconds).

function base64UrlDecode(s: string): string {
  const pad = s.length % 4 === 0 ? '' : '='.repeat(4 - (s.length % 4));
  const b64 = (s + pad).replace(/-/g, '+').replace(/_/g, '/');
  if (typeof atob === 'function') return atob(b64);
  return Buffer.from(b64, 'base64').toString('binary');
}

export function readJwtExpMs(token: string | undefined | null): number | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length < 2) return null;
  try {
    const json = base64UrlDecode(parts[1]);
    const payload = JSON.parse(json) as { exp?: number };
    if (typeof payload.exp !== 'number') return null;
    return payload.exp * 1000;
  } catch {
    return null;
  }
}

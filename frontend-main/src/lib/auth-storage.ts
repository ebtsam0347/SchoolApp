// Persists the access/refresh token pair + last-known user identity to
// localStorage so a page reload doesn't bounce the user back to /login.
//
// Tradeoffs documented in PLAN.md Phase 3: with no BFF in place yet, we can't
// use httpOnly cookies for the refresh token, so it lives in localStorage.
// Once the BFF lands and can set Set-Cookie on /auth/refresh, swap this layer
// to a memory-only access token + cookie-driven refresh.

import type { TokenResponse, UserIdentity } from '@/auth/types';

const KEY = 'fshrms.auth.v1';

export type AuthSnapshot = {
  tokens: TokenResponse;
  user: UserIdentity | null;
};

const isBrowser = typeof window !== 'undefined' && !!window.localStorage;

export function readSnapshot(): AuthSnapshot | null {
  if (!isBrowser) return null;
  const raw = window.localStorage.getItem(KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as AuthSnapshot;
    if (!parsed?.tokens?.accessToken || !parsed?.tokens?.refreshToken) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function writeSnapshot(snapshot: AuthSnapshot): void {
  if (!isBrowser) return;
  window.localStorage.setItem(KEY, JSON.stringify(snapshot));
  emit();
}

export function clearSnapshot(): void {
  if (!isBrowser) return;
  window.localStorage.removeItem(KEY);
  emit();
}

// Tiny pub/sub so AuthProvider can re-sync after another tab logs in/out and
// after the http client rotates tokens during a silent refresh.
type Listener = () => void;
const listeners = new Set<Listener>();

export function subscribe(listener: Listener): () => void {
  listeners.add(listener);
  if (isBrowser) {
    const onStorage = (e: StorageEvent) => {
      if (e.key === KEY) listener();
    };
    window.addEventListener('storage', onStorage);
    return () => {
      listeners.delete(listener);
      window.removeEventListener('storage', onStorage);
    };
  }
  return () => listeners.delete(listener);
}

function emit() {
  for (const l of listeners) l();
}

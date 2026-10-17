// Session for the standalone photo-admin sign-in (/photos). Independent of the
// school portal login: POST /media/login trades the photo password for a
// short-lived token (worker/gallery-token.ts) kept in localStorage. While a
// valid one exists the gallery API calls use it instead of the portal token.

import { useSyncExternalStore } from 'react';
import { parseProblemDetail } from '@/lib/api-error';

const KEY = 'school.photos.session.v1';

export type PhotoSession = { token: string; expiresAt: string };

const listeners = new Set<() => void>();
let cache: { raw: string | null; value: PhotoSession | null } = { raw: null, value: null };

function emit() {
  listeners.forEach((l) => l());
}

export function readPhotoSession(): PhotoSession | null {
  let raw: string | null = null;
  try {
    raw = window.localStorage.getItem(KEY);
  } catch {
    return null;
  }
  if (raw === cache.raw && (!cache.value || new Date(cache.value.expiresAt).getTime() > Date.now())) return cache.value;
  let value: PhotoSession | null = null;
  try {
    const parsed = raw ? (JSON.parse(raw) as PhotoSession) : null;
    if (parsed?.token && parsed.expiresAt && new Date(parsed.expiresAt).getTime() > Date.now()) value = parsed;
  } catch {
    value = null;
  }
  if (raw && !value) {
    try {
      window.localStorage.removeItem(KEY);
    } catch {
      /* ignore */
    }
    raw = null;
  }
  cache = { raw, value };
  return value;
}

export function clearPhotoSession(): void {
  try {
    window.localStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
  emit();
}

export function usePhotoSession(): PhotoSession | null {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      const onStorage = (e: StorageEvent) => e.key === KEY && listener();
      window.addEventListener('storage', onStorage);
      return () => {
        listeners.delete(listener);
        window.removeEventListener('storage', onStorage);
      };
    },
    readPhotoSession,
    () => null,
  );
}

/** Signs in with the photo password. Throws an ApiError (wrong password, locked out, not set up). */
export async function photoLogin(password: string): Promise<void> {
  const res = await fetch(`${window.location.origin}/media/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ password }),
  });
  if (!res.ok) throw await parseProblemDetail(res);
  const { token, expiresAt } = (await res.json()) as PhotoSession;
  window.localStorage.setItem(KEY, JSON.stringify({ token, expiresAt }));
  emit();
}

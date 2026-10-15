// fetch wrapper for the gateway at VITE_API_BASE.
//
// Responsibilities:
//   - Prefix paths with the configured base URL.
//   - Serialize JSON request bodies and parse JSON responses.
//   - Attach Authorization: Bearer <accessToken> from auth-storage.
//   - Stamp Idempotency-Key on unsafe methods (commons-web requires it for
//     POST/PUT/PATCH/DELETE; gateway 400s without one).
//   - Always throw an `ApiError` on non-2xx via parseProblemDetail so callers
//     see one shape regardless of how the server failed.
//   - Single-flight 401 → /auth/refresh retry, with an opt-out for the
//     refresh/login/logout calls themselves to avoid recursion. The gateway
//     returns 403 (not 401) for expired/invalid JWTs because commons-web's
//     JwtAuthenticationFilter leaves the request unauthenticated and Spring
//     Security has no auth entry point — so we also treat "403 + locally-
//     expired access token" as a refresh trigger.
//   - Hard request timeout (DEFAULT_TIMEOUT_MS) so a misbehaving backend
//     surfaces as an error, not a forever-spinning loader. Composed with the
//     caller's AbortSignal — whichever fires first cancels the request.

import { parseProblemDetail, ApiError } from './api-error';
import {
  readSnapshot,
  writeSnapshot,
  clearSnapshot,
} from './auth-storage';
import { readJwtExpMs } from './jwt';
import type { TokenResponse } from '@/auth/types';

const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? '';

/**
 * Default per-request timeout. Tuned for HRMS reads + writes:
 * - long enough for the slowest BFF composites under load,
 * - short enough that a hung upstream surfaces in the UI within ~30s.
 * Pass `timeoutMs: 0` in opts to disable for a specific call (e.g. SSE
 * streams that manage their own lifecycle).
 */
const DEFAULT_TIMEOUT_MS = 30_000;

// `crypto.randomUUID()` is gated to secure contexts (HTTPS / localhost /
// 127.0.0.1). In UAT we serve the SPA over plain HTTP at app.hrms.local,
// where calling it throws and the whole request never leaves the page —
// surfaces as a confusing "couldn't reach the server" toast despite zero
// network traffic. Prefer the native call when available, fall back to
// crypto.getRandomValues (which IS exposed on insecure contexts) and
// assemble an RFC 4122 v4 UUID by hand. Drop the fallback once the deploy
// gets a TLS cert.
function randomUuid(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const b = new Uint8Array(16);
  crypto.getRandomValues(b);
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, '0'));
  return `${h.slice(0, 4).join('')}-${h.slice(4, 6).join('')}-${h.slice(6, 8).join('')}-${h.slice(8, 10).join('')}-${h.slice(10, 16).join('')}`;
}

type Method = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';

type RequestOpts = {
  method?: Method;
  body?: unknown;
  headers?: Record<string, string>;
  /** Skip Authorization + auto-refresh. Used for /auth/login + /auth/refresh. */
  skipAuth?: boolean;
  /** AbortSignal for cancellation (TanStack Query passes one in). */
  signal?: AbortSignal;
  /** Override the default per-request timeout. Pass 0 to disable. */
  timeoutMs?: number;
};

/**
 * Combine the caller's signal with a timeout signal. The returned signal
 * fires (and `reason` is set) on whichever happens first.
 */
function combineSignals(
  caller: AbortSignal | undefined,
  timeoutMs: number,
): { signal: AbortSignal; cleanup: () => void } {
  if (timeoutMs <= 0) {
    return { signal: caller ?? new AbortController().signal, cleanup: () => {} };
  }
  const ctl = new AbortController();
  const timer = setTimeout(
    () => ctl.abort(new DOMException(`Request timed out after ${timeoutMs}ms`, 'TimeoutError')),
    timeoutMs,
  );
  const onCallerAbort = () => ctl.abort(caller!.reason);
  if (caller) {
    if (caller.aborted) ctl.abort(caller.reason);
    else caller.addEventListener('abort', onCallerAbort, { once: true });
  }
  return {
    signal: ctl.signal,
    cleanup: () => {
      clearTimeout(timer);
      caller?.removeEventListener('abort', onCallerAbort);
    },
  };
}

function url(path: string) {
  if (path.startsWith('http')) return path;
  if (!BASE) return path;
  return `${BASE}${path.startsWith('/') ? '' : '/'}${path}`;
}

function needsIdempotencyKey(method: Method) {
  return method !== 'GET';
}

async function rawRequest(path: string, opts: RequestOpts): Promise<Response> {
  const method: Method = opts.method ?? 'GET';
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...(opts.headers ?? {}),
  };

  if (opts.body !== undefined && !(opts.body instanceof FormData)) {
    headers['Content-Type'] ??= 'application/json';
  }

  if (needsIdempotencyKey(method) && !headers['Idempotency-Key']) {
    headers['Idempotency-Key'] = randomUuid();
  }

  if (!opts.skipAuth) {
    const snap = readSnapshot();
    if (snap?.tokens.accessToken) {
      headers['Authorization'] = `Bearer ${snap.tokens.accessToken}`;
    }
  }

  const timeoutMs = opts.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const { signal, cleanup } = combineSignals(opts.signal, timeoutMs);

  try {
    return await fetch(url(path), {
      method,
      headers,
      body:
        opts.body === undefined || opts.body instanceof FormData
          ? (opts.body as FormData | undefined)
          : JSON.stringify(opts.body),
      signal,
      credentials: 'omit',
    });
  } finally {
    cleanup();
  }
}

// Use the JWT `exp` claim — the response field `accessTokenExpiresAt` is
// a naive `LocalDateTime` in server-local time (Asia/Karachi), so any
// string-parse here would be off by the server's UTC offset.
function isAccessTokenLocallyExpired(): boolean {
  const snap = readSnapshot();
  const exp = readJwtExpMs(snap?.tokens.accessToken);
  return exp !== null && exp <= Date.now();
}

// Single-flight refresh: if multiple requests 401 simultaneously, only one
// hits /auth/refresh and the others wait on its promise.
let refreshInFlight: Promise<TokenResponse | null> | null = null;

async function refreshTokens(): Promise<TokenResponse | null> {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    const snap = readSnapshot();
    if (!snap?.tokens.refreshToken) return null;
    try {
      const res = await rawRequest('/auth/refresh', {
        method: 'POST',
        body: { refreshToken: snap.tokens.refreshToken },
        skipAuth: true,
      });
      if (!res.ok) {
        // Only drop the session if the server actually rejected the refresh
        // token (401/403). 5xx / gateway flap / timeout are transient — the
        // tokens are still valid; the next request will try again.
        if (res.status === 401 || res.status === 403) clearSnapshot();
        return null;
      }
      const next = (await res.json()) as TokenResponse;
      writeSnapshot({ tokens: next, user: snap.user });
      return next;
    } catch {
      // Network error / timeout — never an auth failure. Keep the session.
      return null;
    } finally {
      // Release the lock after the value resolves so concurrent waiters reuse it.
      queueMicrotask(() => {
        refreshInFlight = null;
      });
    }
  })();
  return refreshInFlight;
}

export async function request<T>(
  path: string,
  opts: RequestOpts = {},
): Promise<T> {
  let response: Response;
  try {
    response = await rawRequest(path, opts);
  } catch (e) {
    // Distinguish three abort flavours so the UI can react sensibly:
    //   - caller cancelled (TanStack Query unmount, route change) → rethrow as-is
    //     so React Query treats it as a cancellation, not an error.
    //   - hard timeout fired → surface as a synthetic ApiError(504-style).
    //   - network failure (offline, DNS, gateway down) → synthetic ApiError(0).
    if (e instanceof DOMException && e.name === 'AbortError') {
      const callerCancelled = opts.signal?.aborted;
      if (callerCancelled) throw e;
      throw new ApiError({
        type: 'about:blank',
        title: 'Request timed out',
        status: 504,
        code: 'GATEWAY_TIMEOUT',
        detail: e.message,
        correlationId: 'client-timeout',
        timestamp: new Date().toISOString(),
      });
    }
    throw new ApiError({
      type: 'about:blank',
      title: 'Network error',
      status: 0,
      code: 'NETWORK_ERROR',
      detail: e instanceof Error ? e.message : 'Failed to reach the server.',
      correlationId: 'client-network',
      timestamp: new Date().toISOString(),
    });
  }

  // 401 always means refresh-and-retry. 403 normally means "real permission
  // denial" — but commons-web's JwtAuthenticationFilter silently drops an
  // expired/invalid JWT, after which Spring Security 403s the protected
  // route. Distinguish by checking the locally-known token expiry: if our
  // stored access token is past its `accessTokenExpiresAt`, the 403 is the
  // gateway's flavour of "your token died" and we should refresh.
  const tokenLikelyDead =
    response.status === 401 ||
    (response.status === 403 && isAccessTokenLocallyExpired());
  if (tokenLikelyDead && !opts.skipAuth) {
    const refreshed = await refreshTokens();
    if (refreshed) {
      response = await rawRequest(path, opts);
    }
  }

  if (!response.ok) {
    throw await parseProblemDetail(response);
  }

  if (response.status === 204) return undefined as T;
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('application/json')) return undefined as T;
  return (await response.json()) as T;
}

export const http = {
  get: <T>(path: string, opts?: Omit<RequestOpts, 'method' | 'body'>) =>
    request<T>(path, { ...opts, method: 'GET' }),
  post: <T>(
    path: string,
    body?: unknown,
    opts?: Omit<RequestOpts, 'method' | 'body'>,
  ) => request<T>(path, { ...opts, method: 'POST', body }),
  patch: <T>(
    path: string,
    body?: unknown,
    opts?: Omit<RequestOpts, 'method' | 'body'>,
  ) => request<T>(path, { ...opts, method: 'PATCH', body }),
  put: <T>(
    path: string,
    body?: unknown,
    opts?: Omit<RequestOpts, 'method' | 'body'>,
  ) => request<T>(path, { ...opts, method: 'PUT', body }),
  del: <T>(
    path: string,
    body?: unknown,
    opts?: Omit<RequestOpts, 'method' | 'body'>,
  ) => request<T>(path, { ...opts, method: 'DELETE', body }),
};

export { ApiError };

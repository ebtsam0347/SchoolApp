// Binary-download helper for the reports surface. The shared `http` client
// in `src/lib/http.ts` is JSON-only — it parses every body as JSON and
// returns `undefined` otherwise. Reports return XLSX / PDF / CSV / ZIP
// blobs (or 302 → signed MinIO URLs that the browser auto-follows on
// `/reports/jobs/{id}/download`), so we need a separate path that:
//
//   - keeps the `Authorization: Bearer …` header from auth-storage,
//   - parses ProblemDetail bodies on non-2xx into the same `ApiError` the
//     rest of the app already handles,
//   - reads the suggested filename out of `Content-Disposition` (RFC 5987
//     `filename*=` is preferred when present, then `filename="…"`, then
//     bare `filename=…`), falling back to a caller-supplied default,
//   - saves via a synthetic `<a download>` click + `URL.revokeObjectURL`.
//
// Token refresh: this helper does NOT mirror http.ts's silent refresh.
// If the access token has expired exactly between the user's last JSON
// fetch and the download click, the gateway will 401/403 and the caller
// surfaces a "session expired" message. The fix is to retry — any
// background JSON call will rotate the token transparently. Add the
// refresh dance here only if it bites in practice.

import { parseProblemDetail } from '@/lib/api-error';
import { readSnapshot } from '@/lib/auth-storage';

const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? '';

function urlOf(path: string): string {
  if (path.startsWith('http')) return path;
  if (!BASE) return path;
  return `${BASE}${path.startsWith('/') ? '' : '/'}${path}`;
}

function authHeader(): Record<string, string> {
  const snap = readSnapshot();
  return snap?.tokens.accessToken
    ? { Authorization: `Bearer ${snap.tokens.accessToken}` }
    : {};
}

function filenameFromDisposition(
  cd: string | null,
  fallback: string,
): string {
  if (!cd) return fallback;
  const star = /filename\*=(?:UTF-8'')?([^;]+)/i.exec(cd);
  if (star) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^"|"$/g, ''));
    } catch {
      // fall through to the simpler patterns
    }
  }
  const quoted = /filename="([^"]+)"/i.exec(cd);
  if (quoted) return quoted[1];
  const bare = /filename=([^;]+)/i.exec(cd);
  if (bare) return bare[1].trim();
  return fallback;
}

/** Save a blob to disk via a synthetic `<a download>` click. */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export type ReportFile = { blob: Blob; filename: string };

/**
 * Fetch a report file from the gateway without saving it — for callers
 * that preview the blob first (the appointment-letter drawer). A `body`
 * turns the call into a JSON POST. No Idempotency-Key is stamped: report
 * POSTs are read-only renders, and a replayed idempotent response comes
 * back as a corrupted PDF.
 *
 * Throws `ApiError` on non-2xx; domain extensions on the ProblemDetail
 * (e.g. `missingFields`) survive on `err.problem`.
 */
export async function fetchReportFile(
  path: string,
  fallbackFilename: string,
  opts?: { body?: unknown; signal?: AbortSignal; accept?: string },
): Promise<ReportFile> {
  const hasBody = opts?.body !== undefined;
  const res = await fetch(urlOf(path), {
    method: hasBody ? 'POST' : 'GET',
    headers: {
      // Defaults to any type — reports come back as XLSX / CSV / ZIP too.
      // Errors still negotiate to problem+json either way.
      Accept: opts?.accept ?? '*/*',
      ...(hasBody ? { 'Content-Type': 'application/json' } : {}),
      ...authHeader(),
    },
    body: hasBody ? JSON.stringify(opts?.body) : undefined,
    signal: opts?.signal,
    credentials: 'omit',
  });
  if (!res.ok) throw await parseProblemDetail(res);
  return {
    blob: await res.blob(),
    filename: filenameFromDisposition(
      res.headers.get('Content-Disposition'),
      fallbackFilename,
    ),
  };
}

/**
 * Fetch a report blob from the gateway and save it to disk. Used for both
 * sync `/reports/<svc>/...` endpoints (200 + body) and the async
 * `/reports/jobs/{id}/download` endpoint (302 → signed MinIO URL; the
 * browser follows automatically and strips the Authorization header on
 * the cross-origin redirect, which is what MinIO expects for presigned
 * URLs).
 *
 * Throws `ApiError` on non-2xx, with the same ProblemDetail shape every
 * other call site already handles (so `getErrorMessage` /
 * `forbiddenOr` work uniformly).
 */
export async function downloadReportBlob(
  path: string,
  fallbackFilename: string,
  opts?: { signal?: AbortSignal; accept?: string },
): Promise<void> {
  const { blob, filename } = await fetchReportFile(path, fallbackFilename, opts);
  saveBlob(blob, filename);
}

// Reports BFF + service-direct wrappers.
//
// Catalog discovery goes through the BFF (`/bff/reports/catalog`); every
// other call lands directly on the report service via the gateway —
// async submits at the path the catalog gives us, status polls at
// `/reports/jobs/{id}`, downloads at `/reports/jobs/{id}/download`.
//
// `buildReportRequest` is the single dispatcher: it consumes the
// catalog's `parameters[]` schema and routes each value to its
// `location` (`path` → templated, `query` → ?…, `body` → JSON body).
// Drawers don't need to know what shape a particular report wants.

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';

import { downloadReportBlob, fetchReportFile, type ReportFile } from './download';
import type {
  ReportCatalogEntry,
  ReportFormat,
  ReportJob,
  ReportParameterValues,
} from './types';

const FORMAT_EXTENSION: Record<ReportFormat, string> = {
  xlsx: 'xlsx',
  pdf: 'pdf',
  csv: 'csv',
  zip: 'zip',
  json: 'json',
};

/** Filename used when the server omits `Content-Disposition` — a stable,
 *  recognisable name that includes the report id and today's date. */
function fallbackFilename(entry: ReportCatalogEntry): string {
  const today = new Date().toISOString().slice(0, 10);
  return `${entry.id}-${today}.${FORMAT_EXTENSION[entry.format]}`;
}

/** Filename for the async-download endpoint. We don't know the original
 *  catalog entry there, so derive from the job. `type` looks like
 *  `PAY_REGISTER` — lowercase + dash for readability. */
function fallbackJobFilename(job: ReportJob): string {
  const slug = job.type.toLowerCase().replace(/_/g, '-');
  return `${slug}-${job.id}`;
}

/* ------------------------------ Catalog -------------------------------- */

/** Permission-filtered list of every report the caller can run. */
export function fetchReportCatalog(opts?: {
  signal?: AbortSignal;
}): Promise<ReportCatalogEntry[]> {
  return http.get<ReportCatalogEntry[]>('/bff/reports/catalog', opts);
}

/* ----------------------- Request shape dispatcher ---------------------- */

interface BuiltRequest {
  path: string;
  query: Record<string, unknown>;
  body: Record<string, unknown> | undefined;
}

/**
 * Apply user-entered values to a catalog entry, splitting them into the
 * three transports the report server understands. Drops empty values
 * (undefined/null/"") so optional parameters don't poison the URL.
 */
export function buildReportRequest(
  entry: ReportCatalogEntry,
  values: ReportParameterValues,
): BuiltRequest {
  let path = entry.path;
  const query: Record<string, unknown> = {};
  const body: Record<string, unknown> = {};
  for (const p of entry.parameters) {
    const v = values[p.name];
    if (v === undefined || v === null || v === '') continue;
    if (p.location === 'path') {
      path = path.replace(`{${p.name}}`, encodeURIComponent(String(v)));
    } else if (p.location === 'query') {
      query[p.name] = v;
    } else {
      body[p.name] = v;
    }
  }
  return {
    path,
    query,
    body: Object.keys(body).length ? body : undefined,
  };
}

/* --------------------------- Sync execution --------------------------- */

/**
 * Run a `mode: "sync"` report and trigger a download. Resolves once the
 * blob has been saved to disk; rejects with `ApiError` on non-2xx.
 */
export async function runSyncReport(
  entry: ReportCatalogEntry,
  values: ReportParameterValues,
  opts?: { signal?: AbortSignal },
): Promise<void> {
  const { path, query } = buildReportRequest(entry, values);
  await downloadReportBlob(path + qs(query), fallbackFilename(entry), opts);
}

/**
 * Run a `mode: "sync"` report and return the file without saving it — for
 * PDFs, which open in the preview drawer. Rejects with `ApiError` on non-2xx.
 */
export function fetchSyncReport(
  entry: ReportCatalogEntry,
  values: ReportParameterValues,
  opts?: { signal?: AbortSignal },
): Promise<ReportFile> {
  const { path, query } = buildReportRequest(entry, values);
  return fetchReportFile(path + qs(query), fallbackFilename(entry), opts);
}

/* -------------------------- Async lifecycle --------------------------- */

/**
 * Enqueue a `mode: "async"` report. Returns the freshly-created job row;
 * the UI then polls `fetchReportJob` until status settles.
 */
export function runAsyncReport(
  entry: ReportCatalogEntry,
  values: ReportParameterValues,
  opts?: { signal?: AbortSignal },
): Promise<ReportJob> {
  const { path, query, body } = buildReportRequest(entry, values);
  return http.post<ReportJob>(path + qs(query), body, opts);
}

/** Status row for one job. Used as the polling unit. */
export function fetchReportJob(
  id: number,
  opts?: { signal?: AbortSignal },
): Promise<ReportJob> {
  return http.get<ReportJob>(`/reports/jobs/${id}`, opts);
}

/**
 * Download the output of a READY job. The endpoint 302s to a signed MinIO
 * URL; `downloadReportBlob` follows the redirect and saves the blob.
 * Throws `ApiError` if the job isn't READY (the server 409s).
 */
export function downloadJobOutput(
  job: ReportJob,
  opts?: { signal?: AbortSignal },
): Promise<void> {
  return downloadReportBlob(
    `/reports/jobs/${job.id}/download`,
    fallbackJobFilename(job),
    opts,
  );
}

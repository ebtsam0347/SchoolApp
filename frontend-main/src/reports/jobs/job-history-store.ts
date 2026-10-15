// Persistent record of async report jobs the *current browser* has
// submitted. The server-side `/reports/jobs/{id}` endpoint only returns
// rows by id — it does NOT have a "list my jobs" endpoint and does NOT
// echo back the original request parameters. So if the user wants to
// see "what async reports have I run today" or to re-run an earlier
// submission with the same parameters, the FE has to remember.
//
// This store is intentionally tiny: a localStorage list of
// `{ jobId, entryId, title, submittedAt, parameters }`. We keep the
// 50 most recent. The Jobs page hydrates the table by mapping over
// this list and querying the server for each job's current status.
//
// Lives in localStorage so it survives reloads. Per-browser-only is
// fine — these are user-initiated downloads, not shared state. The key
// `fshrms.reports.jobs.v1` follows the convention `auth-storage.ts`
// uses (`fshrms.<scope>.v<version>`).

import type { ReportCatalogEntry, ReportJob, ReportParameterValues } from '../types';

const KEY = 'fshrms.reports.jobs.v1';
const MAX_ENTRIES = 50;

export interface RememberedJob {
  jobId: number;
  entryId: string;
  title: string;
  submittedAt: string;
  /** Verbatim values from the parameter drawer — used to power Retry. */
  parameters: ReportParameterValues;
}

const isBrowser =
  typeof window !== 'undefined' && !!window.localStorage;

function parseAll(raw: string | null): RememberedJob[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw) as RememberedJob[];
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter(
        (j): j is RememberedJob =>
          !!j && typeof j.jobId === 'number' && typeof j.entryId === 'string',
      )
      .sort((a, b) => (a.submittedAt < b.submittedAt ? 1 : -1));
  } catch {
    return [];
  }
}

// Snapshot cache so `useSyncExternalStore` sees a stable array reference
// when nothing has changed. Without this, the hook treats every render as
// a state change, schedules a re-render, and React aborts with
// "Maximum update depth exceeded". We invalidate the cache whenever the
// raw localStorage string differs from what we last saw — i.e. when a
// write happened in this tab (via writeAll → emit) or another tab (via
// the storage event in subscribeSubmittedJobs).
let cachedRaw: string | null = null;
let cachedSnapshot: RememberedJob[] = [];
let cacheInitialised = false;

function getCachedSnapshot(): RememberedJob[] {
  if (!isBrowser) return cachedSnapshot;
  const raw = window.localStorage.getItem(KEY);
  if (!cacheInitialised || raw !== cachedRaw) {
    cachedRaw = raw;
    cachedSnapshot = parseAll(raw);
    cacheInitialised = true;
  }
  return cachedSnapshot;
}

function writeAll(list: RememberedJob[]): void {
  if (!isBrowser) return;
  const trimmed = list.slice(0, MAX_ENTRIES);
  const serialized = JSON.stringify(trimmed);
  window.localStorage.setItem(KEY, serialized);
  cachedRaw = serialized;
  cachedSnapshot = parseAll(serialized);
  cacheInitialised = true;
  emit();
}

/** Snapshot for the current browser. Newest first. The reference is
 *  stable until the underlying localStorage changes, so it's safe to
 *  pass straight to `useSyncExternalStore`. */
export function listSubmittedJobs(): RememberedJob[] {
  return getCachedSnapshot();
}

/** Persist a new submission. Replaces any prior entry with the same jobId
 *  (defensive — shouldn't happen, but keeps the list de-duplicated). */
export function rememberSubmittedJob(
  job: ReportJob,
  entry: ReportCatalogEntry,
  parameters: ReportParameterValues,
): void {
  const next: RememberedJob = {
    jobId: job.id,
    entryId: entry.id,
    title: entry.title,
    submittedAt: new Date().toISOString(),
    parameters,
  };
  const list = getCachedSnapshot().filter((j) => j.jobId !== job.id);
  list.unshift(next);
  writeAll(list);
}

/** Drop a job from history (e.g. user clicked "Forget"). */
export function forgetSubmittedJob(jobId: number): void {
  writeAll(getCachedSnapshot().filter((j) => j.jobId !== jobId));
}

/** Clear everything — surfaced as a "Clear history" button on the Jobs page. */
export function clearSubmittedJobs(): void {
  writeAll([]);
}

/** Pub/sub so the Jobs page can re-render after the drawer adds a row.
 *  Also subscribes to cross-tab storage events so a submission from
 *  another tab shows up live without reload. */
type Listener = () => void;
const listeners = new Set<Listener>();

export function subscribeSubmittedJobs(listener: Listener): () => void {
  listeners.add(listener);
  if (!isBrowser) {
    return () => {
      listeners.delete(listener);
    };
  }
  const onStorage = (e: StorageEvent) => {
    if (e.key === KEY) {
      // Drop the cache so the next getCachedSnapshot() re-reads disk.
      cacheInitialised = false;
      listener();
    }
  };
  window.addEventListener('storage', onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener('storage', onStorage);
  };
}

function emit() {
  for (const l of listeners) l();
}

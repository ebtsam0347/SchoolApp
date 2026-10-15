// Per-user palette recents in localStorage. Stores both static-entry refs
// (by `key` — labels/icons are re-resolved from `nav-config` at render time
// so a rename or removal silently drops stale entries) and remote-hit
// snapshots (full payload — the entity may not be in any local registry).
//
// v2 shape change from v1 (which was `string[]` of static keys only):
//   v1: ["nav-directory", "qa-apply-leave"]
//   v2: [{ kind: "static", key: "nav-directory" }, { kind: "remote", ... }]
// Old v1 blobs are ignored on read; first save under v2 supersedes them.

const MAX_RECENTS = 6;
const KEY_PREFIX = 'fshrms.palette.recent.v2';

export type RecentEntry =
  | { kind: 'static'; key: string }
  | {
      kind: 'remote';
      id: string;
      type: string;
      title: string;
      subtitle?: string;
      url: string;
      badge?: string;
    };

function storageKey(userId: number | string): string {
  return `${KEY_PREFIX}.${userId}`;
}

function isRecentEntry(value: unknown): value is RecentEntry {
  if (!value || typeof value !== 'object') return false;
  const v = value as Record<string, unknown>;
  if (v.kind === 'static') return typeof v.key === 'string';
  if (v.kind === 'remote') {
    return (
      typeof v.id === 'string' &&
      typeof v.type === 'string' &&
      typeof v.title === 'string' &&
      typeof v.url === 'string'
    );
  }
  return false;
}

function dedupeKey(entry: RecentEntry): string {
  return entry.kind === 'static' ? `s:${entry.key}` : `r:${entry.id}`;
}

export function loadRecents(
  userId: number | string | null | undefined,
): RecentEntry[] {
  if (userId == null) return [];
  try {
    const raw = localStorage.getItem(storageKey(userId));
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(isRecentEntry).slice(0, MAX_RECENTS);
  } catch {
    return [];
  }
}

export function pushRecent(
  userId: number | string | null | undefined,
  entry: RecentEntry,
): RecentEntry[] {
  if (userId == null) return [];
  const current = loadRecents(userId);
  const incomingKey = dedupeKey(entry);
  const next = [
    entry,
    ...current.filter((e) => dedupeKey(e) !== incomingKey),
  ].slice(0, MAX_RECENTS);
  try {
    localStorage.setItem(storageKey(userId), JSON.stringify(next));
  } catch {
    // Quota or disabled storage — recents are best-effort, swallow.
  }
  return next;
}

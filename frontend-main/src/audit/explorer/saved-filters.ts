// localStorage-backed saved filter sets for the audit explorer. Each
// saved set has a name + the FiltersState (with the dayjs range
// serialized to ISO strings so it survives a JSON round-trip).
//
// Storage key: `fshrms.audit.savedFilters.v1`. Values are versioned and
// schema-validated on read so a corrupt entry never blocks the UI.

import dayjs from 'dayjs';
import type { FiltersState, VisibilityFilter } from './types';

const STORAGE_KEY = 'fshrms.audit.savedFilters.v1';

type Serialized = {
  q: string;
  entityType?: string;
  action?: string;
  sourceService?: string;
  visibility: VisibilityFilter;
  range?: [string, string];
};

export type SavedFilter = {
  name: string;
  filters: Serialized;
};

function readAll(): SavedFilter[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (x): x is SavedFilter =>
        !!x &&
        typeof x === 'object' &&
        typeof (x as SavedFilter).name === 'string' &&
        !!(x as SavedFilter).filters,
    );
  } catch {
    return [];
  }
}

function writeAll(items: SavedFilter[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
  } catch {
    // localStorage unavailable / quota exceeded — silently degrade.
  }
}

export function listSavedFilters(): SavedFilter[] {
  return readAll();
}

export function serialize(filters: FiltersState): Serialized {
  return {
    q: filters.q,
    entityType: filters.entityType,
    action: filters.action,
    sourceService: filters.sourceService,
    visibility: filters.visibility,
    range: filters.range
      ? [filters.range[0].toISOString(), filters.range[1].toISOString()]
      : undefined,
  };
}

export function deserialize(s: Serialized): FiltersState {
  return {
    q: s.q ?? '',
    entityType: s.entityType,
    action: s.action,
    sourceService: s.sourceService,
    visibility: s.visibility ?? 'ALL',
    range: s.range ? [dayjs(s.range[0]), dayjs(s.range[1])] : null,
  };
}

export function saveFilter(name: string, filters: FiltersState): SavedFilter[] {
  const trimmed = name.trim();
  if (trimmed.length === 0) return readAll();
  const all = readAll().filter((s) => s.name !== trimmed);
  const next: SavedFilter[] = [
    { name: trimmed, filters: serialize(filters) },
    ...all,
  ].slice(0, 50);
  writeAll(next);
  return next;
}

export function deleteFilter(name: string): SavedFilter[] {
  const next = readAll().filter((s) => s.name !== name);
  writeAll(next);
  return next;
}

export function isFiltersDirty(f: FiltersState): boolean {
  return (
    f.q.length > 0 ||
    !!f.entityType ||
    !!f.action ||
    !!f.sourceService ||
    f.visibility !== 'ALL' ||
    f.range !== null
  );
}

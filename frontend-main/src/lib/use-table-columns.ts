// Persist the user's hidden-column choices for a table to localStorage.
//
// We track *hidden* keys (not visible ones) so that newly added columns
// default to visible — otherwise users would silently miss new data the
// day a column lands.
//
// Pass a stable `storageKey` per table (e.g. 'people.directory.cols').
// The hook returns the filtered AntD columns and a toggle / reset API.

import { useCallback, useEffect, useMemo, useState } from 'react';

export type ColumnLike = { key?: React.Key; dataIndex?: unknown; title?: unknown };

const PREFIX = 'fshrms.table-cols.v1.';

function readHidden(storageKey: string): string[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(PREFIX + storageKey);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((v) => typeof v === 'string') : [];
  } catch {
    return [];
  }
}

function writeHidden(storageKey: string, hidden: string[]): void {
  if (typeof window === 'undefined') return;
  try {
    if (hidden.length === 0) {
      window.localStorage.removeItem(PREFIX + storageKey);
    } else {
      window.localStorage.setItem(PREFIX + storageKey, JSON.stringify(hidden));
    }
  } catch {
    // best-effort — quota / disabled storage shouldn't break the table
  }
}

export type ColumnPrefsOption = {
  key: string;
  label: string;
  hidden: boolean;
};

export type UseTableColumnsResult<C extends ColumnLike> = {
  /** Columns filtered to only the visible ones (pass to <Table columns=>). */
  columns: C[];
  /** All columns + hidden flag — render each as a checkbox in a dropdown. */
  options: ColumnPrefsOption[];
  toggle: (key: string) => void;
  reset: () => void;
  hiddenCount: number;
};

function columnKey(c: ColumnLike, idx: number): string {
  if (typeof c.key === 'string' || typeof c.key === 'number') return String(c.key);
  if (typeof c.dataIndex === 'string') return c.dataIndex;
  if (Array.isArray(c.dataIndex)) return c.dataIndex.join('.');
  return `__col_${idx}`;
}

function columnLabel(c: ColumnLike, fallback: string): string {
  if (typeof c.title === 'string') return c.title;
  if (typeof c.title === 'number') return String(c.title);
  return fallback;
}

export type UseTableColumnsOptions = {
  /**
   * Column keys that should be hidden by default until the user opts them
   * in. Lets a table expose extra fields without overwhelming new users.
   * Once the user makes any explicit choice, their stored preference wins.
   * "Reset" returns to this default list.
   */
  defaultHidden?: ReadonlyArray<string>;
};

export function useTableColumns<C extends ColumnLike>(
  storageKey: string,
  allColumns: C[],
  hookOptions?: UseTableColumnsOptions,
): UseTableColumnsResult<C> {
  const defaults = hookOptions?.defaultHidden;
  const [hidden, setHidden] = useState<string[]>(() => {
    const stored = readHidden(storageKey);
    if (stored.length > 0) return stored;
    // No stored prefs yet — apply the defaults if any.
    if (defaults && defaults.length > 0) return [...defaults];
    return [];
  });

  // If storageKey changes (rare — different table on the same page), reload.
  // `defaults` should be a referentially-stable array (module-level constant
  // by convention) so this effect doesn't re-fire on every render.
  useEffect(() => {
    const stored = readHidden(storageKey);
    if (stored.length > 0) setHidden(stored);
    else if (defaults && defaults.length > 0) setHidden([...defaults]);
    else setHidden([]);
  }, [storageKey, defaults]);

  const toggle = useCallback(
    (key: string) => {
      setHidden((prev) => {
        const next = prev.includes(key)
          ? prev.filter((k) => k !== key)
          : [...prev, key];
        writeHidden(storageKey, next);
        return next;
      });
    },
    [storageKey],
  );

  const reset = useCallback(() => {
    const next = defaults && defaults.length > 0 ? [...defaults] : [];
    setHidden(next);
    // Clearing localStorage means future loads also pick up the defaults.
    writeHidden(storageKey, []);
  }, [storageKey, defaults]);

  const hiddenSet = useMemo(() => new Set(hidden), [hidden]);

  const columns = useMemo(
    () => allColumns.filter((c, i) => !hiddenSet.has(columnKey(c, i))),
    [allColumns, hiddenSet],
  );

  const options = useMemo<ColumnPrefsOption[]>(
    () =>
      allColumns.map((c, i) => {
        const key = columnKey(c, i);
        return {
          key,
          label: columnLabel(c, `Column ${i + 1}`),
          hidden: hiddenSet.has(key),
        };
      }),
    [allColumns, hiddenSet],
  );

  return { columns, options, toggle, reset, hiddenCount: hidden.length };
}

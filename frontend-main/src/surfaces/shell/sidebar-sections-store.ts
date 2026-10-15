// Zustand store for which sidebar nav sections the user has folded shut.
//
// Why a store: the expanded rail and the mobile drawer can both render the
// nav at once, and either can toggle a section — both need to react. Mirrors
// workspace-store's load-once / persist-on-write shape.
//
// Persistence: localStorage key `fshrms.sidebar.collapsedSections.v1`, an
// array of collapsed group keys. Group keys are unique across workspaces
// (hr / accounts / ai are all prefixed), so one flat set is enough. Default
// is empty — every section starts expanded, so behaviour is unchanged until
// the user folds something.

import { create } from 'zustand';

const STORAGE_KEY = 'fshrms.sidebar.collapsedSections.v1';

function loadCollapsed(): Set<string> {
  if (typeof localStorage === 'undefined') return new Set();
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return new Set();
    const parsed: unknown = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return new Set(parsed.filter((x): x is string => typeof x === 'string'));
    }
  } catch {
    // malformed JSON or private-mode throw — fall through to empty.
  }
  return new Set();
}

function persistCollapsed(s: Set<string>) {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([...s]));
  } catch {
    // ignore quota / privacy errors — non-fatal.
  }
}

interface SidebarSectionsStore {
  collapsed: Set<string>;
  toggle: (key: string) => void;
  /**
   * Unfold a section, no-op if already open. Used to auto-expand the section
   * holding the active route on navigation (see SidebarSection) — mirrors the
   * row tier's `containsActive` behaviour. Returns the same state reference
   * when nothing changes so it never thrashes the store or localStorage.
   */
  expand: (key: string) => void;
}

export const useSidebarSectionsStore = create<SidebarSectionsStore>((set) => ({
  collapsed: loadCollapsed(),
  toggle: (key) =>
    set((state) => {
      const next = new Set(state.collapsed);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      persistCollapsed(next);
      return { collapsed: next };
    }),
  expand: (key) =>
    set((state) => {
      if (!state.collapsed.has(key)) return state;
      const next = new Set(state.collapsed);
      next.delete(key);
      persistCollapsed(next);
      return { collapsed: next };
    }),
}));

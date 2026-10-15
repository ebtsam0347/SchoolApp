// Zustand store for the active workspace.
//
// HR is the only workspace — the Accounts and Assistant previews were removed
// from the Topbar switcher. The store stays because the Sidebar and AppShell
// still resolve their nav tree through it, so restoring a second workspace is
// an append here rather than a re-wiring.
//
// Persistence: localStorage key `fshrms.workspace.v1`. A value written before
// the previews were removed ('accounts' / 'ai') is no longer valid and falls
// back to 'hr' — without that, anyone who had switched would boot into a nav
// tree the switcher can no longer navigate them out of.

import { create } from 'zustand';

export type Workspace = 'hr';

const STORAGE_KEY = 'fshrms.workspace.v1';
const DEFAULT_WORKSPACE: Workspace = 'hr';

function loadWorkspace(): Workspace {
  if (typeof localStorage === 'undefined') return DEFAULT_WORKSPACE;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === 'hr') return raw;
  } catch {
    // localStorage may throw in private mode; fall through.
  }
  return DEFAULT_WORKSPACE;
}

function persistWorkspace(w: Workspace) {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(STORAGE_KEY, w);
  } catch {
    // ignore quota / privacy errors — non-fatal.
  }
}

interface WorkspaceStore {
  workspace: Workspace;
  setWorkspace: (w: Workspace) => void;
}

export const useWorkspaceStore = create<WorkspaceStore>((set) => ({
  workspace: loadWorkspace(),
  setWorkspace: (w) => {
    persistWorkspace(w);
    set({ workspace: w });
  },
}));

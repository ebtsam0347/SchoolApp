// Zustand store for command-palette open state + recents.
//
// Why a store and not local state:
//   - Open/close needs to be triggerable from anywhere (a route's empty
//     state, a dashboard "search" CTA, the future command-registry from
//     PR 4). Local state in `Topbar` couldn't be reached from those.
//   - Recents stay in localStorage as the source of truth (`palette-recents.ts`);
//     the store just caches them in memory and gives consumers a subscribable
//     handle so future widgets ("Recently visited" on the dashboard) can read
//     without reaching back into localStorage on every render.
//
// What this store is NOT:
//   - Server state. That's TanStack Query.
//   - Auth identity / ability. That's Context.
//   - Anything that belongs in URL search params.

import { create } from 'zustand';
import {
  loadRecents,
  pushRecent as persistRecent,
  type RecentEntry,
} from './palette-recents';

interface PaletteStore {
  open: boolean;
  recents: RecentEntry[];
  setOpen: (open: boolean) => void;
  toggle: () => void;
  /** Load recents for the given user — call on palette open and on user change. */
  syncRecents: (userId: number | string | null | undefined) => void;
  /** Push an entry to recents (writes through to localStorage). */
  pushRecent: (
    userId: number | string | null | undefined,
    entry: RecentEntry,
  ) => void;
}

export const usePaletteStore = create<PaletteStore>((set) => ({
  open: false,
  recents: [],
  setOpen: (open) => set({ open }),
  toggle: () => set((s) => ({ open: !s.open })),
  syncRecents: (userId) => set({ recents: loadRecents(userId) }),
  pushRecent: (userId, entry) =>
    set({ recents: persistRecent(userId, entry) }),
}));

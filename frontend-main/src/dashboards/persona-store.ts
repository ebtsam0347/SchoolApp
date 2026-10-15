// Zustand store for the persona override.
//
// Why a store and not local state in `useDashboardPersona`:
//   The RoleSwitcher and DashboardRouter both call `useDashboardPersona`,
//   so each was its own `useState`. Switching personas updated the
//   switcher's copy but not the router's, and the cross-tab `storage`
//   listener only fires in *other* tabs — not the writer's. A shared
//   store gives every caller the same subscription.
//
// localStorage stays the source of truth (`persona-storage.ts`); the
// store caches it in memory and writes through on every change.

import { useEffect } from 'react';
import { create } from 'zustand';

import { readPersona, writePersona } from './persona-storage';
import type { DashboardPersona } from './persona';

interface PersonaStore {
  persona: DashboardPersona | null;
  setPersona: (persona: DashboardPersona | null) => void;
  /** Hydrate from localStorage (called by `useHydratePersonaStore`). */
  hydrate: () => void;
}

export const usePersonaStore = create<PersonaStore>((set) => ({
  persona: typeof window === 'undefined' ? null : readPersona(),
  setPersona: (persona) => {
    writePersona(persona);
    set({ persona });
  },
  hydrate: () => set({ persona: readPersona() }),
}));

/**
 * Mount once near the app root (e.g. inside a top-level layout) to
 * subscribe to cross-tab `storage` events. Same-tab updates flow
 * through `setPersona` directly.
 */
export function useHydratePersonaStore() {
  useEffect(() => {
    const handler = (e: StorageEvent) => {
      if (e.key !== 'fshrms.dashboard.persona') return;
      usePersonaStore.getState().hydrate();
    };
    window.addEventListener('storage', handler);
    return () => window.removeEventListener('storage', handler);
  }, []);
}

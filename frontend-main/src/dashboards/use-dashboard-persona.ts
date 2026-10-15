/**
 * Compose persona availability + override into the value that route
 * + switcher consume. Returns the resolved persona, the available set,
 * and a setter that writes through to localStorage.
 *
 * State lives in `usePersonaStore` (Zustand) so every caller —
 * RoleSwitcher, DashboardRouter, anything else — subscribes to the
 * same source. Switching personas re-renders all subscribers
 * immediately; no hard refresh.
 *
 * Manager signal comes from `/bff/account/profile.employee.directReportsCount`
 * — the Team tab is only shown to employee-linked users who have at
 * least one direct report. Admin-only accounts (no employee link) never
 * qualify for the Team tab.
 */
import { useQuery } from '@tanstack/react-query';

import { useAuth } from '@/auth';
import { peopleBff } from '@/people';

import {
  availablePersonas,
  resolveDashboardPersona,
  type DashboardPersona,
} from './persona';
import { usePersonaStore } from './persona-store';

export interface UseDashboardPersonaResult {
  /** Persona currently rendered. */
  persona: DashboardPersona;
  /** Whether the user has manually overridden the default. */
  isOverride: boolean;
  /** Set of personas the user is eligible for. */
  available: Set<DashboardPersona>;
  /** Persist a new persona override. Pass null to clear. */
  setPersona: (persona: DashboardPersona | null) => void;
}

export function useDashboardPersona(): UseDashboardPersonaResult {
  const { user } = useAuth();
  const stored = usePersonaStore((s) => s.persona);
  const setPersona = usePersonaStore((s) => s.setPersona);

  // Fetch the manager signal once per session — directReportsCount
  // lives on the account profile composite. Skip entirely for admins
  // who don't have an employee record (would 403). Errors degrade
  // quietly to null so the perm-based fallback kicks in.
  const profile = useQuery({
    queryKey: ['account', 'profile'],
    queryFn: ({ signal }) => peopleBff.accountProfile(signal),
    staleTime: 60_000,
    enabled: !!user && user.employeeLinked === true,
    retry: false,
  });
  const directReportsCount =
    profile.data?.employee?.directReportsCount ?? null;

  const available = availablePersonas(user, directReportsCount);
  const fallback = resolveDashboardPersona(user, directReportsCount);

  // If the persisted choice is no longer available (e.g. perms changed),
  // ignore it and use the auto-resolved default. We don't clear storage
  // automatically — the user's intent might come back if their perms
  // are re-granted.
  const persona = stored && available.has(stored) ? stored : fallback;

  return {
    persona,
    isOverride: stored !== null && stored === persona,
    available,
    setPersona,
  };
}

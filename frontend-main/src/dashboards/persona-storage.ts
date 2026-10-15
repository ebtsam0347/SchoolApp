/**
 * localStorage helpers for the persona override. The Zustand store
 * (`persona-store.ts`) reads from / writes through these helpers so
 * the storage layer stays a single, testable function pair.
 */
import type { DashboardPersona } from './persona';

const KEY = 'fshrms.dashboard.persona';
const VALID: ReadonlySet<DashboardPersona> = new Set([
  'employee',
  'manager',
  'hr',
  'payroll',
  'admin',
]);

export function readPersona(): DashboardPersona | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return null;
    return VALID.has(raw as DashboardPersona) ? (raw as DashboardPersona) : null;
  } catch {
    return null;
  }
}

export function writePersona(persona: DashboardPersona | null): void {
  if (typeof window === 'undefined') return;
  try {
    if (persona === null) window.localStorage.removeItem(KEY);
    else window.localStorage.setItem(KEY, persona);
  } catch {
    /* swallow — storage may be unavailable in some environments */
  }
}

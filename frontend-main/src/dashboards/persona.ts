/**
 * Dashboard persona resolver.
 *
 * The route at `/` picks one of the persona layouts based on the caller's
 * permissions. Resolution order is admin → payroll → hr → manager →
 * employee — the highest-tier available wins by default.
 *
 * Users with multiple hats (e.g. an HR-cap engineer who also manages a
 * team) can override via the role switcher; the choice is persisted in
 * localStorage and re-applied on each load. If their permissions change
 * such that the persisted choice is no longer available, we fall back
 * to the auto-resolved default.
 */
import type { UserIdentity } from '@/auth/types';

export type DashboardPersona = 'employee' | 'manager' | 'hr' | 'payroll' | 'admin';

export const PERSONA_ORDER: readonly DashboardPersona[] = [
  'admin',
  'payroll',
  'hr',
  'manager',
  'employee',
] as const;

export const PERSONA_LABEL: Record<DashboardPersona, string> = {
  employee: 'Personal',
  manager: 'My team',
  hr: 'People ops',
  payroll: 'Money',
  admin: 'System',
};

/**
 * Personas with a shipped layout. Unimplemented personas still appear
 * in the switcher (a user with admin perms should know the Admin tab is
 * coming) but render a placeholder until D-5 / D-6 / D-7 land.
 */
export const IMPLEMENTED_PERSONAS: ReadonlySet<DashboardPersona> = new Set([
  'employee',
  'manager',
  'hr',
  'payroll',
  'admin',
]);

/**
 * Set of personas the user is *eligible* for, based on their permissions
 * and (when known) their direct-report count. The resolver uses this to
 * pick a default; the role switcher uses it to populate the segmented
 * control.
 *
 * Personal and Team are gated on employee state:
 * - `employee` (Personal) requires `user.employeeLinked` — an admin-only
 *   account has no employee record to render against.
 * - `manager` (Team) requires the user to be employee-linked AND have at
 *   least one direct report. Ordinary employees never see the Team tab.
 *   Admin/HR personas have their own surfaces and don't need it either.
 *
 * A non-admin carrying the MANAGER role is capped at Personal + My team:
 * see the restriction at the end of the function for why the permission
 * tests alone aren't enough.
 */
export function availablePersonas(
  user: UserIdentity | null,
  directReportsCount: number | null = null,
): Set<DashboardPersona> {
  const set = new Set<DashboardPersona>();
  if (!user) return set;

  const perms = user.permissions ?? [];
  const isAdmin =
    user.roles.includes('ADMIN') ||
    perms.some((p) => p.endsWith('.admin'));
  // Any payroll grant — read, write, admin, reports.run, analytics.read,
  // read.team — earns the Money persona. ACCOUNTS (read-only) should
  // land on the payroll dashboard, not Personal.
  const hasPayrollAccess = perms.some((p) => p.startsWith('payroll.'));
  const hasHrWrite =
    perms.some(
      (p) =>
        p.startsWith('employee.') &&
        (p.endsWith('.write') || p.endsWith('.admin')),
    ) ||
    perms.some((p) => p.startsWith('attendance.') && p.endsWith('.admin'));

  const hasDirectReports =
    directReportsCount !== null && directReportsCount > 0;

  if (isAdmin) set.add('admin');
  if (isAdmin || hasPayrollAccess) set.add('payroll');
  if (isAdmin || hasHrWrite) set.add('hr');
  if (user.employeeLinked && hasDirectReports) set.add('manager');
  if (user.employeeLinked) set.add('employee');

  // A manager's dashboard is their own plus their team — nothing else.
  //
  // The permission tests above are deliberately loose, and manager-scoped
  // grants trip them: `payroll.read.team` and `payroll.analytics.read` both
  // satisfy `startsWith('payroll.')` and were handing every manager the Money
  // tab, even though those grants only cover their own reports. People ops
  // arrives the same way. Cap by role instead: admins keep all four tabs, and
  // a pure HR or payroll user who isn't a manager is unaffected.
  //
  // Runs before the empty-set fallback below so a MANAGER with no employee
  // link still degrades to Personal rather than to nothing.
  const isManager = user.roles.some(
    (r) => r.replace(/^ROLE_/, '').toUpperCase() === 'MANAGER',
  );
  if (isManager && !isAdmin) {
    set.delete('admin');
    set.delete('payroll');
    set.delete('hr');
  }

  // Last-resort fallback for the rare account with no admin/HR/payroll
  // permissions and no employee link — better to render an empty
  // Personal layout than to dead-end on a missing route.
  if (set.size === 0) set.add('employee');

  return set;
}

/**
 * Pick the default persona for the user — the highest-tier available.
 * `manager` is already filtered out of `availablePersonas` when the user
 * has no direct reports, so no extra guard is needed here.
 */
export function resolveDashboardPersona(
  user: UserIdentity | null,
  directReportsCount: number | null = null,
): DashboardPersona {
  const available = availablePersonas(user, directReportsCount);
  for (const p of PERSONA_ORDER) {
    if (available.has(p)) return p;
  }
  return 'employee';
}

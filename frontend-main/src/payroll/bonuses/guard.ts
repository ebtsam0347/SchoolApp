// Route guard for the bonus-run pages. Every bonus-no settings write
// (create / compute / approve / reset / delete) requires `payroll.admin`,
// which CASL maps to `manage:payroll` — the pages are admin-only, so other
// callers are bounced before the route renders.

import { redirect } from '@tanstack/react-router';

import { buildAbility } from '@/auth';
import { readSnapshot } from '@/lib';

export function requirePayrollAdmin() {
  const snap = readSnapshot();
  if (!snap?.user) return;
  if (!buildAbility(snap.user).can('manage', 'payroll')) {
    throw redirect({ to: '/' });
  }
}

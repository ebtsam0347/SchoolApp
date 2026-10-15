// /analytics — six-tile executive overview. Visible to anyone with at
// least one of the four `*.analytics.read` subjects; the composite BFF
// endpoint also returns null per-field when individual downstream
// permissions are missing, so partial-permission callers still get a
// useful (but reduced) page.

import { createFileRoute, redirect } from '@tanstack/react-router';

import { OverviewPage } from '@/analytics/overview/OverviewPage';
import { buildAbility } from '@/auth';
import { readSnapshot } from '@/lib';

export const Route = createFileRoute('/_app/analytics/')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    const ability = buildAbility(snap.user);
    const allowed =
      ability.can('read', 'employee.analytics') ||
      ability.can('read', 'attendance.analytics') ||
      ability.can('read', 'leaves.analytics') ||
      ability.can('read', 'payroll.analytics');
    if (!allowed) throw redirect({ to: '/' });
  },
  component: OverviewPage,
});

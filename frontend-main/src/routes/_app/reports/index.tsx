// /reports — single catalog page driven by /bff/reports/catalog. Route
// guard reads the ability snapshot synchronously and bounces callers
// without any `<svc>.reports.run` permission before AuthProvider mounts.
// Server-side, the catalog endpoint returns an empty list for the same
// users — this is just first-paint protection.

import { createFileRoute, redirect } from '@tanstack/react-router';

import { CatalogPage } from '@/reports';
import { buildAbility } from '@/auth';
import { readSnapshot } from '@/lib';

export const Route = createFileRoute('/_app/reports/')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    const ability = buildAbility(snap.user);
    const allowed =
      ability.can('run', 'employee.reports') ||
      ability.can('run', 'attendance.reports') ||
      ability.can('run', 'attendance.leaves.reports') ||
      ability.can('run', 'payroll.reports');
    if (!allowed) throw redirect({ to: '/' });
  },
  component: CatalogPage,
});

import { createFileRoute, redirect } from '@tanstack/react-router';

import { PayrollAnalyticsPage } from '@/analytics/payroll/PayrollAnalyticsPage';
import { buildAbility } from '@/auth';
import { readSnapshot } from '@/lib';

export const Route = createFileRoute('/_app/analytics/payroll')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    const ability = buildAbility(snap.user);
    if (!ability.can('read', 'payroll.analytics')) {
      throw redirect({ to: '/analytics' });
    }
  },
  component: PayrollAnalyticsPage,
});

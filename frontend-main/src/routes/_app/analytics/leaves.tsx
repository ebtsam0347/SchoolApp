import { createFileRoute, redirect } from '@tanstack/react-router';

import { LeavesAnalyticsPage } from '@/analytics/leaves/LeavesAnalyticsPage';
import { buildAbility } from '@/auth';
import { readSnapshot } from '@/lib';

export const Route = createFileRoute('/_app/analytics/leaves')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    const ability = buildAbility(snap.user);
    if (!ability.can('read', 'leaves.analytics')) {
      throw redirect({ to: '/analytics' });
    }
  },
  component: LeavesAnalyticsPage,
});

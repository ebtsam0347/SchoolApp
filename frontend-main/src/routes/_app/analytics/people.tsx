import { createFileRoute, redirect } from '@tanstack/react-router';

import { PeopleAnalyticsPage } from '@/analytics/people/PeopleAnalyticsPage';
import { buildAbility } from '@/auth';
import { readSnapshot } from '@/lib';

export const Route = createFileRoute('/_app/analytics/people')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    const ability = buildAbility(snap.user);
    if (!ability.can('read', 'employee.analytics')) {
      throw redirect({ to: '/analytics' });
    }
  },
  component: PeopleAnalyticsPage,
});

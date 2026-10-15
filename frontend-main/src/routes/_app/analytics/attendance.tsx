import { createFileRoute, redirect } from '@tanstack/react-router';

import { AttendanceAnalyticsPage } from '@/analytics/attendance/AttendanceAnalyticsPage';
import { buildAbility } from '@/auth';
import { readSnapshot } from '@/lib';

export const Route = createFileRoute('/_app/analytics/attendance')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    const ability = buildAbility(snap.user);
    if (!ability.can('read', 'attendance.analytics')) {
      throw redirect({ to: '/analytics' });
    }
  },
  component: AttendanceAnalyticsPage,
});

// /attendance — operator workspace layout (gate only).
//
// Day-to-day attendance work — operator dashboard, daily records, raw
// punches, period locks, operator-added punches, shift overrides, plus
// shifts + devices catalogs. These pages used to live under /admin/* but
// admins don't run day-to-day attendance; an operator role with
// `attendance.operator` shouldn't have to traverse the admin gate to
// reach them.
//
// Browse pages (dashboard / records / punches / periods) mount
// <AttendanceTabs/> themselves to share the tab header.

import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { buildAbility } from '@/auth';
import { readSnapshot } from '@/lib';

export const Route = createFileRoute('/_app/attendance')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    const ability = buildAbility(snap.user);
    const allowed =
      ability.can('manage', 'attendance') ||
      ability.can('admin', 'attendance') ||
      ability.can('operator', 'attendance');
    if (!allowed) throw redirect({ to: '/' });
  },
  component: () => <Outlet />,
});

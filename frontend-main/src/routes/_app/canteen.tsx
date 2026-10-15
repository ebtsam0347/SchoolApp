// /canteen — canteen workspace layout (gate only).
//
// The canteen desk is its own role: a clerk who records meals and looks
// after the canteen readers, without any attendance-operator rights. So
// this tree gates on the `canteen.*` permission family rather than
// reusing `attendance.operator`.
//
// Union of every subject a /canteen/* child gates on — children still
// enforce their own perms; this layout only decides who gets past the
// front door. Attendance managers are let through because canteen devices
// and canteen usage are part of the attendance estate they already own.
// Keep in sync with the Canteen group in nav-config.

import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { buildAbility } from '@/auth';
import { readSnapshot } from '@/lib';

export const Route = createFileRoute('/_app/canteen')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    const ability = buildAbility(snap.user);
    const checks: Array<[string, string]> = [
      ['read', 'canteen'],
      ['manage', 'canteen'],
      ['read', 'canteen.attendance'],
      ['write', 'canteen.attendance'],
      ['read', 'canteen.devices'],
      ['read', 'canteen.employees'],
      ['manage', 'attendance'],
    ];
    const allowed = checks.some(([verb, subject]) => ability.can(verb, subject));
    if (!allowed) throw redirect({ to: '/' });
  },
  component: () => <Outlet />,
});

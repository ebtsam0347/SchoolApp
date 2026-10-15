// /admin — layout route. Pure permission gate; no shared visual chrome.
//
// Earlier this layout rendered a "control plane" stripe header and a horizontal
// pill TabNav over every admin child route. Both were redundant: the breadcrumb
// already names the section, and the sidebar's Admin group already exposes
// every sub-route. Stripping them lets each child page (overview, users list,
// user detail, audit explorer, audit health, replay) own the layout that fits
// its content rather than inheriting a one-size shell.

import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { buildAbility } from '@/auth';
import { readSnapshot } from '@/lib';

export const Route = createFileRoute('/_app/admin')({
  beforeLoad: ({ location }) => {
    // Hiring Requisitions is a manager surface, not an admin one — it lives
    // in the Time nav group and is gated on the MANAGER role. It only sits
    // under /admin/* for historical URL reasons, so it must not inherit the
    // admin-tier gate below; otherwise a manager sees the sidebar row and
    // gets bounced to the dashboard on click. That route runs its own
    // `beforeLoad` role check (matching its `requiresRole` sidebar row), and
    // the backend stays the authority for rows and actions.
    if (
      location.pathname.replace(/\/$/, '') === '/admin/employee-requisition'
    ) {
      return;
    }

    const snap = readSnapshot();
    if (!snap?.user) return;
    const ability = buildAbility(snap.user);
    // Union of every subject any /admin/* child gates on. Children still
    // enforce their own perms; this layout just decides who's allowed past
    // the front door. Keep in sync with the Admin group in nav-config.
    // `manage attendance` is included so attendance managers can reach the
    // Devices catalog that lives under /admin/devices. Operator-only perms
    // (punch / shift-override) stay out — those routes live under /attendance.
    const checks: Array<[string, string]> = [
      ['read', 'auth.users'],
      ['read', 'auth.roles'],
      ['read', 'audit'],
      ['manage', 'audit'],
      ['manage', 'auth.sessions'],
      ['write', 'employee'],
      ['write', 'payroll'],
      ['manage', 'attendance'],
      ['announce', 'notification'],
      ['read', 'notification.template'],
    ];
    const allowed = checks.some(([verb, subject]) => ability.can(verb, subject));
    if (!allowed) throw redirect({ to: '/' });
  },
  component: () => <Outlet />,
});

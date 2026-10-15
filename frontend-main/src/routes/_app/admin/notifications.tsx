// /admin/notifications — admin layout shared by Announce + Templates.
//
// Pure pass-through; the children own their layout. The route guard reads
// the caller's ability snapshot synchronously and bounces unauthorized
// callers before the AuthProvider mounts. Server still enforces
// `notification.announce` / `notification.template.read` etc., this is
// just first-paint protection.

import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { buildAbility } from '@/auth';
import { readSnapshot } from '@/lib';

export const Route = createFileRoute('/_app/admin/notifications')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    const ability = buildAbility(snap.user);
    const allowed =
      ability.can('announce', 'notification') ||
      ability.can('read', 'notification.template') ||
      ability.can('write', 'notification.template');
    if (!allowed) throw redirect({ to: '/admin' });
  },
  component: () => <Outlet />,
});

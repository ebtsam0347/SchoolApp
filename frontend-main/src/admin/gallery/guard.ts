import { redirect } from '@tanstack/react-router';
import { readSnapshot } from '@/lib';

// Website image management is ADMIN-only. This only decides who sees the
// page; the Worker re-checks the ADMIN role on every /media/admin request.
export function requireAdminRole() {
  const snap = readSnapshot();
  if (snap?.user && !snap.user.roles.includes('ADMIN')) throw redirect({ to: '/' });
}

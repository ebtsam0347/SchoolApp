// /self-service/travel-request — layout for the Travel module.
//
// Only the Requests page lives here; Travel Expenses, Policy
// Administration and Grade Limits are tabs under /admin/lookups.
//
// Manager surface. Two gates, both keyed off the MANAGER role, and they must
// agree:
//   1. The sidebar row (`requiresRole` in nav-config.ts).
//   2. `beforeLoad` below, so typing the URL isn't a way around the sidebar.
// Both call the same `hasAnyRole` from @/auth. Neither has an admin bypass —
// an ADMIN / HR account without MANAGER does not get this page. Gating the
// layout covers every child route (/requests included).
//
// Approving a travel request is not here — those land in /inbox.

import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';

import { hasAnyRole } from '@/auth';
import { readSnapshot } from '@/lib';

/** Role that owns this page. Mirrors nav-config's `requiresRole`. */
const TRAVEL_REQUEST_ROLE = 'MANAGER';

export const Route = createFileRoute('/_app/self-service/travel-request')({
  beforeLoad: ({ location }) => {
    const snap = readSnapshot();
    if (snap?.user && snap.user.employeeLinked === false) {
      throw redirect({ to: '/account' });
    }
    // `_app` has already bounced guests, so a missing snapshot here means the
    // session is mid-bootstrap: let the page render and leave the redirect
    // to `_app`.
    if (snap?.user && !hasAnyRole([TRAVEL_REQUEST_ROLE], snap.user.roles)) {
      throw redirect({ to: '/' });
    }
    if (location.pathname.replace(/\/$/, '') === '/self-service/travel-request') {
      throw redirect({ to: '/self-service/travel-request/requests' });
    }
  },
  component: TravelRequestLayout,
});

function TravelRequestLayout() {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 20,
        width: '100%',
      }}
    >
      <Outlet />
    </div>
  );
}

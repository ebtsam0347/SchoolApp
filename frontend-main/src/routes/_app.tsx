// Auth-guarded layout: every route nested under `_app` requires an authed
// session. We redirect:
//   - guest                  → /login
//   - must-change-password   → /change-password
//
// The bootstrap path is "loading" (we have a token but haven't verified /me
// yet); render a thin placeholder so the AppShell doesn't paint with stale
// identity. Once AuthProvider settles into 'authed' the route re-renders.

import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { Spin } from 'antd';
import { AppShell } from '@/surfaces/AppShell';
import { useAuth } from '@/auth';
import { useHydratePersonaStore } from '@/dashboards/persona-store';
import { readSnapshot } from '@/lib';
import { useNotificationsStream, usePushBridge } from '@/notifications';
import { chatStreamHandlers } from '@/chat/stream-handlers';
import { attendanceStreamHandlers } from '@/attendance/stream-handlers';

export const Route = createFileRoute('/_app')({
  // Guard runs before AuthProvider mounts, so we read the storage snapshot
  // directly. AuthProvider then revalidates against /auth/me — if that 401s
  // it'll clear storage, the next navigation re-evaluates, and we land on
  // /login.
  beforeLoad: ({ location }) => {
    const snap = readSnapshot();
    // Signed-out visitors to the site root see the public school website;
    // any other portal URL still goes to the login screen.
    if (!snap) throw redirect({ to: location.pathname === '/' ? '/home' : '/login' });
    if (snap.user?.mustChangePassword) {
      throw redirect({ to: '/change-password' });
    }
  },
  component: AppLayout,
});

function AppLayout() {
  const auth = useAuth();
  // Long-lived /bff/notifications/stream subscription. The BFF multiplexes
  // notification, chat.* and presence.changed events onto this one stream;
  // we register the chat handlers here so a single connection drives both
  // the bell and the chat surface. The attendance handlers are inert until the
  // BFF starts forwarding attendance.* events (see attendanceStreamHandlers).
  useNotificationsStream({ ...chatStreamHandlers, ...attendanceStreamHandlers });
  // Web Push: keeps this browser's OS-notification subscription bound to the
  // signed-in user and routes notification clicks back into the app.
  usePushBridge();
  // Cross-tab sync for the dashboard persona override. Same-tab updates
  // flow through the Zustand store directly; this listener catches
  // localStorage writes from other tabs.
  useHydratePersonaStore();

  if (auth.status === 'loading') {
    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          height: '100dvh',
        }}
      >
        <Spin />
      </div>
    );
  }

  return (
    <AppShell>
      <Outlet />
    </AppShell>
  );
}

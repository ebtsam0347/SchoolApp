// Mounted once in the authed app shell. Connects the service worker to the
// running app:
//   - re-registers this browser's push subscription for the signed-in user,
//   - "push:open" (OS notification clicked while a tab is open) and the
//     ?pushId/?pushLink boot params (clicked with no tab open) → mark the
//     row read and navigate to its target,
//   - "push:received" (push arrived while this tab is focused) → in-app
//     toast instead of an OS popup.

import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { App } from 'antd';

import { useAuth } from '@/auth';

import { notificationsApi } from '../api';
import { resolveNotificationLink } from '../resolve-link';
import { PUSH_STATE_KEY, usePushConfig } from './usePushNotifications';
import { pushSupport, syncPush } from './web-push';

type PushPayload = {
  id?: number | null;
  title?: string;
  body?: string | null;
  link?: string | null;
  priority?: string;
};

type WorkerMessage =
  | { type: 'push:open'; id: number | null; link: string | null }
  | { type: 'push:received'; notification: PushPayload };

export function usePushBridge() {
  const auth = useAuth();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const { notification } = App.useApp();
  const config = usePushConfig();
  const userId = auth.user?.userId ?? null;
  const publicKey = config.data?.enabled ? config.data.publicKey : null;
  const supported = pushSupport() === 'supported';

  // Latest callbacks for the long-lived listeners below.
  const openRef = useRef<(id: number | null, link: string | null) => void>(() => {});
  openRef.current = (id, link) => {
    if (id != null) {
      notificationsApi
        .markRead(id)
        .then(() => qc.invalidateQueries({ queryKey: ['notifications'] }))
        .catch(() => {});
    }
    if (link) {
      const { to, search } = resolveNotificationLink(link);
      void navigate({ to, search });
    }
  };

  const toastRef = useRef<(n: PushPayload) => void>(() => {});
  toastRef.current = (n) => {
    void qc.invalidateQueries({ queryKey: ['notifications'] });
    const key = `push-${n.id ?? Date.now()}`;
    notification.open({
      key,
      title: n.title ?? 'New notification',
      description: n.body ?? undefined,
      placement: 'topRight',
      duration: n.priority === 'HIGH' ? 0 : 6,
      style: { cursor: n.link ? 'pointer' : undefined },
      onClick: () => {
        notification.destroy(key);
        openRef.current(n.id ?? null, n.link ?? null);
      },
    });
  };

  // Re-bind this browser's subscription to the signed-in user, once per user.
  useEffect(() => {
    if (!supported || !userId || !publicKey) return;
    syncPush(publicKey)
      .catch(() => false)
      .finally(() => void qc.invalidateQueries({ queryKey: PUSH_STATE_KEY }));
  }, [supported, userId, publicKey, qc]);

  // Messages from the service worker.
  useEffect(() => {
    if (!supported) return;
    const onMessage = (e: MessageEvent<WorkerMessage>) => {
      if (e.data?.type === 'push:open') openRef.current(e.data.id, e.data.link);
      else if (e.data?.type === 'push:received') toastRef.current(e.data.notification);
    };
    navigator.serviceWorker.addEventListener('message', onMessage);
    return () => navigator.serviceWorker.removeEventListener('message', onMessage);
  }, [supported]);

  // Opened from an OS notification with no tab open.
  useEffect(() => {
    if (!userId) return;
    const params = new URLSearchParams(window.location.search);
    const rawId = params.get('pushId');
    const link = params.get('pushLink');
    if (rawId === null && link === null) return;
    params.delete('pushId');
    params.delete('pushLink');
    const rest = params.toString();
    window.history.replaceState(null, '', window.location.pathname + (rest ? `?${rest}` : ''));
    const id = rawId !== null && /^\d+$/.test(rawId) ? Number(rawId) : null;
    openRef.current(id, link);
  }, [userId]);

  // Permission flipped in browser settings → refresh the toggle.
  useEffect(() => {
    if (!supported || !navigator.permissions) return;
    let status: PermissionStatus | null = null;
    const onChange = () => void qc.invalidateQueries({ queryKey: PUSH_STATE_KEY });
    navigator.permissions
      .query({ name: 'notifications' })
      .then((s) => {
        status = s;
        s.addEventListener('change', onChange);
      })
      .catch(() => {});
    return () => status?.removeEventListener('change', onChange);
  }, [supported, qc]);
}

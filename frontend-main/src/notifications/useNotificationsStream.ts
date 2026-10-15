// Mounts one notification SSE connection for the authed app session.
// Plug into a route component that's only rendered when the user is signed
// in (`/_app` layout) so unauthenticated routes don't open dangling streams.
//
// Extra event handlers can be passed in — the BFF multiplexes notifications,
// chat.* and presence.changed onto the same `/bff/notifications/stream`
// connection, so feature modules register their handlers here rather than
// opening their own SSE.

import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@/auth';
import { openNotificationStream, type LiveStreamHandlers } from './stream';

export function useNotificationsStream(extraHandlers: LiveStreamHandlers = {}) {
  const auth = useAuth();
  const qc = useQueryClient();
  const userId = auth.user?.userId ?? null;
  // Stream is currently scoped to employee-linked users; opening it for
  // admin-only accounts produces silent 403s on every page load.
  const employeeLinked = auth.user?.employeeLinked === true;
  // Pin handlers in a ref so identity changes don't tear down the SSE
  // connection. Callers should pass stable module-level objects (e.g.
  // `chatStreamHandlers`); the ref is defence in depth.
  const handlersRef = useRef(extraHandlers);
  handlersRef.current = extraHandlers;

  useEffect(() => {
    if (!userId || !employeeLinked) return;
    const handle = openNotificationStream(qc, handlersRef.current);
    return () => handle.close();
  }, [userId, employeeLinked, qc]);
}

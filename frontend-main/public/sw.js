// Spirit of Islam School service worker — Web Push only.
//
// Shows an OS notification (Windows Action Center, macOS Notification
// Center, Android shade) for every push notification-service sends, even when
// no app tab is open. No fetch handler and no caching: the app itself is
// untouched by this worker.
//
// Payload (JSON, mirrors the SSE `notification` event):
//   { id, type, title, body, link, category, priority, createdAt }
//
// When an app tab is focused we skip the OS popup and hand the payload to
// the page, which shows an in-app toast instead. Chrome allows skipping the
// notification while a page of the origin is visible.

const ICON = '/icons/icon-192.png';
const BADGE = '/icons/badge-96.png';

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('push', (event) => {
  event.waitUntil(handlePush(event));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const { id = null, link = null } = event.notification.data || {};
  event.waitUntil(openApp(id, link));
});

async function handlePush(event) {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = { body: event.data ? event.data.text() : '' };
  }

  const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  const focused = windows.find((c) => c.focused && c.visibilityState === 'visible');
  if (focused) {
    focused.postMessage({ type: 'push:received', notification: data });
    return;
  }

  const createdAt = data.createdAt ? Date.parse(data.createdAt) : NaN;
  await self.registration.showNotification(data.title || 'The Spirit of Islam School', {
    body: data.body || '',
    icon: ICON,
    badge: BADGE,
    // One OS entry per notification row; a redelivered push replaces it.
    tag: data.id != null ? `fshrms-${data.id}` : undefined,
    // Urgent items (approvals waiting on you) stay until dismissed.
    requireInteraction: data.priority === 'HIGH',
    timestamp: Number.isNaN(createdAt) ? Date.now() : createdAt,
    data: { id: data.id ?? null, link: data.link ?? null },
  });
}

async function openApp(id, link) {
  const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  const existing = windows.find((c) => new URL(c.url).origin === self.location.origin);
  if (existing) {
    // Reuse the open tab: the page resolves the link and marks the row read.
    await existing.focus();
    existing.postMessage({ type: 'push:open', id, link });
    return;
  }
  // No tab open — boot the app with the target in the query string; the
  // app shell picks it up after sign-in checks.
  const url = new URL('/', self.location.origin);
  if (id != null) url.searchParams.set('pushId', String(id));
  if (link) url.searchParams.set('pushLink', link);
  await self.clients.openWindow(url.href);
}

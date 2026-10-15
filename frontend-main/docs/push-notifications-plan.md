# Push notifications (Web Push)

Every notification the bell gets is also delivered as an OS notification
(Windows Action Center, macOS Notification Center, Android shade, iOS
home-screen app), **even when no FSHRMS tab is open**. The user turns it on
once per browser ("Turn on" in the bell panel, or the switch on
`/account/notifications`); the per-type preferences on that page still decide
which notifications exist at all.

This replaces the earlier in-app-only plan: Web Push, the service worker and
device registration are now in scope.

## How it flows

```
notification-service                       browser
─────────────────────                      ───────
NotificationService persists row
  └ NotificationPublisher.publish()
      ├ Redis → BFF SSE → bell (open tabs)
      └ PushService.dispatch()  ── after commit, virtual thread ──►
          encrypt (RFC 8291) + VAPID (RFC 8292)
          POST to push service (FCM / Mozilla / Apple / WNS) ──► public/sw.js `push`
                                                                  ├ FSHRMS tab focused → postMessage → antd toast
                                                                  └ otherwise → showNotification (OS popup)
                                                              click → focus tab + navigate, or open /?pushId&pushLink
```

## Frontend

| Piece | File |
|---|---|
| Service worker (push + click only, no caching) | `public/sw.js` |
| Manifest + icons (install on Android, required for iOS push) | `public/manifest.webmanifest`, `public/icons/*` |
| Endpoints | `src/notifications/push/api.ts` |
| Subscribe / sync / release, support detection | `src/notifications/push/web-push.ts` |
| Opt-in state shared by bell + settings | `src/notifications/push/usePushNotifications.ts` |
| App-shell bridge: re-bind on sign-in, click → navigate + mark read, focused → toast | `src/notifications/push/usePushBridge.ts` (mounted in `routes/_app.tsx`) |
| UI: bell banner + settings card | `src/notifications/push/PushOptIn.tsx` |
| Sign-out unbinds this browser from the user | `src/auth/AuthProvider.tsx` → `releasePushOnLogout` |

Lifecycle rules:

- The permission prompt only ever comes from a click ("Turn on" / the switch).
- On every sign-in the existing browser subscription is POSTed again. That
  re-binds a shared PC to whoever signed in and picks up a subscription the
  browser rotated while the app was closed. It never creates a subscription.
- Sign-out tells the server to forget the browser but keeps the browser's
  subscription, so the next sign-in restores push without a prompt.
- "Turn off" forgets it server-side and drops the browser subscription.

## Backend (notification-service, branch `feature/web-push`)

- `V0009__push_subscriptions.sql`: one row per browser, `endpoint` unique.
- `GET /notifications/push/config` → `{ enabled, publicKey }`
- `POST /notifications/push/subscriptions` `{ endpoint, p256dh, auth, userAgent }` → 204 (upsert, re-binds to caller)
- `POST /notifications/push/unsubscribe` `{ endpoint }` → 204 (idempotent)
- `push/WebPushCrypto`, `push/VapidSigner`, `push/WebPushClient`: JDK crypto only, no new
  dependency. Covered by the RFC 8291 Appendix A test vector.
- Only `https` endpoints on the push-service allow-list are accepted/contacted
  (FCM, Mozilla, Apple, WNS), so a client can't make the server POST to an internal URL.
- 404/410 from a push service deletes the subscription. No retries: the row is
  still in the bell.

## Deploying

1. **VAPID keys, once per environment** (UAT and prod get different pairs):
   `npx web-push generate-vapid-keys`. Changing a pair later silently kills every
   existing subscription (users must turn push on again), so generate once.
2. Put `VAPID_PUBLIC_KEY` and `VAPID_PRIVATE_KEY` in `notification-secret`
   (sealed secret per overlay). Optional `VAPID_SUBJECT`
   (default `mailto:it@forwardsports.com`). Without both keys push stays off and
   the UI hides the opt-in.
3. **Egress**: the notification pod must reach `fcm.googleapis.com`,
   `*.push.services.mozilla.com`, `*.push.apple.com` and `*.notify.windows.com` on 443.
4. **HTTPS is required** by browsers. Prod (`https://hrms.forward.pk`) is fine.
   UAT (`http://*.uat.hrms.local`) can't use push. `localhost` works for development.
5. nginx already serves `/sw.js` with `Cache-Control: no-store`, so service-worker
   updates reach users on their next visit.

## Platform notes

- **Windows**: Chrome/Edge use native Windows notifications. If nothing shows:
  Settings → System → Notifications → Google Chrome (or Edge) On, and
  Do not disturb off. The settings card says this.
- **Android (Chrome)**: works in the browser; installing the app is optional.
- **iPhone/iPad**: iOS 16.4+, and only after Share → Add to Home Screen and
  opening FSHRMS from that icon. The settings card detects this and explains it.
- Browser must be running (Chrome keeps a background process on Windows by default);
  a message sent while the device is offline is kept for 24h (`ttl`).

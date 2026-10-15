// Browser side of Web Push: service-worker registration and the push
// subscription lifecycle. No React here, so AuthProvider can call
// `releasePushOnLogout` without importing hooks.
//
// Lifecycle:
//   - "Turn on"  → permission prompt → PushManager.subscribe → POST to server.
//   - App start  → if this browser already has a subscription, POST it again.
//                  That re-binds it to whoever is signed in now and picks up a
//                  subscription the browser rotated while the app was closed.
//   - Sign out   → tell the server to forget it, keep the browser subscription
//                  so signing back in restores push without another prompt.
//   - "Turn off" → tell the server, then drop the browser subscription too.

import { pushApi } from './api';

const SW_URL = '/sw.js';

export type PushSupport =
  | 'supported'
  /** Push needs HTTPS (or localhost). */
  | 'insecure'
  /** iPhone/iPad Safari only allows push for a home-screen app. */
  | 'ios-needs-install'
  | 'unsupported';

export function pushSupport(): PushSupport {
  if (typeof window === 'undefined') return 'unsupported';
  if (!window.isSecureContext) return 'insecure';
  const hasApis =
    'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
  if (isIos() && !isStandalone()) return 'ios-needs-install';
  return hasApis ? 'supported' : 'unsupported';
}

function isIos(): boolean {
  // iPadOS reports as Mac; touch points tell it apart.
  return (
    /iPad|iPhone|iPod/.test(navigator.userAgent) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
  );
}

function isStandalone(): boolean {
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  );
}

let registration: Promise<ServiceWorkerRegistration> | null = null;

/** Registers `/sw.js` once per page load. */
export function registerServiceWorker(): Promise<ServiceWorkerRegistration> {
  registration ??= navigator.serviceWorker
    .register(SW_URL, { scope: '/' })
    .then(() => navigator.serviceWorker.ready);
  return registration;
}

export async function currentSubscription(): Promise<PushSubscription | null> {
  if (pushSupport() !== 'supported') return null;
  const reg = await navigator.serviceWorker.getRegistration('/');
  return (await reg?.pushManager.getSubscription()) ?? null;
}

/**
 * Ask for permission (call from a click handler — browsers ignore or
 * quiet prompts that aren't user-initiated), subscribe, and register the
 * subscription with the server. Resolves to the resulting permission.
 */
export async function enablePush(publicKey: string): Promise<NotificationPermission> {
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') return permission;
  const reg = await registerServiceWorker();
  const sub = await ensureSubscription(reg, publicKey);
  await pushApi.subscribe(toRequest(sub));
  return permission;
}

/** Stop push on this browser for everyone who signs in here. */
export async function disablePush(): Promise<void> {
  const sub = await currentSubscription();
  if (!sub) return;
  try {
    await pushApi.unsubscribe(sub.endpoint);
  } finally {
    await sub.unsubscribe();
  }
}

/**
 * App-start sync: re-register an existing subscription for the signed-in
 * user. Never prompts and never creates a subscription the user didn't ask
 * for. Returns whether push is on for this browser.
 */
export async function syncPush(publicKey: string): Promise<boolean> {
  if (Notification.permission !== 'granted') return false;
  const reg = await registerServiceWorker();
  const existing = await reg.pushManager.getSubscription();
  if (!existing) return false;
  const sub = await ensureSubscription(reg, publicKey);
  await pushApi.subscribe(toRequest(sub));
  return true;
}

/** Best-effort, bounded: sign-out must not hang on a slow network. */
export async function releasePushOnLogout(): Promise<void> {
  try {
    const sub = await currentSubscription();
    if (!sub) return;
    await Promise.race([
      pushApi.unsubscribe(sub.endpoint),
      new Promise((resolve) => setTimeout(resolve, 3000)),
    ]);
  } catch {
    // The server drops it on the next failed delivery anyway.
  }
}

/** Reuse the browser's subscription unless it was made with another server key. */
async function ensureSubscription(
  reg: ServiceWorkerRegistration,
  publicKey: string,
): Promise<PushSubscription> {
  const key = base64UrlToBytes(publicKey);
  const existing = await reg.pushManager.getSubscription();
  if (existing) {
    const current = existing.options.applicationServerKey;
    if (current && sameBytes(new Uint8Array(current), key)) return existing;
    // VAPID keys were rotated server-side; the old subscription is dead.
    await existing.unsubscribe();
  }
  return reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key });
}

function toRequest(sub: PushSubscription) {
  const json = sub.toJSON();
  return {
    endpoint: sub.endpoint,
    p256dh: json.keys?.p256dh ?? '',
    auth: json.keys?.auth ?? '',
    userAgent: navigator.userAgent.slice(0, 512),
  };
}

function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const b64 = value.replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(b64 + '='.repeat((4 - (b64.length % 4)) % 4));
  const out = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

function sameBytes(a: Uint8Array, b: Uint8Array): boolean {
  return a.length === b.length && a.every((v, i) => v === b[i]);
}

// notification-service Web Push endpoints (`/notifications/push/**`).

import { http } from '@/lib/http';

export type PushConfig = {
  /** False until the server has VAPID keys — the opt-in stays hidden. */
  enabled: boolean;
  /** VAPID public key (base64url) for `applicationServerKey`. */
  publicKey: string | null;
};

export type PushSubscribeRequest = {
  endpoint: string;
  p256dh: string;
  auth: string;
  userAgent?: string;
};

export const pushApi = {
  config: (signal?: AbortSignal) =>
    http.get<PushConfig>('/notifications/push/config', { signal }),
  /** Upsert by endpoint; re-binds the browser to the caller. */
  subscribe: (body: PushSubscribeRequest) =>
    http.post<void>('/notifications/push/subscriptions', body),
  /** Idempotent. */
  unsubscribe: (endpoint: string) =>
    http.post<void>('/notifications/push/unsubscribe', { endpoint }),
};

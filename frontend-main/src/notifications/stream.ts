// Long-lived SSE consumer for `/bff/notifications/stream`.
//
// Subscribes once for the authenticated session, attaches the bearer JWT
// from `auth-storage`, and dispatches each forwarded SSE event by name to a
// registered handler. The bell + inbox handler is built in (`notification`
// event); chat handlers register additional events on the same connection
// — the BFF multiplexes notifications, chat.* and presence.changed onto one
// `/bff/notifications/stream`.
//
// We use `@microsoft/fetch-event-source` rather than the native `EventSource`
// so we can:
//   - send `Authorization: Bearer …` (native EventSource has no header API),
//   - run our own retry/backoff (the server keeps a 10-min timeout safety
//     net so the connection will close periodically),
//   - drop and reopen the connection cleanly when the access token rotates
//     (silent-refresh in `http.ts` writes a new snapshot; we listen on
//     storage and reconnect).
//
// Mounting: called from `_app/index` (or any authed surface) — see the hook.
// One active connection per tab is enough.

import { fetchEventSource } from '@microsoft/fetch-event-source';
import type { QueryClient } from '@tanstack/react-query';
import { readSnapshot, subscribeAuthStorage } from '@/lib';

const STREAM_PATH = '/bff/notifications/stream';
const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? '';

type StreamHandle = {
  close: () => void;
};

/** A handler may parse the JSON payload itself; we pass the raw string + the QC. */
export type LiveStreamHandler = (qc: QueryClient, raw: string) => void;

export type LiveStreamHandlers = Record<string, LiveStreamHandler>;

class FatalError extends Error {}

/**
 * Default handler — invalidates the bell + inbox query keys. Kept exported so
 * callers can compose it into a wider handler map without re-implementing.
 */
export const defaultNotificationHandler: LiveStreamHandler = (qc) => {
  // We don't trust the in-event payload to be schema-stable yet — refetch is
  // cheap and authoritative. Invalidate everything under the
  // `notifications` key.
  void qc.invalidateQueries({ queryKey: ['notifications'] });
};

/**
 * Open one persistent SSE connection. Returns a handle whose `close()` aborts
 * cleanly. Internally:
 *   - reconnects on transient errors with capped exponential backoff (1s →
 *     30s),
 *   - reopens immediately when the bearer token rotates (storage event from
 *     `http.ts` after silent refresh),
 *   - gives up only on 401/403 — those mean the session is gone, and the
 *     auth layer will have already scheduled a /login redirect.
 *
 * @param qc       react-query client used for invalidations
 * @param handlers extra event-name → handler map; merged with the built-in
 *                 `notification` handler. Pass `chatStreamHandlers` from
 *                 `src/chat/stream-handlers.ts` to drive the chat surface.
 */
export function openNotificationStream(
  qc: QueryClient,
  handlers: LiveStreamHandlers = {},
): StreamHandle {
  const dispatch: LiveStreamHandlers = {
    notification: defaultNotificationHandler,
    ...handlers,
  };
  let aborter = new AbortController();
  let closed = false;
  let backoffMs = 1000;
  const MAX_BACKOFF_MS = 30_000;

  // Reopen the stream when the token snapshot changes (login in another tab,
  // silent refresh, logout). We re-trigger `connect()` after aborting; the
  // new connection picks up the latest bearer.
  const unsubStorage = subscribeAuthStorage(() => {
    if (closed) return;
    aborter.abort();
    aborter = new AbortController();
    backoffMs = 1000;
    void connect();
  });

  async function connect(): Promise<void> {
    if (closed) return;
    const snap = readSnapshot();
    if (!snap?.tokens.accessToken || !snap.user) return;

    try {
      await fetchEventSource(`${BASE}${STREAM_PATH}`, {
        signal: aborter.signal,
        // BFF responds 401 unless the gateway has populated identity headers,
        // which it does only when the bearer is valid. Send it explicitly so
        // direct-to-BFF dev setups also work.
        headers: { Authorization: `Bearer ${snap.tokens.accessToken}` },
        // The default `openWhenHidden: false` pauses retries when the tab
        // backgrounds. We want the bell to be live the moment the user
        // returns to the tab, which the library already handles for us.
        onopen: async (res) => {
          if (res.ok && res.headers.get('content-type')?.includes('text/event-stream')) {
            backoffMs = 1000;
            return;
          }
          if (res.status === 401 || res.status === 403) {
            // Session is gone. AuthProvider/http.ts will redirect on the
            // next gated request — give up so we don't hammer the BFF.
            throw new FatalError(`stream auth failed (${res.status})`);
          }
          throw new Error(`stream open failed (${res.status})`);
        },
        onmessage: (ev) => {
          // 'ready' / comment-line keepalives have no event name we care
          // about — they fall through silently. Everything else routes
          // through the dispatch table.
          const handler = ev.event ? dispatch[ev.event] : undefined;
          if (handler) handler(qc, ev.data ?? '');
        },
        onerror: (err) => {
          if (err instanceof FatalError) throw err;
          // Returning normally lets the library retry. We also bump our own
          // backoff so a flapping BFF doesn't spin.
          const next = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
          backoffMs = next;
          return next;
        },
        onclose: () => {
          if (!closed) {
            // Server-driven disconnect (10-min timeout safety net) — reopen
            // after the current backoff so the keepalive cycle continues.
            setTimeout(() => {
              if (!closed) void connect();
            }, backoffMs);
          }
        },
      });
    } catch (err) {
      if (err instanceof FatalError || closed) return;
      // Transient error path — the library already calls onerror; if it
      // bubbled here something genuinely failed before we could even try.
      setTimeout(() => {
        if (!closed) void connect();
      }, backoffMs);
    }
  }

  void connect();

  return {
    close: () => {
      closed = true;
      unsubStorage();
      aborter.abort();
    },
  };
}

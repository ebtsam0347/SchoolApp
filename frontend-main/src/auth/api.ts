// Typed wrappers around the auth-service endpoints we consume from the SPA.
// Mirrors `docs/services/auth.openapi.json` — keep in sync if the contract
// changes. Login/refresh/logout/changePassword live on AuthProvider; the
// helpers below are for self-service surfaces under /account.
//
// `timelineApi` and `selfSignInEventsApi` actually live in audit-service,
// not auth — but they're consumed exclusively by the /account screens, so
// they ride alongside the auth wrappers for ergonomic imports.

import { http } from '@/lib/http';

export type DeviceFamily = 'mobile' | 'tablet' | 'desktop' | 'unknown';

export type BrowserInfo = { name: string; version: string | null };
export type OsInfo = { name: string; version: string | null };

export type SessionDto = {
  id: number;
  deviceLabel: string;
  /** Server-parsed device family — replaces frontend UA sniffing. */
  deviceFamily: DeviceFamily;
  /** Server-parsed browser. Null when the UA is opaque. */
  browser: BrowserInfo | null;
  /** Server-parsed OS. Null when the UA is opaque. */
  os: OsInfo | null;
  ipAddress: string;
  userAgent: string;
  issuedAt: string;
  lastSeenAt: string;
  expiresAt: string;
  current: boolean;
  /** Set when this session was revoked. Stays in the listing for ~7 days. */
  revokedAt: string | null;
  /** Username of the revoker (admin or self). Pairs with `revokedAt`. */
  revokedBy: string | null;
};

export type LoginHistoryAction =
  | 'login.success'
  | 'login.failed'
  | 'user.locked'
  | 'user.unlocked';

// Audit-service timeline row (user-visible events). The route
// `/timeline/user/{userId}` is open to the actor for their own user entity.
export type TimelineEntry = {
  id: number;
  action: string;
  actorUsername: string;
  actorLabel: string;
  message: string;
  occurredAt: string;
};

export type TimelinePage = {
  content: TimelineEntry[];
  nextCursor: string | null;
};

export type RevokedSessionsResponse = {
  revoked: SessionDto[];
};

export const authApi = {
  listSessions: (signal?: AbortSignal) =>
    http.get<SessionDto[]>('/auth/sessions', { signal }),
  /** Returns the killed `SessionDto` for inline confirmation. */
  revokeSession: (id: number) => http.del<SessionDto>(`/auth/sessions/${id}`),
  /**
   * `exceptCurrent: true` keeps the calling session alive — "sign out everywhere else".
   * Returns the list of sessions that were actually killed so the UI can show
   * "Signed out 3 sessions: Chrome on macOS, Safari on iOS, …".
   */
  revokeAllSessions: (exceptCurrent = false) =>
    http.del<RevokedSessionsResponse>(
      `/auth/sessions${exceptCurrent ? '?except=current' : ''}`,
    ),
};

export const timelineApi = {
  forUser: (
    userId: number,
    opts: { from?: string; to?: string; limit?: number; cursor?: string } = {},
    signal?: AbortSignal,
  ) => {
    const q = new URLSearchParams();
    if (opts.from) q.set('from', opts.from);
    if (opts.to) q.set('to', opts.to);
    if (opts.limit) q.set('limit', String(opts.limit));
    if (opts.cursor) q.set('cursor', opts.cursor);
    const qs = q.toString();
    return http.get<TimelinePage>(
      `/timeline/user/${userId}${qs ? `?${qs}` : ''}`,
      { signal },
    );
  },
};

// Caller-scoped login feed — success + failed + locked + unlocked. Bypasses
// the `userVisible` filter (so the user can see their own failed attempts)
// and the `audit.read` permission (the actor is implicit). Sibling to
// `/timeline/user/{id}` but lives at /timeline/self/sign-in-events.
export type SelfSignInEvent = {
  occurredAt: string;
  action: LoginHistoryAction;
  success: boolean;
  ipAddress: string | null;
  userAgent: string | null;
  reason: string | null;
};

export type SelfSignInEventPage = {
  content: SelfSignInEvent[];
  nextCursor: string | null;
};

export const selfSignInEventsApi = {
  list: (
    opts: { from?: string; to?: string; limit?: number; cursor?: string } = {},
    signal?: AbortSignal,
  ) => {
    const q = new URLSearchParams();
    if (opts.from) q.set('from', opts.from);
    if (opts.to) q.set('to', opts.to);
    if (opts.limit) q.set('limit', String(opts.limit));
    if (opts.cursor) q.set('cursor', opts.cursor);
    const qs = q.toString();
    return http.get<SelfSignInEventPage>(
      `/timeline/self/sign-in-events${qs ? `?${qs}` : ''}`,
      { signal },
    );
  },
};

// BFF composites for chat. One round-trip per page surface, plus a
// generic userId → identity resolver shared by every chat surface.
//
// Backed by `services/bff/.../ChatBffController.java` — see
// `docs/services/bff.openapi.json` for response shapes.

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';
import type { ChatInboxResponse, UsersByIdsResponse } from './types';

export const chatBff = {
  /**
   * Single-call shape for first paint of the chat surface — conversations
   * list + every member resolved + presence snapshot. Use this on the
   * conversation list pane; subsequent member resolution can use
   * `usersByIds`.
   */
  inbox: (signal?: AbortSignal) =>
    http.get<ChatInboxResponse>('/bff/chat/inbox', { signal }),

  /**
   * Resolve a `userId` array → identity summaries. PII-safe; no
   * permission gate beyond authenticated. Server is order-preserving and
   * silently drops ids that don't resolve.
   */
  usersByIds: (ids: number[], signal?: AbortSignal) =>
    http.get<UsersByIdsResponse>(
      `/bff/users/by-ids${qs({ ids: ids.join(',') })}`,
      { signal },
    ),

  /**
   * Substring search on username + fullName, ACTIVE users only. Composes
   * auth-service's `/users/search` with employee avatars. Authenticated;
   * no admin/HR perm. Server caps at 50.
   *
   * Same response shape as `usersByIds` so the resolver code path is
   * identical — drop-in for any chat picker that needs to discover users.
   */
  usersSearch: (q: string, size = 20, signal?: AbortSignal) =>
    http.get<UsersByIdsResponse>(
      `/bff/users/search${qs({ q, size })}`,
      { signal },
    ),
};

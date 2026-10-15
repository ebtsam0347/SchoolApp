// Aggregated BFF endpoints (services/bff/, gateway-mounted at /bff/**).
// One round trip replaces a fan-out — useful where panels were previously
// painted by parallel queries that all waterfalled through gateway + JWT
// verify. Each composite is gated by header-identity at the gateway; the
// downstream services still enforce their own @RequirePermission, so 403s
// surface for the same reasons they did before.
//
// Wishlist items A.1 (account summary) and B.A2 (admin user overview) —
// shipped 2026-04-29.

import { http } from '@/lib/http';
import type { UserIdentity } from './types';
import type { SessionDto, TimelineEntry } from './api';
import type { LoginHistoryEntry, RoleDto, UserDto } from './admin-api';
import type { AuditEventDto } from '@/audit/api';

export type AccountSummary = {
  identity: UserIdentity;
  sessions: {
    active: number;
    current: SessionDto;
    others: SessionDto[];
  };
  recentEvents: TimelineEntry[];
};

export type AdminUserOverview = {
  user: UserDto;
  roles: RoleDto[];
  sessions: SessionDto[];
  loginHistory: LoginHistoryEntry[];
  recentActions: AuditEventDto[];
};

export const bffApi = {
  accountSummary: (signal?: AbortSignal) =>
    http.get<AccountSummary>('/bff/account/summary', { signal }),
  adminUserOverview: (id: number, signal?: AbortSignal) =>
    http.get<AdminUserOverview>(`/bff/admin/users/${id}/overview`, { signal }),
};

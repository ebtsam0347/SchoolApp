// Typed wrappers for the admin surfaces of auth-service. Mirrors
// `docs/services/auth.openapi.json` — the user-self-service wrappers live in
// `./api.ts`. Pagination uses the Spring `page`/`size`/`sort` triplet, response
// is `PageResponseUserDto`. All requests go through the gateway (no /api/v1
// prefix) and rely on `src/lib/http.ts` for auth + idempotency.

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';
import type { LoginHistoryAction, SessionDto } from './api';

export type UserStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED';

export type UserDto = {
  id: number;
  username: string;
  fullName: string;
  email: string | null;
  status: UserStatus;
  mustChangePw: boolean;
  employeeLinked: boolean;
  roles: string[];
  lastLoginAt: string | null;
  lockedUntil: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateUserRequest = {
  username: string;
  fullName: string;
  email?: string | null;
  temporaryPassword: string;
  employeeLinked?: boolean;
  employeeId?: number | null;
  roles?: string[];
};

export type UpdateUserRequest = {
  fullName?: string;
  email?: string | null;
  status?: UserStatus;
};

export type AttachRolesRequest = {
  roles: string[];
};

export type PasswordResetRequest = {
  temporaryPassword: string;
};

export type IssueServiceTokenRequest = {
  /** Token lifetime in days. Server caps at 730 (~2 years). */
  ttlDays: number;
};

export type ServiceTokenResponse = {
  /** Bearer JWT, RS256-signed. Returned ONCE — capture immediately. */
  token: string;
  /** ISO-8601 expiry. */
  expiresAt: string;
  /** JWT id, useful for grepping audit logs after the fact. */
  jti: string;
};

export type DisableUserRequest = {
  /**
   * Why the user is being disabled. Flows into the session-revoke reason and
   * the auth.user.deactivated outbox event. Defaults server-side to
   * ADMIN_REVOKE — pass this only when the call is part of a higher-level
   * flow (e.g. termination saga sends EMPLOYEE_TERMINATED).
   */
  reason?:
    | 'ADMIN_REVOKE'
    | 'USER_REVOKE'
    | 'EMPLOYEE_TERMINATED'
    | 'EMPLOYEE_ESS_REVOKED'
    | 'PASSWORD_CHANGED'
    | 'EXPIRED'
    | 'USER_LOGOUT';
};

export type PageResponseUserDto = {
  content: UserDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  sort: string;
};

export type RoleDto = {
  id: number;
  code: string;
  name: string;
  description: string | null;
  permissions: string[];
  /** Seeded roles (EMPLOYEE | MANAGER | HR | ADMIN) refuse delete. */
  system: boolean;
  /** Machine-only role — issued for service-to-service tokens, not assigned to humans. */
  serviceAccount: boolean;
};

export type PermissionDto = {
  id: number;
  code: string;
  name: string;
  /** Server-decided UI label, e.g. "Identity" instead of `auth.users`. */
  group: string;
  /** 1-line plain-English explanation. */
  description: string;
};

export type CreateRoleRequest = {
  code: string;
  name: string;
  description?: string | null;
  permissions: string[];
};

export type UpdateRoleRequest = {
  name?: string;
  description?: string | null;
  permissions?: string[];
};

export type LoginHistoryEntry = {
  occurredAt: string;
  action: LoginHistoryAction;
  success: boolean;
  ipAddress: string | null;
  userAgent: string | null;
  reason: string | null;
};

export type LoginHistoryPage = {
  content: LoginHistoryEntry[];
  nextCursor: string | null;
};

type ListUsersParams = {
  page?: number;
  size?: number;
  sort?: string;
  status?: UserStatus | null;
  /** Case-insensitive contains across username | fullName | email. */
  q?: string | null;
  /** Exact role-code match (single). */
  role?: string | null;
  /** Tri-state: omit (any) | true (linked only) | false (standalone only). */
  employeeLinked?: boolean | null;
};

export type UserStats = {
  total: number;
  active: number;
  locked: number;
  disabled: number;
  withMustChangePw: number;
  employeeLinked: number;
  standalone: number;
};

type LoginHistoryParams = {
  from?: string;
  to?: string;
  limit?: number;
  cursor?: string;
};

export const adminUsersApi = {
  list: (params: ListUsersParams = {}, signal?: AbortSignal) =>
    http.get<PageResponseUserDto>(
      `/admin/users${qs({
        page: params.page,
        size: params.size,
        sort: params.sort,
        status: params.status ?? undefined,
        q: params.q ?? undefined,
        role: params.role ?? undefined,
        employeeLinked: params.employeeLinked ?? undefined,
      })}`,
      { signal },
    ),
  stats: (signal?: AbortSignal) =>
    http.get<UserStats>('/admin/users/stats', { signal }),
  get: (id: number, signal?: AbortSignal) =>
    http.get<UserDto>(`/admin/users/${id}`, { signal }),
  create: (body: CreateUserRequest) =>
    http.post<UserDto>('/admin/users', body),
  update: (id: number, body: UpdateUserRequest) =>
    http.patch<UserDto>(`/admin/users/${id}`, body),
  attachRoles: (id: number, body: AttachRolesRequest) =>
    http.post<UserDto>(`/admin/users/${id}/roles`, body),
  detachRole: (id: number, roleId: number) =>
    http.del<UserDto>(`/admin/users/${id}/roles/${roleId}`),
  resetPassword: (id: number, body: PasswordResetRequest) =>
    http.post<void>(`/admin/users/${id}/password/reset`, body),
  unlock: (id: number) =>
    http.post<void>(`/admin/users/${id}/unlock`),
  /**
   * Idempotent. Flips status=DISABLED, revokes active sessions, emits
   * `auth.user.deactivated`. Body is optional — server defaults `reason`
   * to ADMIN_REVOKE.
   */
  disable: (id: number, body: DisableUserRequest = {}) =>
    http.post<void>(`/admin/users/${id}/disable`, body),
  /**
   * Mint a long-lived service-account JWT. Returned once, never stored —
   * capture, seal into the consumer secret, restart the consumer.
   */
  issueServiceToken: (id: number, body: IssueServiceTokenRequest) =>
    http.post<ServiceTokenResponse>(
      `/admin/users/${id}/service-tokens`,
      body,
    ),
  listSessions: (id: number, signal?: AbortSignal) =>
    http.get<SessionDto[]>(`/admin/users/${id}/sessions`, { signal }),
  loginHistory: (id: number, params: LoginHistoryParams = {}, signal?: AbortSignal) =>
    http.get<LoginHistoryPage>(
      `/admin/users/${id}/login-history${qs({
        from: params.from,
        to: params.to,
        limit: params.limit,
        cursor: params.cursor,
      })}`,
      { signal },
    ),
};

export const adminSessionsApi = {
  /** Returns the killed `SessionDto` for inline confirmation. */
  revoke: (id: number) => http.del<SessionDto>(`/admin/sessions/${id}`),
};

export const rolesApi = {
  list: (signal?: AbortSignal) => http.get<RoleDto[]>('/admin/roles', { signal }),
  create: (body: CreateRoleRequest) => http.post<RoleDto>('/admin/roles', body),
  update: (id: number, body: UpdateRoleRequest) =>
    http.patch<RoleDto>(`/admin/roles/${id}`, body),
  delete: (id: number) => http.del<void>(`/admin/roles/${id}`),
};

export const permissionsApi = {
  list: (signal?: AbortSignal) =>
    http.get<PermissionDto[]>('/admin/permissions', { signal }),
  /** Reverse lookup — which roles include this permission. */
  rolesByCode: (code: string, signal?: AbortSignal) =>
    http.get<RoleDto[]>(
      `/admin/permissions/${encodeURIComponent(code)}/roles`,
      { signal },
    ),
};

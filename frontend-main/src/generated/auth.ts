/* eslint-disable */
/**
 * GENERATED FILE — do not edit by hand.
 * Source: docs/services/<group>.openapi.json (refresh via `npm run gen:api:fetch`).
 * Regenerate: `npm run gen:api`.
 */

export interface paths {
    "/auth/refresh": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Mint a new access + refresh pair
         * @description Rotates the refresh token. The previous refresh token is revoked atomically.
         */
        post: operations["refresh"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/password/change": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Change my password
         * @description Validates the current password, applies the password policy, re-hashes (bcrypt cost 12), clears the must-change-password flag, rotates the caller's session refresh token, and returns a fresh access + refresh token pair so the FE can replace its cached tokens in one round-trip — the previous 204 left the old `pwChangeRequired: true` JWT in play until the next refresh.
         */
        post: operations["change"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/logout": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * End the current session
         * @description Revokes the refresh token and the backing session row. Idempotent.
         */
        post: operations["logout"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Log in with username + password
         * @description Returns an access + refresh token pair plus session id.
         *     Failed attempts increment a counter and lock the account temporarily.
         */
        post: operations["login"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/users": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List users (paginated)
         * @description Standard Spring `page`, `size`, and `sort` query params apply. All filters are independently optional and AND-combined server-side — FE no longer needs to pull the whole page and filter locally. Requires `auth.users.read`.
         */
        get: operations["list"];
        put?: never;
        /**
         * Create a user
         * @description Issues a temp password the user must change at first login. When the request carries an `Idempotency-Key` header, a re-issue of the same payload (same username, same `employeeLinked=true`) returns the existing user as 200 instead of 409 — used by the onboarding saga to make retries safe. Requires `auth.users.write`.
         */
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/users/{id}/unlock": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Unlock a temporarily locked account
         * @description Clears `lockedUntil` and resets failed-login counter. Requires `auth.users.write`.
         */
        post: operations["unlock"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/users/{id}/service-tokens": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Mint a long-lived service-account JWT
         * @description Issues a JWT for a service-account user (e.g. attendance-poller). Token is RS256-signed, identical claim shape to a login JWT plus `tokenType: "service"`. Returned once and not stored — capture it, seal it into the consumer's secret, and rotate by re-issuing. Resource servers accept it on signature + exp alone, so there is no session row, no refresh, and no revocation list. Cap is 730 days. Requires `auth.users.write`.
         */
        post: operations["issueServiceToken"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/users/{id}/roles": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Attach roles to a user
         * @description Adds the supplied roles. Existing roles are kept. Requires `auth.users.write`.
         */
        post: operations["attachRoles"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/users/{id}/password/reset": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Admin password reset
         * @description Forces the user to change at next login. Active sessions remain — revoke explicitly if needed. Requires `auth.users.reset`.
         */
        post: operations["resetPassword"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/users/{id}/disable": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Disable a user
         * @description Idempotent. Flips status=DISABLED, revokes active sessions, emits `auth.user.deactivated`. Used by the termination saga to guarantee the disable lands even if the Kafka path is delayed, and by the admin UI "disable user" action. The optional body's `reason` flows into the session-revoke RevokeReason and the outbox event so consumers can distinguish admin disables from termination-driven ones; defaults to ADMIN_REVOKE when omitted. Requires `auth.users.write`.
         */
        post: operations["disable"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/users/replay/role-grants": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Re-emit role grants (one-shot bootstrap)
         * @description Re-emits `auth.user.role_assigned` for every existing (user, role) pair. Use this once after deploying a service that needs a `role_members` projection (notification's fan-out path for HR-targeted approvals — bug #7). The events going forward already cover changes; this only seeds the snapshot. Idempotent on the consumer side. Requires `auth.users.write`.
         */
        post: operations["replayRoleGrants"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/roles": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List roles with attached permissions
         * @description Service-account roles (POLLER, ATTENDANCE_SVC) are excluded by default — they back machine identities and shouldn't show up in the user-assignment dropdown. Pass `includeServiceAccounts=true` from the role catalog admin screen when the full set is wanted. Requires `auth.roles.read`.
         */
        get: operations["listRoles"];
        put?: never;
        /**
         * Create a custom role
         * @description Code is immutable once created. Use UPPER_SNAKE_CASE codes (no ROLE_ prefix). All permissions in the request must already exist in /admin/permissions. Requires `auth.roles.write`.
         */
        post: operations["createRole"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/users/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a user by id
         * @description Requires `auth.users.read`.
         */
        get: operations["get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Patch a user
         * @description Null fields are left unchanged. Requires `auth.users.write`.
         */
        patch: operations["update"];
        trace?: never;
    };
    "/admin/roles/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Delete a custom role
         * @description Refuses if the role is system-seeded (EMPLOYEE | MANAGER | HR | ADMIN) or if any user currently holds it. Detach users first, then retry. Requires `auth.roles.write`.
         */
        delete: operations["deleteRole"];
        options?: never;
        head?: never;
        /**
         * Patch a role
         * @description Null fields are left unchanged. To replace permissions, send the full target set in `permissions` (not a diff). Both system-seeded and custom roles can be patched here; the `code` field is immutable. Requires `auth.roles.write`.
         */
        patch: operations["updateRole"];
        trace?: never;
    };
    "/users/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Search active users by username or full name
         * @description Case-insensitive substring match against username + fullName. ACTIVE only — DISABLED / LOCKED accounts are excluded so chat pickers don't surface ex-employees. Cap: 50 rows per call.
         */
        get: operations["search"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/users/by-ids": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Resolve userIds to display identity
         * @description Returns one row per existing user id, deduped and ordered to match the input. Unknown ids are silently dropped — the resolver isn't a `findById` and won't 404. Cap: 200 ids per call.
         */
        get: operations["byIds"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/sessions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List my active sessions
         * @description Returns every active session for the caller, newest `lastSeenAt` first. The `current` flag marks the session backing this request.
         */
        get: operations["list_1"];
        put?: never;
        post?: never;
        /**
         * Revoke all my sessions
         * @description Revokes every active session for the caller. Pass `?except=current` to keep this session alive (e.g. "sign out everywhere else"). Returns the killed sessions so the FE can show "Signed out 3 sessions: Chrome on macOS, Safari on iOS, ..." without re-fetching the list.
         */
        delete: operations["revokeAll"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get the current user's profile and permissions
         * @description Use this on app boot to drive role/permission-gated UI.
         */
        get: operations["me"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/.well-known/jwks.json": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * JSON Web Key Set
         * @description Returns the current RS256 signing key. Other services cache and rotate from here.
         */
        get: operations["jwks"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/users/{id}/sessions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List sessions for a user
         * @description Returns every active session row (refresh-token side). Use admin sessions DELETE to revoke a specific one. Requires `auth.sessions.admin`.
         */
        get: operations["sessions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/users/{id}/login-history": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Login history for a user (cursor-paginated)
         * @description Proxies audit-service for `auth.login.success` / `auth.login.failed` / `user.locked` / `user.unlocked` events on this user. Pass `cursor` from the previous response's `nextCursor` to scroll back further than the page size. Requires `audit.read` — login history is operator-only and is not part of HR's employee timeline (V0015 doctrine). HR uses employee-service `/employees/{id}/history` for org-assignment changes.
         */
        get: operations["loginHistory"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/users/stats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Aggregate user counts
         * @description One round-trip for the /admin overview tile counts. Replaces the four paginated `size=1` reads the FE used to do just to read `totalElements`. Requires `auth.users.read`.
         */
        get: operations["stats"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/permissions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List permissions
         * @description Use the codes as the value for `@RequirePermission` and frontend gating. Requires `auth.roles.read`.
         */
        get: operations["listPermissions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/permissions/{code}/roles": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Roles that include a permission
         * @description Reverse lookup — given a permission code, return the roles that grant it. Lets the roles page render "who has this?" without pulling the whole role catalog and joining client-side. Requires `auth.roles.read`.
         */
        get: operations["rolesByPermission"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/sessions/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Revoke one of my sessions
         * @description Revokes the session and deletes its refresh token. The corresponding access token still expires naturally (≤ 15 min). Returns the killed session so the FE can show "Revoked Chrome on macOS at 10:42" inline without re-fetching.
         */
        delete: operations["revoke"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/users/{id}/roles/{roleId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Detach a single role from a user
         * @description Requires `auth.users.write`.
         */
        delete: operations["detachRole"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/sessions/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Revoke a session by id
         * @description Marks the session row revoked and invalidates the refresh token. Idempotent. Returns the killed session so the admin UI can show "Revoked Chrome on macOS at 10:42" inline without re-fetching the user's session list. Requires `auth.sessions.admin`.
         */
        delete: operations["revoke_1"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /** @description Opaque refresh token returned at login or last refresh. */
        RefreshRequest: {
            /**
             * @description Opaque refresh token. Treat as a credential — do not log.
             * @example eyJhbGciOi...
             */
            refreshToken: string;
        };
        /** @description Access + refresh token pair. Send the access token as `Authorization: Bearer <accessToken>`. */
        TokenResponse: {
            /** @description Signed JWT (RS256). Place in the Authorization header for protected calls. */
            accessToken?: string;
            /**
             * Format: date-time
             * @description UTC instant the access token expires. Refresh before this to avoid 401.
             * @example 2026-04-28T12:15:00Z
             */
            accessTokenExpiresAt?: string;
            /** @description Opaque refresh token bound to the active session. Send to /auth/refresh to mint new tokens. */
            refreshToken?: string;
            /**
             * Format: date-time
             * @description UTC instant the refresh token expires. After this the user must log in again.
             * @example 2026-05-05T12:00:00Z
             */
            refreshTokenExpiresAt?: string;
            /**
             * Format: int64
             * @description Server-side session id. Used by admin endpoints to revoke the session.
             * @example 1042
             */
            sessionId?: number;
            /** @description True when HR provisioned a temporary password — UI must force a change before any other action. */
            mustChangePassword?: boolean;
        };
        /** @description Self-service password change. Both fields required. */
        PasswordChangeRequest: {
            /** @description Current password. */
            oldPassword: string;
            /** @description New password. Must satisfy server-side password policy. */
            newPassword: string;
        };
        /** @description Credentials for password-based login. */
        LoginRequest: {
            /**
             * @description Username (case-sensitive). Same value used at user creation time.
             * @example admin
             */
            username: string;
            /**
             * @description Plain-text password. Sent over TLS in non-dev environments.
             * @example ChangeMe!2026
             */
            password: string;
        };
        /** @description Admin payload to create a new user account. */
        CreateUserRequest: {
            /**
             * @description Unique username (case-sensitive). Used at login.
             * @example ahmed.raza
             */
            username: string;
            /**
             * @description Display name shown in UI.
             * @example Ahmed Raza
             */
            fullName: string;
            /**
             * Format: email
             * @description Optional contact email.
             * @example ahmed.raza@forwardsports.com
             */
            email?: string;
            /**
             * @description Temporary password — user is forced to change on first login.
             * @example Welcome!2026
             */
            temporaryPassword: string;
            /** @description True when the account is linked to an employee record (ESS). Reverse linkage (auth → employee) is not stored on `users`; employee-service owns the relationship via `employees.user_id`. */
            employeeLinked?: boolean;
            /**
             * @description Role codes to attach immediately (no ROLE_ prefix; matches the seeded EMPLOYEE | MANAGER | HR | ADMIN).
             * @example [
             *       "HR"
             *     ]
             */
            roles?: string[];
        };
        /** @description User account view — admin lists, get-by-id, and post-mutation responses. */
        UserDto: {
            /**
             * Format: int64
             * @description Internal user id.
             * @example 1042
             */
            id?: number;
            /**
             * @description Login name.
             * @example ahmed.raza
             */
            username?: string;
            /**
             * @description Display name.
             * @example Ahmed Raza
             */
            fullName?: string;
            /** @description Contact email (nullable). */
            email?: string;
            /**
             * @description Lifecycle status — drives whether the user can log in.
             * @enum {string}
             */
            status?: "ACTIVE" | "DISABLED" | "LOCKED";
            /** @description True until the user changes their initial / reset password. */
            mustChangePw?: boolean;
            /** @description True when linked to an employee record (ESS). */
            employeeLinked?: boolean;
            /**
             * @description Role codes assigned (sorted, distinct; no ROLE_ prefix).
             * @example [
             *       "HR"
             *     ]
             */
            roles?: string[];
            /**
             * Format: date-time
             * @description Last successful login (UTC).
             */
            lastLoginAt?: string;
            /**
             * Format: date-time
             * @description If non-null, account is locked until this UTC instant due to failed login attempts.
             */
            lockedUntil?: string;
            /**
             * Format: date-time
             * @description Created at (UTC).
             */
            createdAt?: string;
            /**
             * Format: date-time
             * @description Updated at (UTC).
             */
            updatedAt?: string;
        };
        /** @description Mint a long-lived JWT for a service-account user. Returned once, never stored. Rotation is admin-driven: re-issue, re-seal, restart the consumer. */
        IssueServiceTokenRequest: {
            /**
             * Format: int32
             * @description Token lifetime in days. Cap is 730 (~2y) to keep `exp` as the one revocation lever we have until a denylist exists.
             * @example 365
             */
            ttlDays: number;
        };
        /** @description Long-lived service-account JWT. Surfaced once at issue time. Auth does not store it; if lost, mint a new one and rotate the consumer's secret. */
        ServiceTokenResponse: {
            /** @description Bearer token. RS256, identical claim shape to a login JWT plus `tokenType: "service"`. */
            token: string;
            /**
             * Format: date-time
             * @description Token expiry as an ISO-8601 instant. Equal to issued-at + ttlDays.
             * @example 2027-05-08T13:42:00Z
             */
            expiresAt: string;
            /** @description JWT id (jti). Useful for grepping audit logs after the fact. */
            jti: string;
        };
        /** @description Roles to attach to a user. At least one is required. */
        AttachRolesRequest: {
            /**
             * @description Role codes to attach (no ROLE_ prefix; matches the seeded EMPLOYEE | MANAGER | HR | ADMIN).
             * @example [
             *       "HR"
             *     ]
             */
            roles: string[];
        };
        /** @description Admin password reset. The user is forced to change on next login. */
        PasswordResetRequest: {
            /**
             * @description Temporary password to set on the account.
             * @example Reset!2026
             */
            temporaryPassword: string;
        };
        /** @description Optional body for POST /admin/users/{id}/disable. The reason flows into the session-revoke RevokeReason and the auth.user.deactivated outbox event so downstream consumers can distinguish admin-initiated disables from termination-driven ones. */
        DisableUserRequest: {
            /**
             * @description Why the user is being disabled. Defaults to ADMIN_REVOKE when omitted, which is the right value for admin UI "disable user" actions and for the onboarding saga's compensation path. The termination saga passes EMPLOYEE_TERMINATED.
             * @example ADMIN_REVOKE
             * @enum {string}
             */
            reason?: "USER_LOGOUT" | "USER_REVOKE" | "ADMIN_REVOKE" | "PASSWORD_CHANGED" | "EXPIRED" | "EMPLOYEE_TERMINATED" | "EMPLOYEE_ESS_REVOKED";
        };
        /** @description Admin payload to create a custom role. */
        CreateRoleRequest: {
            /**
             * @description Stable role code. Conventionally upper-snake-case, no ROLE_ prefix (matches the seeded EMPLOYEE | MANAGER | HR | ADMIN). Used in JWT claims and policy checks.
             * @example PAYROLL_READONLY
             */
            code: string;
            /**
             * @description Human-readable name shown in the admin UI.
             * @example Payroll Read-only
             */
            name: string;
            /**
             * @description Optional 1-line description.
             * @example Audit consultant — payroll read-only.
             */
            description?: string;
            /**
             * @description Permission codes to attach immediately. Must already exist in /admin/permissions.
             * @example [
             *       "audit.read",
             *       "employee.read"
             *     ]
             */
            permissions: string[];
        };
        /** @description Role with its attached permission codes. */
        RoleDto: {
            /**
             * Format: int64
             * @description Internal role id.
             * @example 3
             */
            id?: number;
            /**
             * @description Stable role code used in JWT claims and policy checks. Seeded codes are EMPLOYEE | MANAGER | HR | ADMIN (no ROLE_ prefix).
             * @example HR
             */
            code?: string;
            /**
             * @description Human-readable name.
             * @example HR Manager
             */
            name?: string;
            /** @description Optional description. */
            description?: string;
            /**
             * @description True for Flyway-seeded roles — UI should hide the Delete control on these.
             * @example true
             */
            system?: boolean;
            /**
             * @description True for roles that back a machine identity (POLLER, ATTENDANCE_SVC). Excluded from the default catalog response — pass `includeServiceAccounts=true` on /admin/roles to see them.
             * @example false
             */
            serviceAccount?: boolean;
            /**
             * @description Permission codes the role grants (sorted, distinct).
             * @example [
             *       "auth.users.read",
             *       "auth.users.write"
             *     ]
             */
            permissions?: string[];
        };
        /** @description Patch a user. All fields optional — null means "leave unchanged". */
        UpdateUserRequest: {
            /**
             * @description New display name.
             * @example Ahmed M. Raza
             */
            fullName?: string;
            /**
             * Format: email
             * @description New contact email.
             * @example ahmed@forwardsports.com
             */
            email?: string;
            /**
             * @description Switch account status. Use DISABLED to lock the user out without deleting them.
             * @enum {string}
             */
            status?: "ACTIVE" | "DISABLED" | "LOCKED";
        };
        /** @description Patch a role. Null fields are left unchanged. The `code` is immutable — change a role's code by creating a new role and re-assigning users (the code is part of the JWT contract). */
        UpdateRoleRequest: {
            /** @description New display name. */
            name?: string;
            /** @description New description. */
            description?: string;
            /**
             * @description Replace the role's permissions with this set. Send the full target set, not a diff. Pass null to leave permissions untouched.
             * @example [
             *       "audit.read",
             *       "employee.read"
             *     ]
             */
            permissions?: string[];
        };
        /** @description Minimal user-identity row returned by the by-ids resolver. Intentionally narrow: only the fields a chat / mention / directory chip needs. */
        UserIdentitySummary: {
            /**
             * Format: int64
             * @description User id.
             * @example 42
             */
            id: number;
            /**
             * @description Login handle. Stable. Used for @mentions.
             * @example ahmed.khan
             */
            username: string;
            /**
             * @description Display name.
             * @example Ahmed Khan
             */
            fullName: string;
            /**
             * @description Account status. UI may grey out non-ACTIVE rows.
             * @example ACTIVE
             * @enum {string}
             */
            status: "ACTIVE" | "DISABLED" | "LOCKED";
        };
        /** @description Parsed name + optional version. */
        NameAndVersion: {
            /** @example Chrome */
            name?: string;
            /** @example 118.0.5993.117 */
            version?: string | null;
        };
        /** @description Refresh-token-backed session. Active when revokedAt is null and expiresAt is in the future. */
        SessionDto: {
            /**
             * Format: int64
             * @description Session id.
             * @example 412
             */
            id?: number;
            /**
             * @description Best-effort device label parsed from the User-Agent at login.
             * @example Chrome on macOS
             */
            deviceLabel?: string;
            /**
             * @description Coarse device family for UI icon-picking.
             * @example DESKTOP
             * @enum {string}
             */
            deviceFamily?: "MOBILE" | "TABLET" | "DESKTOP" | "UNKNOWN";
            /** @description Browser name + version, parsed from the User-Agent. Null when unknown. */
            browser?: components["schemas"]["NameAndVersion"];
            /** @description OS name + version, parsed from the User-Agent. Null when unknown. */
            os?: components["schemas"]["NameAndVersion"];
            /**
             * @description Originating IP at login.
             * @example 203.0.113.42
             */
            ipAddress?: string;
            /**
             * @description Raw User-Agent at login.
             * @example Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) ...
             */
            userAgent?: string;
            /**
             * Format: date-time
             * @description When the session was created.
             * @example 2026-04-28T08:14:22Z
             */
            issuedAt?: string;
            /**
             * Format: date-time
             * @description Last token refresh (≈ activity within the last 15 min).
             * @example 2026-04-28T09:42:00Z
             */
            lastSeenAt?: string;
            /**
             * Format: date-time
             * @description Refresh token expiry.
             * @example 2026-05-05T08:14:22Z
             */
            expiresAt?: string;
            /**
             * @description True if this session backs the current request.
             * @example true
             */
            current?: boolean;
            /**
             * Format: date-time
             * @description When the session was revoked. Null for active sessions.
             * @example 2026-04-28T10:42:11Z
             */
            revokedAt?: string | null;
            /**
             * @description Username that revoked the session (self for user-initiated, admin for admin revoke).
             * @example ahmed.raza
             */
            revokedBy?: string | null;
        };
        /** @description Current user identity, roles, and effective permissions. */
        MeResponse: {
            /**
             * Format: int64
             * @description Internal user id.
             * @example 1
             */
            userId?: number;
            /**
             * @description Username used at login.
             * @example admin
             */
            username?: string;
            /**
             * @description Display name.
             * @example System Administrator
             */
            fullName?: string;
            /**
             * @description Contact email (nullable for system users).
             * @example admin@forwardsports.com
             */
            email?: string;
            /** @description True when the user is linked to an employee record (ESS access). */
            employeeLinked?: boolean;
            /** @description True if the user must change their password before doing anything else. */
            mustChangePassword?: boolean;
            /**
             * @description Role codes assigned to the user (no ROLE_ prefix; matches the Flyway seed).
             * @example [
             *       "HR",
             *       "ADMIN"
             *     ]
             */
            roles?: string[];
            /**
             * @description Flattened, distinct permission codes from all roles.
             * @example [
             *       "auth.users.read",
             *       "auth.users.write"
             *     ]
             */
            permissions?: string[];
            /**
             * Format: date-time
             * @description Last successful login (UTC). Null on first login.
             */
            lastLoginAt?: string;
        };
        PageResponseUserDto: {
            content?: components["schemas"]["UserDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        /** @description One login-related audit event for a user. */
        LoginHistoryEntry: {
            /**
             * Format: date-time
             * @description When the event occurred.
             * @example 2026-04-28T08:14:22Z
             */
            occurredAt?: string;
            /**
             * @description Audit action.
             * @example login.success
             * @enum {string}
             */
            action?: "login.success" | "login.failed" | "user.locked" | "user.unlocked";
            /**
             * @description True if the login attempt succeeded.
             * @example true
             */
            success?: boolean;
            /**
             * @description Originating IP, if recorded.
             * @example 203.0.113.42
             */
            ipAddress?: string;
            /**
             * @description User-Agent header, if recorded.
             * @example Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) ...
             */
            userAgent?: string;
            /**
             * @description Failure reason for failed/locked entries (BAD_PASSWORD, USER_DISABLED, USER_LOCKED).
             * @example BAD_PASSWORD
             */
            reason?: string;
        };
        /** @description Cursor-paginated login history. */
        LoginHistoryPage: {
            content?: components["schemas"]["LoginHistoryEntry"][];
            /** @description Opaque cursor for the next page, null at end. */
            nextCursor?: string | null;
        };
        /** @description Aggregate counts over the users table — drives the /admin overview tile without paginating four times to read totalElements. */
        UserStatsResponse: {
            /**
             * Format: int64
             * @description Total user accounts.
             * @example 1248
             */
            total?: number;
            /**
             * Format: int64
             * @description Status = ACTIVE.
             * @example 1180
             */
            active?: number;
            /**
             * Format: int64
             * @description Status = LOCKED (admin lock; failed-login lock surfaces via lockedUntil instead).
             * @example 4
             */
            locked?: number;
            /**
             * Format: int64
             * @description Status = DISABLED.
             * @example 64
             */
            disabled?: number;
            /**
             * Format: int64
             * @description Users that must change their password at next login.
             * @example 12
             */
            withMustChangePw?: number;
            /**
             * Format: int64
             * @description Users linked to an employee record (ESS access).
             * @example 1230
             */
            employeeLinked?: number;
            /**
             * Format: int64
             * @description Users not linked to any employee (system / admin / consultant).
             * @example 18
             */
            standalone?: number;
        };
        /** @description A single permission row from the catalog. */
        PermissionDto: {
            /**
             * Format: int64
             * @description Internal permission id.
             * @example 12
             */
            id?: number;
            /**
             * @description Stable permission code referenced from @RequirePermission and policy checks.
             * @example auth.users.write
             */
            code?: string;
            /**
             * @description Human-readable name.
             * @example Manage users
             */
            name?: string;
            /**
             * @description UI grouping for the permission catalog (server-decided so non-engineers see "Identity" instead of "auth.users.*").
             * @example Identity
             */
            group?: string;
            /**
             * @description 1-line plain-English explanation of what the permission allows.
             * @example Create, update, lock, and unlock user accounts.
             */
            description?: string;
        };
        /** @description Sessions actually killed by a bulk revoke. Empty when there was nothing to revoke. */
        BulkRevokeResult: {
            /** @description Sessions revoked, in the order they were processed (newest active first). */
            revoked?: components["schemas"]["SessionDto"][];
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    refresh: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RefreshRequest"];
            };
        };
        responses: {
            /** @description New token pair issued. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TokenResponse"];
                };
            };
            /** @description Refresh token unknown, expired, or already revoked. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TokenResponse"];
                };
            };
        };
    };
    change: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PasswordChangeRequest"];
            };
        };
        responses: {
            /** @description Password changed; new tokens issued. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TokenResponse"];
                };
            };
            /** @description Old password wrong or new password fails policy. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TokenResponse"];
                };
            };
            /** @description Missing or invalid bearer token. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TokenResponse"];
                };
            };
        };
    };
    logout: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RefreshRequest"];
            };
        };
        responses: {
            /** @description Session revoked (or already revoked). */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    login: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LoginRequest"];
            };
        };
        responses: {
            /** @description Tokens issued. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TokenResponse"];
                };
            };
            /** @description Validation failed (blank credentials). */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TokenResponse"];
                };
            };
            /** @description Bad credentials or account disabled. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TokenResponse"];
                };
            };
            /** @description Account temporarily locked after too many failures. */
            423: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TokenResponse"];
                };
            };
        };
    };
    list: {
        parameters: {
            query?: {
                /** @description Filter by status. Omit to return all. */
                status?: "ACTIVE" | "DISABLED" | "LOCKED";
                /**
                 * @description Case-insensitive substring against username, fullName, or email.
                 * @example ahmed
                 */
                q?: string;
                /**
                 * @description Exact role-code match (single role). Use the codes from /admin/roles — seeded codes are EMPLOYEE | MANAGER | HR | ADMIN (no ROLE_ prefix).
                 * @example HR
                 */
                role?: string;
                /** @description Tri-state: omit to ignore, true for ESS-linked users, false for standalone. */
                employeeLinked?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseUserDto"];
                };
            };
        };
    };
    create: {
        parameters: {
            query?: never;
            header?: {
                "Idempotency-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateUserRequest"];
            };
        };
        responses: {
            /** @description Idempotent retry — user already existed. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserDto"];
                };
            };
            /** @description User created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserDto"];
                };
            };
            /** @description Username or email taken (no idempotency-key, or mismatched payload). */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserDto"];
                };
            };
        };
    };
    unlock: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description User id */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Account unlocked. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    issueServiceToken: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description User id */
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["IssueServiceTokenRequest"];
            };
        };
        responses: {
            /** @description Token issued. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ServiceTokenResponse"];
                };
            };
            /** @description User not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ServiceTokenResponse"];
                };
            };
            /** @description User is DISABLED. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ServiceTokenResponse"];
                };
            };
        };
    };
    attachRoles: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description User id */
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AttachRolesRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserDto"];
                };
            };
        };
    };
    resetPassword: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description User id */
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PasswordResetRequest"];
            };
        };
        responses: {
            /** @description Password reset. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    disable: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description User id */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["DisableUserRequest"];
            };
        };
        responses: {
            /** @description User disabled (or was already disabled). */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserDto"];
                };
            };
            /** @description User not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserDto"];
                };
            };
        };
    };
    replayRoleGrants: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Replay queued; response carries the count of pairs emitted. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        [key: string]: number;
                    };
                };
            };
        };
    };
    listRoles: {
        parameters: {
            query?: {
                /** @description Include service-account roles in the response. */
                includeServiceAccounts?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RoleDto"][];
                };
            };
        };
    };
    createRole: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateRoleRequest"];
            };
        };
        responses: {
            /** @description Role created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RoleDto"];
                };
            };
            /** @description One of the requested permission codes is unknown. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RoleDto"];
                };
            };
            /** @description Role code already in use. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RoleDto"];
                };
            };
        };
    };
    get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description User id
                 * @example 1042
                 */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description User not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserDto"];
                };
            };
        };
    };
    update: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description User id */
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateUserRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserDto"];
                };
            };
        };
    };
    deleteRole: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Role id */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Role deleted. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Role not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Role is system-seeded or held by one or more users. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    updateRole: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Role id */
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateRoleRequest"];
            };
        };
        responses: {
            /** @description Role updated. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RoleDto"];
                };
            };
            /** @description Role not found, or one of the requested permission codes is unknown. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RoleDto"];
                };
            };
        };
    };
    search: {
        parameters: {
            query: {
                /** @description Substring; min 1 char. */
                q: string;
                /** @description Max rows (default 20, capped at 50). */
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserIdentitySummary"][];
                };
            };
        };
    };
    byIds: {
        parameters: {
            query: {
                /** @description Comma-separated user ids; max 200 per call. */
                ids: number[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserIdentitySummary"][];
                };
            };
            /** @description Too many ids in a single request. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserIdentitySummary"][];
                };
            };
        };
    };
    list_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Active sessions for the caller. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SessionDto"][];
                };
            };
        };
    };
    revokeAll: {
        parameters: {
            query?: {
                /** @description Pass `current` to keep the calling session. */
                except?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Sessions revoked. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BulkRevokeResult"];
                };
            };
        };
    };
    me: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Profile returned. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MeResponse"];
                };
            };
            /** @description Missing or invalid bearer token. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MeResponse"];
                };
            };
        };
    };
    jwks: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        [key: string]: unknown;
                    };
                };
            };
        };
    };
    sessions: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description User id */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SessionDto"][];
                };
            };
        };
    };
    loginHistory: {
        parameters: {
            query?: {
                /** @description Inclusive lower bound (ISO-8601 instant). */
                from?: string;
                /** @description Exclusive upper bound (ISO-8601 instant). */
                to?: string;
                /** @description Page size (default 100). */
                limit?: number;
                /** @description Opaque cursor from a previous response. */
                cursor?: string;
            };
            header?: never;
            path: {
                /** @description User id */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Page of login history, newest first. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LoginHistoryPage"];
                };
            };
            /** @description audit-service is unreachable or returned an error. */
            502: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LoginHistoryPage"];
                };
            };
        };
    };
    stats: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Counts assembled in a single SQL pass. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserStatsResponse"];
                };
            };
        };
    };
    listPermissions: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PermissionDto"][];
                };
            };
        };
    };
    rolesByPermission: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description Permission code (e.g. `auth.users.write`).
                 * @example auth.users.write
                 */
                code: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Roles ordered by code, possibly empty. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RoleDto"][];
                };
            };
            /** @description No permission with that code. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RoleDto"][];
                };
            };
        };
    };
    revoke: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Session id */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Session revoked; revokedAt/revokedBy populated. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SessionDto"];
                };
            };
            /** @description Session does not belong to the caller. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SessionDto"];
                };
            };
            /** @description Session not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SessionDto"];
                };
            };
        };
    };
    detachRole: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description User id */
                id: number;
                /** @description Role id */
                roleId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserDto"];
                };
            };
        };
    };
    revoke_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Session id */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Session revoked; revokedAt/revokedBy populated. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SessionDto"];
                };
            };
            /** @description Session not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SessionDto"];
                };
            };
        };
    };
}

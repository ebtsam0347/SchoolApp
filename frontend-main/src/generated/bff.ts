/* eslint-disable */
/**
 * GENERATED FILE — do not edit by hand.
 * Source: docs/services/<group>.openapi.json (refresh via `npm run gen:api:fetch`).
 * Regenerate: `npm run gen:api`.
 */

export interface paths {
    "/bff/users/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Search active users for chat pickers
         * @description Substring match on username + fullName. ACTIVE only — terminated/disabled accounts are filtered out. Returns the same shape as `/by-ids`. Cap: 50 rows per call.
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
    "/bff/users/by-ids": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Resolve userIds → display identity
         * @description Order matches input, deduped. Unknown ids are dropped silently. Avatar is null for admin-only users with no employee row. Cap: 200 ids per call.
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
    "/bff/timeline/{requestType}/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Approval-request timeline
         * @description Audit feed for a single leave / overtime / regularization request, for the drawer Activity tab. The BFF probes the upstream request first (404 if it doesn't exist or the caller can't see it) and then reads the audit timeline. Cursor pagination via the audit-service cursor.
         */
        get: operations["requestTimeline"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Aggregated palette search
         * @description Returns hits across employee, payslip-mine, leave-mine, lookup-*, org-node, and inbox-item providers (each landed independently). Server-side query length minimum is 2; below that the endpoint returns `{results: [], partial: false, providers: []}`. Results are caller-scoped — callers without a permission's subject simply don't see those provider rows; self-only providers (`*-mine`) always return only the caller's own data.
         */
        get: operations["search_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/sagas": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List sagas across employee + payroll
         * @description Fans out to employee-service and payroll-service in parallel, merges rows, and sorts by updatedAt desc. Use `service=employee|payroll` to restrict to one side; default `all` queries both. Per-service slice degrades to empty if that service is down — degraded flags surface that in the response.
         */
        get: operations["list"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/reports/catalog": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Reports catalog — filtered by caller permissions
         * @description Returns every report the caller is allowed to run, in the order the FE should render them. Each entry carries the gateway path, the permission required (already enforced), the output format, sync/async mode, and the parameter manifest the FE renders into a form.
         */
        get: operations["catalog"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/notifications/stream": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Open the live-notification SSE channel
         * @description Authenticated; returns a long-lived `text/event-stream` response. The first event is `ready` (connection established), then one `notification` event per published message, plus periodic comment-line keepalives. Browsers reconnect automatically on EOF; the server-side timeout (10 min default) is the safety net for stuck connections.
         */
        get: operations["stream"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/employees/{id}/timeline": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Merged employee timeline
         * @description Merges the audit /timeline rows for `employee/{id}` with the user timeline for the linked `user/{userId}` (when the employee has ESS). Newest first; cursor pagination via the audit-service cursor on the entity feed. Requires `employee.read`.
         */
        get: operations["timeline"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/employees/by-ids": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Resolve employee summaries by id list
         * @description Generic name + photo + department resolver for any list of bare employee ids. Used by approvals inbox, OT batch rows, and any surface that comes back keyed by employee id without names attached. De-duplicates the input. Caps at 200 ids per call — split larger lists client-side. Requires `employee.read`.
         */
        get: operations["byIds_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/employee/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Aggregated /people/{id} payload
         * @description Returns employee + resolved labels + active contract + active suspension + most-recent exit row + UPHELD warning counts + first-8 direct reports + last 20 user-visible timeline events + last 10 assignment-history rows. Replaces ~10 round-trips today. For the full paginated history use GET /employees/{id}/history/resolved on employee-service. Requires `employee.read`.
         */
        get: operations["employee"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/dashboard/payroll": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Payroll dashboard composite
         * @description Current run + 6m run trend + payslip queues. Requires `payroll.read` minimum.
         */
        get: operations["payroll"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/dashboard/manager": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Manager dashboard composite
         * @description Manager landing for the caller's direct reports: team today + 14d attendance, approval queue (top 5), leave calendar (next 30 days). Returns 403 if the caller has no direct reports — the FE then falls back to the Employee dashboard.
         */
        get: operations["manager"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/dashboard/hr": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * HR dashboard composite
         * @description Org pulse + queues (onboarding stuck, contracts/probation expiring, disciplinary open) + recent audit anomalies. Most counters require `employee.*.read`; missing scope surfaces zeros rather than failing the whole call.
         */
        get: operations["hr"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/dashboard/employee": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Employee dashboard composite
         * @description Self-service landing payload: next payday, today's attendance, action queues, leave balance, attendance heatmap, recent payslips + 12m net trend, and the next three holidays. Replaces ~6 fan-outs.
         */
        get: operations["employee_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/dashboard/admin": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Admin dashboard composite
         * @description System health + recent role changes + locked accounts. Requires admin-tier scope; sections degrade quietly when the caller lacks audit.admin.
         */
        get: operations["admin"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/chat/inbox": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * First-paint composite for the chat inbox
         * @description Returns the caller's conversations, an identity resolver map for every member (userId → username + fullName + avatar), and a presence snapshot — all in one call. Authenticated only.
         */
        get: operations["inbox"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/attendance/team/{employeeId}/month": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Per-employee month view (manager)
         * @description Same shape as `/attendance/me/month/composite` so the FE drawer reuses the existing component. Caller must be the target's current manager OR hold an active approval delegation covering them on the requested month — 403 otherwise.
         */
        get: operations["teamMonth"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/attendance/team/today": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Team roll-call (manager)
         * @description One row per direct report plus any employee whose manager has delegated approval authority to the caller for the supplied date. Each row carries the report's compact summary, today's status, punch in/out, and lateness. Capped at 500 rows — beyond that `truncated=true` and the FE drops to a filtered org page. Returns 403 when the caller covers nobody (no direct reports and no active delegation) — same contract as the manager dashboard, so the FE gates the "My Team" nav on it. The active-leave chip data is reserved for a future upstream addition; v1 sets `activeLeave: null` on every row.
         */
        get: operations["teamToday"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/analytics/executive-overview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Executive overview — headline numbers
         * @description Single payload combining the latest values from all four service-side analytics endpoints: headcount, attrition rolling-12m, on-time%, absenteeism%, OT hours, payroll cost. Each field falls back to null if its downstream is unreachable so the dashboard card stays renderable.
         */
        get: operations["executiveOverview"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/analytics/executive-dashboard/pdf": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Executive dashboard — printable PDF
         * @description Single-page PDF combining the headline KPIs and 12-month trends (headcount, attrition, on-time/absenteeism, payroll cost) plus a current salary-distribution table. Same fan-out as /executive-overview; if a downstream is unreachable, that section renders empty rather than 502'ing.
         */
        get: operations["executiveDashboardPdf"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/admin/users/{id}/overview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Aggregated user-detail payload
         * @description Returns user + role catalog + active sessions + last 50 login-history entries + last 25 audit events authored by the user, in a single round-trip. Replaces the five queries the /admin/users/{id} page fans out today. Each underlying endpoint enforces its own @RequirePermission, so any missing permission surfaces as a 4xx from the failing slice. Requires `auth.users.read`.
         */
        get: operations["overview"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/account/summary": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Aggregated /account payload
         * @description Returns identity + active sessions (partitioned into current/others) + top user-visible timeline entries in a single round-trip. Lets the /account layout paint its user-stripe and overview tab without a fan-out from the browser.
         */
        get: operations["summary"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/account/profile": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Aggregated /account/profile payload
         * @description ESS counterpart to /bff/employee/{id} — single round-trip for the signed-in employee's profile tab.
         */
        get: operations["accountProfile"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/bff/account/persona-hints": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Persona hints for the signed-in caller
         * @description Lightweight flags the FE persona resolver needs on top of /auth/me — today just the manager signal (does the caller have direct reports). Soft-fails to all-false rather than 4xx-ing when the caller has no ESS-linked employee record so sign-in stays unblocked.
         */
        get: operations["personaHints"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /** @description Composed user-identity row the FE uses for chat / mention chips. Joins auth.user.username + auth.user.fullName with employee.profilePictureUrl (via employee.user_id). Avatar is null for admin-only users that have no employee row. */
        UserIdentitySummary: {
            /**
             * Format: int64
             * @description User id.
             * @example 42
             */
            userId: number;
            /**
             * @description Login handle. Stable. Used as the @mention token.
             * @example ahmed.khan
             */
            username: string;
            /**
             * @description Display name.
             * @example Ahmed Khan
             */
            fullName: string;
            /**
             * @description Pre-signed avatar URL; null when no employee row or no picture set.
             * @example https://minio.local/employee-docs/employees/108/avatar.jpg?…
             */
            profilePictureUrl?: string;
            /**
             * @description Account status from auth-service. UI may grey out non-ACTIVE rows.
             * @example ACTIVE
             */
            status: string;
        };
        /** @description Wrapper for the /bff/users/by-ids resolver. Order matches input order, deduped. */
        UsersByIdsResponse: {
            users?: components["schemas"]["UserIdentitySummary"][];
        };
        /** @description User-facing timeline entry. One row per `user_visible = true` audit event. */
        TimelineEntry: {
            /**
             * Format: int64
             * @example 5421
             */
            id?: number;
            /** @example updated */
            action?: string;
            /**
             * Format: int64
             * @description Actor's user id. `actorLabel` is filled at producer time from the JWT `name` claim; null only for events emitted before that wiring landed (Step 6) or by system actors with no display name.
             * @example 1042
             */
            actorUserId?: number | null;
            /** @example FS-2026-0042 */
            actorUsername?: string | null;
            /** @example Saqib Mushtaq */
            actorLabel?: string | null;
            /** @example Phone number updated. */
            message?: string | null;
            /**
             * Format: date-time
             * @example 2026-04-28T09:12:33Z
             */
            occurredAt?: string;
        };
        /** @description Cursor-paginated timeline response. */
        TimelinePage: {
            content?: components["schemas"]["TimelineEntry"][];
            /**
             * @description Opaque cursor for the next page, null at end.
             * @example MjAyNi0wNC0yOFQwOToxMjozM1o6NTQyMQ
             */
            nextCursor?: string | null;
        };
        /** @description Provider-level outcome. */
        ProviderDiagnostic: {
            /**
             * @description Provider type id.
             * @example employee
             */
            type: string;
            /**
             * Format: int32
             * @description Number of hits returned (post per-provider cap).
             * @example 5
             */
            hits: number;
            /**
             * Format: int64
             * @description Wall time in milliseconds until the provider settled.
             * @example 18
             */
            ms: number;
            /**
             * @description ok, timeout, or error.
             * @example ok
             */
            status: string;
        };
        /** @description One palette result. Order is by descending score within the response. */
        SearchHit: {
            /**
             * @description Stable, provider-prefixed id (e.g. "employee:1234").
             * @example employee:1234
             */
            id: string;
            /**
             * @description Provider type. Matches the type filter in ?types=.
             * @example employee
             */
            type: string;
            /**
             * @description Primary text shown by the palette row.
             * @example Aisha Khan
             */
            title: string;
            /**
             * @description Optional supporting text (designation, status, period).
             * @example Software Engineer · Engineering
             */
            subtitle?: string;
            /**
             * @description Where the FE should navigate on selection.
             * @example /people/1234
             */
            url: string;
            /**
             * Format: double
             * @description Per-provider relevance score in [0, 1]. Aggregator may apply a small exact-prefix boost before global ranking.
             * @example 0.92
             */
            score: number;
            /**
             * @description Short label the FE renders next to the row.
             * @example Person
             */
            badge?: string;
        };
        /** @description Globally ranked palette results plus per-provider diagnostics. */
        SearchResponse: {
            /** @description Merged, re-ranked hits across all consulted providers. Capped at the request limit (default 5 per provider, hard ceiling 10). */
            results: components["schemas"]["SearchHit"][];
            /**
             * @description True when at least one provider timed out or errored. The FE shows what came back; degraded providers surface in `providers`.
             * @example false
             */
            partial: boolean;
            /** @description One row per provider the aggregator consulted (after permission filtering). Useful for tuning provider timeouts and zero-result coverage. */
            providers: components["schemas"]["ProviderDiagnostic"][];
        };
        /** @description One saga row, annotated with its origin service. */
        SagaRow: {
            /**
             * @description Which service the saga row lives in.
             * @enum {string}
             */
            service?: "employee" | "payroll";
            /** Format: int64 */
            id?: number;
            sagaName?: string;
            correlationKey?: string;
            status?: string;
            currentStep?: string;
            /** Format: int32 */
            stepIndex?: number;
            /** Format: int32 */
            attemptCount?: number;
            /** Format: date-time */
            nextAttemptAt?: string;
            lastError?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
            createdBy?: string;
        };
        /** @description Merged saga rows across employee + payroll. Sorted by updatedAt desc. */
        SagasOverview: {
            /** @description Merged rows, newest first. */
            rows?: components["schemas"]["SagaRow"][];
            /**
             * Format: int32
             * @description Count of rows from employee-service in this response.
             */
            employeeCount?: number;
            /**
             * Format: int32
             * @description Count of rows from payroll-service in this response.
             */
            payrollCount?: number;
            /** @description True if employee-service read failed (rows partial). */
            employeeDegraded?: boolean;
            /** @description True if payroll-service read failed (rows partial). */
            payrollDegraded?: boolean;
        };
        /** @description One report the FE can offer. Combines the gateway path, required permission, output format, sync/async mode and a parameter manifest the FE renders into the request form. /bff/reports/catalog returns the list pre-filtered by the caller's permissions. */
        ReportCatalogEntry: {
            /**
             * @description Stable id, '<service>.<slug>'. Use as React key + telemetry.
             * @example payroll.pay-register
             */
            id?: string;
            /**
             * @description Owning service.
             * @example payroll
             * @enum {string}
             */
            service?: "employee" | "attendance" | "leaves" | "payroll";
            /**
             * @description Human-readable title for the card.
             * @example Pay register (per run)
             */
            title?: string;
            /**
             * @description One-sentence summary shown under the title.
             * @example Async — every payslip × every component, footed.
             */
            description?: string;
            /**
             * @description Output file format.
             * @example xlsx
             * @enum {string}
             */
            format?: "csv" | "pdf" | "xlsx" | "zip" | "json";
            /**
             * @description sync = browser receives the file inline; async = enqueue then poll /reports/jobs/{id}.
             * @example async
             * @enum {string}
             */
            mode?: "sync" | "async";
            /**
             * @description HTTP method to invoke.
             * @example POST
             * @enum {string}
             */
            method?: "GET" | "POST";
            /**
             * @description Gateway-relative path. Prepend the gateway base URL.
             * @example /reports/payroll/pay-register/jobs
             */
            path?: string;
            /**
             * @description Permission the caller must hold. Already enforced — this field is informational so the FE can show why a row is hidden.
             * @example payroll.reports.run
             */
            permission?: string;
            /** @description Statutory reports (EOBI / PESSI / tax) emit a user-visible audit row so HR can demonstrate filings to auditors. */
            userVisibleAudit?: boolean;
            /** @description Parameter manifest. Render one form field per entry. */
            parameters?: components["schemas"]["ReportCatalogParameter"][];
        };
        /** @description A single parameter the FE needs to collect before submitting. */
        ReportCatalogParameter: {
            /**
             * @description Parameter name in the URL / body.
             * @example from
             */
            name?: string;
            /**
             * @description Where to put the value when calling.
             * @example query
             * @enum {string}
             */
            location?: "query" | "path" | "body";
            /**
             * @description Value type.
             * @example date
             * @enum {string}
             */
            type?: "string" | "int" | "date" | "enum";
            /** @description True when the gateway / service will 400 / 422 if missing. */
            required?: boolean;
            /** @description Allowed values when type=enum, else null. */
            values?: string[] | null;
            /**
             * @description Inline help text the FE can show beside the field.
             * @example Inclusive lower bound of the date range.
             */
            help?: string | null;
        };
        SseEmitter: {
            /** Format: int64 */
            timeout?: number;
        };
        /** @description Compact employee row for resolving names against an id list. Used by the approvals inbox, payroll composites, attendance composites, and any surface that comes back keyed by employee id without names attached. */
        EmployeeSummary: {
            /**
             * Format: int64
             * @description Employee id.
             */
            id?: number;
            /** @description Display name. */
            fullName?: string;
            /** @description Human-readable employee code (e.g. FS-2026-0042). Also the RFID/punch-card number used by attendance devices. */
            employeeCode?: string;
            /**
             * Format: int64
             * @description Department id.
             */
            departmentId?: number | null;
            /** @description Department name, looked up from the catalog. */
            departmentName?: string | null;
            /**
             * Format: int64
             * @description Section id.
             */
            sectionId?: number | null;
            /** @description Section name, looked up from the catalog. */
            sectionName?: string | null;
            /**
             * Format: int64
             * @description Designation id.
             */
            designationId?: number | null;
            /** @description Designation title, looked up from the catalog. */
            designationName?: string | null;
            /** @description Signed URL for the employee's profile photo. */
            profilePictureUrl?: string | null;
        };
        /** @description Resolver result. Order matches the request id order; ids the caller cannot see (or that don't exist) are silently dropped — clients should compare lengths if exact-match matters. */
        EmployeesByIdsResponse: {
            employees?: components["schemas"]["EmployeeSummary"][];
        };
        /** @description Per-row active-probation summary for HR's probation queue. */
        ActiveProbationDto: {
            /**
             * Format: int64
             * @description Underlying employment_contracts row id.
             * @example 734
             */
            contractId?: number;
            /**
             * Format: date
             * @description Probation end date.
             * @example 2026-05-15
             */
            endDate?: string;
            /**
             * Format: int32
             * @description Days remaining (negative when overdue).
             * @example 12
             */
            daysRemaining?: number;
            /** @description True iff the probation has already been extended once. */
            isExtended?: boolean;
        };
        /** @description City in Pakistan, scoped to a province. */
        CityDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 5
             */
            id?: number;
            /**
             * @description Catalog code.
             * @example SKT
             */
            code?: string;
            /**
             * @description Display name.
             * @example Sialkot
             */
            name?: string;
            /**
             * Format: int64
             * @description Province id.
             * @example 1
             */
            provinceId?: number;
            /**
             * @description Province code (denormalized for filters).
             * @example PB
             */
            provinceCode?: string;
            /** @description Soft-deactivation flag. */
            active?: boolean;
            /**
             * Format: int32
             * @description HR-controlled sort order; lower sorts first.
             * @example 10
             */
            displayOrder?: number;
        };
        /** @description Top-level org unit. Departments are flat — no nesting. */
        DepartmentDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 3
             */
            id?: number;
            /**
             * @description Catalog code.
             * @example PROD
             */
            code?: string;
            /**
             * @description Display name.
             * @example Production
             */
            name?: string;
            /**
             * Format: int64
             * @description Department head's employee id, if assigned.
             */
            headId?: number | null;
            /** @description Soft-deactivation flag. */
            active?: boolean;
            /** @description Marks the department as a production unit (factory floor). */
            isProduction?: boolean;
        };
        /** @description Job title catalog. Department-agnostic. */
        DesignationDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 7
             */
            id?: number;
            /**
             * @description Catalog code.
             * @example SR_ENG
             */
            code?: string;
            /**
             * @description Display title.
             * @example Senior Engineer
             */
            title?: string;
            /**
             * @description DIRECT (production-line) vs INDIRECT (office / admin). Null when the distinction does not apply.
             * @enum {string|null}
             */
            category?: "DIRECT" | "INDIRECT" | null;
            /** @description Soft-deactivation flag. */
            active?: boolean;
        };
        /** @description Composite payload for the /people/{id} detail page: employee + resolved labels + active contract + suspension + exit + warning counts + direct reports preview + recent timeline + recent assignment history. */
        EmployeeDetail: {
            employee?: components["schemas"]["EmployeeDto"];
            /** @description Resolved id→label rows for the catalogs the page renders. */
            resolved?: components["schemas"]["EmployeeResolvedLabels"];
            /** @description Active contract row (most-recent ACTIVE). */
            activeContract?: components["schemas"]["EmploymentContractDto"];
            /** @description Currently active suspension, if any. */
            activeSuspension?: components["schemas"]["SuspensionDto"];
            /** @description Most recent exit row (PENDING / COMPLETED / WITHDRAWN). */
            exit?: components["schemas"]["ExitDto"];
            /** @description UPHELD warning counts for the current FY by level (FIRST/SECOND/FINAL → count). */
            upheldWarningsThisFy?: {
                [key: string]: number;
            };
            /**
             * Format: int64
             * @description Open (ISSUED) warning rows total.
             */
            openWarningsThisFy?: number;
            /** @description First 8 direct reports + total count. */
            directReportsPreview?: components["schemas"]["EmployeeDetail.DirectReportsPreview"];
            /** @description Last 20 user-visible timeline events for this employee. */
            recentTimeline?: components["schemas"]["TimelineEntry"][];
            /** @description Last 10 assignment-history rows (newest first), with catalog + manager labels resolved. Use GET /employees/{id}/history/resolved for the paginated full list. */
            recentHistory?: components["schemas"]["EmployeeHistoryRow"][];
            /**
             * Format: int64
             * @description Days from joining_date through today (Asia/Karachi).
             */
            tenureDays?: number;
        };
        "EmployeeDetail.DirectReportsPreview": {
            items?: components["schemas"]["EmployeeDetail.ReportSummary"][];
            /** Format: int32 */
            total?: number;
        };
        "EmployeeDetail.ReportSummary": {
            /** Format: int64 */
            id?: number;
            fullName?: string;
            employeeCode?: string;
            profilePictureUrl?: string;
            /** Format: int32 */
            directReportsCount?: number;
        };
        /** @description Employee record — admin lists, get-by-id, post-mutation responses. */
        EmployeeDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 42
             */
            id?: number;
            /**
             * Format: int64
             * @description Linked auth user id (null when ESS not granted).
             */
            userId?: number | null;
            /**
             * @description Employee code.
             * @example FS-2026-0042
             */
            employeeCode?: string;
            /**
             * @description Display name.
             * @example Ahmed Raza
             */
            fullName?: string;
            /** @description 13-digit CNIC. */
            cnic?: string | null;
            /** Format: date */
            cnicIssueDate?: string | null;
            /** Format: date */
            cnicExpiryDate?: string | null;
            ntn?: string | null;
            insuranceNumber?: string | null;
            sscNumber?: string | null;
            eobiNumber?: string | null;
            /** Format: date */
            dateOfBirth?: string | null;
            gender?: string | null;
            maritalStatus?: string | null;
            fatherName?: string | null;
            motherName?: string | null;
            spouseName?: string | null;
            religion?: string | null;
            /** @example B+ */
            bloodGroup?: string | null;
            personalEmail?: string | null;
            workEmail?: string | null;
            phone?: string | null;
            permanentAddress?: string | null;
            currentAddress?: string | null;
            /** Format: int64 */
            cityId?: number | null;
            /** @example PK */
            country?: string;
            /** Format: date */
            joiningDate?: string;
            /** Format: date */
            confirmationDate?: string | null;
            /** @enum {string} */
            employmentStatus?: "ACTIVE" | "ON_NOTICE" | "ON_LEAVE" | "SUSPENDED" | "TERMINATED";
            /** @enum {string|null} */
            employmentType?: "PERMANENT" | "CONTRACT" | "INTERN" | null;
            /** Format: int64 */
            departmentId?: number | null;
            /** Format: int64 */
            sectionId?: number | null;
            /** Format: int64 */
            designationId?: number | null;
            /** Format: int64 */
            gradeId?: number | null;
            /** Format: int64 */
            managerId?: number | null;
            /**
             * Format: int64
             * @description Linked auth user id of the manager. Null when the manager has no ESS user, or when the source path didn't resolve it (mutation responses leave it null).
             */
            managerUserId?: number | null;
            profilePictureKey?: string | null;
            /** @description Short-lived signed GET URL for the profile picture. Null when no picture is set or the storage call failed. */
            profilePictureUrl?: string | null;
            /** @description True iff ESS is provisioned. */
            hasEss?: boolean;
            /**
             * @description ESS provisioning state. On mutation responses reflects only the linked-user check (COMPLETED / NOT_REQUESTED). On read paths the assembler looks up the onboarding saga row to surface RUNNING / FAILED.
             * @enum {string}
             */
            essProvisioningStatus?: "NOT_REQUESTED" | "RUNNING" | "COMPLETED" | "FAILED";
            /**
             * Format: int32
             * @description Number of non-terminated employees who report to this employee. Populated on read endpoints; null on post-mutation responses.
             */
            directReportsCount?: number | null;
            /** @description Active-probation summary. Populated only on probation queries (probationDueWithin / probationOverdue) and detail reads. */
            activeProbation?: components["schemas"]["ActiveProbationDto"];
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        /** @description One row of the employee's org-assignment history. */
        EmployeeHistoryDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            effectiveFrom?: string;
            /**
             * Format: date
             * @description Null on the current row.
             */
            effectiveTo?: string | null;
            /** Format: int64 */
            departmentId?: number | null;
            /** Format: int64 */
            sectionId?: number | null;
            /** Format: int64 */
            designationId?: number | null;
            /** Format: int64 */
            gradeId?: number | null;
            /** Format: int64 */
            managerId?: number | null;
            /** @enum {string|null} */
            changeReason?: "ONBOARD" | "TRANSFER" | "PROMOTION" | "DEMOTION" | "DEPARTMENT_CHANGE" | "MANAGER_CHANGE" | "SECTION_CHANGE" | "EXIT" | null;
            notes?: string | null;
        };
        /** @description Point-in-time assignment row + resolved catalog labels and manager summary. */
        EmployeeHistoryRow: {
            /**
             * Format: int64
             * @description history row id
             */
            id?: number;
            /**
             * Format: date
             * @description Inclusive start of this assignment.
             */
            effectiveFrom?: string;
            /**
             * Format: date
             * @description Inclusive end. Null when this is the current row.
             */
            effectiveTo?: string | null;
            /** @description ONBOARD / TRANSFER / PROMOTION / DEMOTION / DEPARTMENT_CHANGE / MANAGER_CHANGE / SECTION_CHANGE / EXIT. */
            changeReason?: string | null;
            /** @description Free-text note captured at the time of change. */
            notes?: string | null;
            department?: components["schemas"]["DepartmentDto"];
            section?: components["schemas"]["SectionDto"];
            designation?: components["schemas"]["DesignationDto"];
            grade?: components["schemas"]["GradeDto"];
            manager?: components["schemas"]["ManagerSummary"];
            /** @description Raw upstream row, in case the FE wants the unresolved ids. */
            raw?: components["schemas"]["EmployeeHistoryDto"];
        };
        /** @description Resolved id→label rows for the catalogs the page renders. */
        EmployeeResolvedLabels: {
            department?: components["schemas"]["DepartmentDto"];
            section?: components["schemas"]["SectionDto"];
            designation?: components["schemas"]["DesignationDto"];
            grade?: components["schemas"]["GradeDto"];
            city?: components["schemas"]["CityDto"];
            manager?: components["schemas"]["ManagerSummary"];
        };
        /** @description One row of the employee's contract history. */
        EmploymentContractDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** @enum {string} */
            contractType?: "PROBATION" | "PERMANENT" | "FIXED_TERM";
            /** Format: date */
            startDate?: string;
            /** Format: date */
            endDate?: string | null;
            /** @enum {string} */
            status?: "ACTIVE" | "EXPIRED" | "TERMINATED";
            /**
             * Format: int32
             * @description Only present on PROBATION contracts.
             */
            probationDays?: number | null;
            extended?: boolean;
            documentKey?: string | null;
            /** @description Short-lived signed GET URL for the contract document. Null when no document is attached or the storage call failed. */
            documentUrl?: string | null;
        };
        /** @description Current or historical exit row for an employee. */
        ExitDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** @enum {string} */
            exitType?: "RESIGNATION" | "TERMINATION" | "RETIREMENT";
            /** Format: date */
            noticeStartDate?: string | null;
            /** Format: date */
            lastWorkingDay?: string;
            reason?: string | null;
            notes?: string | null;
            /** @enum {string} */
            status?: "PENDING" | "COMPLETED" | "WITHDRAWN";
            /** Format: date-time */
            completedAt?: string | null;
            /** Format: date-time */
            withdrawnAt?: string | null;
            withdrawnReason?: string | null;
        };
        /** @description Seniority grade catalog. Higher level = more senior. */
        GradeDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 4
             */
            id?: number;
            /**
             * @description Catalog code.
             * @example G3
             */
            code?: string;
            /**
             * @description Display name.
             * @example Grade 3
             */
            name?: string;
            /**
             * Format: int32
             * @description Ordering hint; higher is more senior.
             * @example 3
             */
            level?: number;
            /** @description Optional description. */
            description?: string | null;
            /** @description Soft-deactivation flag. */
            active?: boolean;
            /**
             * Format: int32
             * @description HR-controlled sort order; lower sorts first.
             * @example 30
             */
            displayOrder?: number;
            /**
             * @description Role bucket. Drives payroll's canteen rule (management → weekly, supervisor/labour → per-day) and other grade-type policy switches.
             * @enum {string}
             */
            gradeType?: "MANAGEMENT" | "SUPERVISOR" | "LABOUR";
        };
        /** @description Lean view of an employee's direct manager — enough to render the manager strip. */
        ManagerSummary: {
            /** Format: int64 */
            id?: number;
            fullName?: string;
            employeeCode?: string;
            profilePictureUrl?: string | null;
        };
        /** @description Section within a department. */
        SectionDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 12
             */
            id?: number;
            /**
             * @description Catalog code.
             * @example QC_1
             */
            code?: string;
            /**
             * @description Display name.
             * @example Quality Control - Footballs
             */
            name?: string;
            /**
             * Format: int64
             * @description Parent department id.
             * @example 3
             */
            departmentId?: number;
            /**
             * @description Parent department code.
             * @example PROD
             */
            departmentCode?: string;
            /**
             * Format: int64
             * @description Section head's employee id, if assigned.
             */
            headId?: number | null;
            /** @description Soft-deactivation flag. */
            active?: boolean;
        };
        /** @description One row in an employee's suspension history. */
        SuspensionDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            startDate?: string;
            /**
             * Format: date
             * @description Planned end. Null on open-ended suspensions.
             */
            endDate?: string | null;
            reason?: string;
            /** @description When false, suspended days count as LOP for payroll. */
            withPay?: boolean;
            /** @enum {string} */
            status?: "ACTIVE" | "LIFTED";
            /** Format: date */
            liftedOn?: string | null;
            liftedReason?: string | null;
        };
        /** @description Money envelope used across the dashboard payloads. `amount` is rupees as a BigDecimal at scale 2 — matches the §0.5 doctrine (was historically `long` paisa). */
        DashboardMoney: {
            amount?: number;
            currency?: string;
        };
        /** @description Composite for the Payroll persona. Drives the payroll-officer landing. */
        PayrollDashboard: {
            currentRun?: components["schemas"]["PayrollDashboard.CurrentRun"];
            runs6m?: components["schemas"]["PayrollDashboard.Runs6m"];
            needsYou?: components["schemas"]["PayrollDashboard.NeedsYou"];
            anomalyScatter?: components["schemas"]["PayrollDashboard.AnomalyScatter"];
            bonusByDepartment90d?: components["schemas"]["PayrollDashboard.DepartmentBonus"][];
            netYoYTrend12m?: components["schemas"]["PayrollDashboard.NetPoint"][];
        };
        /** @description Prior-vs-current scatter. Empty until D-S3 (payroll-side scatter endpoint) lands. */
        "PayrollDashboard.AnomalyScatter": {
            /** Format: int64 */
            runId?: number;
            /** Format: int64 */
            comparedTo?: number | null;
            points?: components["schemas"]["PayrollDashboard.ScatterPoint"][];
        };
        /** @description Most recent run that hasn't been POSTED — DRAFT / LOCKED / COMPUTED / APPROVED. Falls back to the latest POSTED run when nothing is in flight. `status` carries the run's actual PayrollRunStatus, so a run caught mid-transition can also surface COMPUTING / POSTING / FAILED / CANCELLED. */
        "PayrollDashboard.CurrentRun": {
            /** Format: int64 */
            id?: number;
            fiscalYear?: string | null;
            period?: string;
            /** @enum {string|null} */
            status?: "DRAFT" | "LOCKED" | "COMPUTING" | "COMPUTED" | "APPROVED" | "POSTING" | "POSTED" | "FAILED" | "CANCELLED" | null;
            /** Format: date */
            cutoffDate?: string | null;
            /** Format: int32 */
            daysToCutoff?: number | null;
            rollup?: components["schemas"]["PayrollDashboard.Rollup"];
        };
        "PayrollDashboard.DepartmentBonus": {
            /** Format: int64 */
            departmentId?: number;
            name?: string;
            total?: components["schemas"]["DashboardMoney"];
        };
        /** @description Action queues for the payroll officer. */
        "PayrollDashboard.NeedsYou": {
            anomaliesOnRun?: components["schemas"]["PayrollDashboard.QueueSection"];
            offCyclePending?: components["schemas"]["PayrollDashboard.QueueSection"];
            salaryChangesPending?: components["schemas"]["PayrollDashboard.QueueSection"];
            payslipsHeld?: components["schemas"]["PayrollDashboard.QueueSection"];
        };
        "PayrollDashboard.NetPoint": {
            month?: string;
            /** Format: int64 */
            amount?: number;
        };
        "PayrollDashboard.QueueItem": {
            /** Format: int64 */
            id?: number;
            employee?: components["schemas"]["EmployeeSummary"];
            summary?: string;
            reason?: string | null;
            /** Format: date-time */
            occurredAt?: string | null;
        };
        "PayrollDashboard.QueueSection": {
            /** Format: int32 */
            count?: number;
            items?: components["schemas"]["PayrollDashboard.QueueItem"][];
        };
        "PayrollDashboard.Rollup": {
            gross?: components["schemas"]["DashboardMoney"];
            deductions?: components["schemas"]["DashboardMoney"];
            net?: components["schemas"]["DashboardMoney"];
            /** Format: int32 */
            employeeCount?: number;
        };
        "PayrollDashboard.RunRollup": {
            /** Format: int64 */
            id?: number;
            period?: string;
            gross?: components["schemas"]["DashboardMoney"];
            deductions?: components["schemas"]["DashboardMoney"];
            net?: components["schemas"]["DashboardMoney"];
        };
        /** @description Last 6 runs in the current fiscal year, oldest first for the chart. */
        "PayrollDashboard.Runs6m": {
            runs?: components["schemas"]["PayrollDashboard.RunRollup"][];
        };
        "PayrollDashboard.ScatterPoint": {
            /** Format: int64 */
            employeeId?: number;
            name?: string;
            departmentName?: string | null;
            /** Format: int64 */
            priorNet?: number;
            /** Format: int64 */
            currentNet?: number;
            /** Format: double */
            variancePct?: number;
        };
        /** @description Composite for the Manager persona. Scope is the caller's direct reports. */
        ManagerDashboard: {
            team?: components["schemas"]["ManagerDashboard.Team"];
            attendance14d?: components["schemas"]["ManagerDashboard.Attendance14d"];
            approvalQueue?: components["schemas"]["ManagerDashboard.ApprovalQueue"];
            leaveCalendar30d?: components["schemas"]["ManagerDashboard.LeaveCalendar30d"];
            context?: components["schemas"]["ManagerDashboard.Context"];
        };
        /** @description Pending approvals across leave / overtime / regularization, capped at the top 5 rows; "see all" links into /approvals/inbox. */
        "ManagerDashboard.ApprovalQueue": {
            /** Format: int32 */
            totalPending?: number;
            items?: components["schemas"]["ManagerDashboard.ApprovalRow"][];
            velocity?: components["schemas"]["ManagerDashboard.Velocity"];
        };
        "ManagerDashboard.ApprovalRow": {
            kind?: string;
            /** Format: int64 */
            id?: number;
            employee?: components["schemas"]["EmployeeSummary"];
            summary?: string;
            /** Format: date-time */
            createdAt?: string;
        };
        /** @description Last 14 days of team attendance, bucketed per day. */
        "ManagerDashboard.Attendance14d": {
            /** Format: date */
            from?: string;
            /** Format: date */
            to?: string;
            days?: components["schemas"]["ManagerDashboard.DayBucket"][];
        };
        /** @description Punctuality + exception roll-ups over the last 7 days. */
        "ManagerDashboard.Context": {
            punctualityWeek?: components["schemas"]["ManagerDashboard.Punctuality"];
            exceptionsWeek?: components["schemas"]["ManagerDashboard.Exceptions"];
        };
        "ManagerDashboard.DayBucket": {
            /** Format: date */
            date?: string;
            /** Format: int32 */
            present?: number;
            /** Format: int32 */
            late?: number;
            /** Format: int32 */
            absent?: number;
            /** Format: int32 */
            leave?: number;
        };
        /** @description Count of LATE + ABSENT records across direct reports in the last 7 days. */
        "ManagerDashboard.Exceptions": {
            /** Format: int32 */
            count?: number;
        };
        /** @description Approved team leaves in the next 30 days. Empty until the team-leave list endpoint lands; for v1 the FE renders "no upcoming leaves". */
        "ManagerDashboard.LeaveCalendar30d": {
            /** Format: date */
            from?: string;
            /** Format: date */
            to?: string;
            items?: components["schemas"]["ManagerDashboard.LeaveItem"][];
        };
        "ManagerDashboard.LeaveItem": {
            /** Format: int64 */
            personId?: number;
            personName?: string;
            leaveType?: string;
            /** Format: date */
            start?: string;
            /** Format: date */
            end?: string;
        };
        /** @description Average late minutes across direct reports who attended in the last 7 days, plus a 7-element daily trend (oldest → today). */
        "ManagerDashboard.Punctuality": {
            /** Format: int32 */
            avgVarianceMin?: number;
            trend7d?: number[];
        };
        /** @description Team roster: counts plus a flat member list for the avatar stack. Per-person status for today is served by /bff/attendance/team/today. */
        "ManagerDashboard.Team": {
            /** Format: int32 */
            size?: number;
            /** Format: int32 */
            directReports?: number;
            members?: components["schemas"]["EmployeeSummary"][];
        };
        /** @description Average minutes between request submission and the caller's decision over the last 7 days, plus a 7-element daily trend (oldest → today). Empty days surface as 0. */
        "ManagerDashboard.Velocity": {
            /** Format: int32 */
            avgDecisionMinutes7d?: number;
            trend7d?: number[];
        };
        /** @description Composite for the HR persona. Org-wide totals are the headline; queues are filtered by caller scope where relevant. */
        HrDashboard: {
            orgPulse?: components["schemas"]["HrDashboard.OrgPulse"];
            attendance30d?: components["schemas"]["HrDashboard.Attendance30d"];
            headcount12m?: components["schemas"]["HrDashboard.Headcount12m"];
            queues?: components["schemas"]["HrDashboard.Queues"];
            departmentLeave30d?: components["schemas"]["HrDashboard.DepartmentLeave"][];
            recentAuditAnomalies?: components["schemas"]["TimelineEntry"][];
        };
        /** @description Per-day attendance rate over the last 30 days. Empty until D-S2 lands. */
        "HrDashboard.Attendance30d": {
            /** Format: date */
            from?: string;
            /** Format: date */
            to?: string;
            days?: components["schemas"]["HrDashboard.RateDay"][];
        };
        "HrDashboard.DepartmentLeave": {
            /** Format: int64 */
            departmentId?: number;
            name?: string;
            /** Format: double */
            leaveDays?: number;
        };
        /** @description Joiners / leavers / end-of-month headcount over the trailing 12 months. Empty until employee-service exposes the monthly aggregate (out of v1 scope). */
        "HrDashboard.Headcount12m": {
            months?: components["schemas"]["HrDashboard.HeadcountMonth"][];
        };
        "HrDashboard.HeadcountMonth": {
            month?: string;
            /** Format: int64 */
            joiners?: number;
            /** Format: int64 */
            leavers?: number;
            /** Format: int64 */
            endHeadcount?: number;
        };
        /** @description Employee burning through annual leave. Empty until the per-employee leave-balance aggregate ships. */
        "HrDashboard.LeaveHotspotRow": {
            /** Format: int64 */
            personId?: number;
            name?: string;
            departmentName?: string | null;
            /** Format: double */
            annualUsedPct?: number;
            /** Format: double */
            remainingDays?: number;
        };
        "HrDashboard.LeaveHotspots": {
            /** Format: int32 */
            count?: number;
            items?: components["schemas"]["HrDashboard.LeaveHotspotRow"][];
        };
        /** @description Today's headcount roll-up. Today's status counters fall back to zero until D-S2 (org-wide attendance pulse endpoint) ships. */
        "HrDashboard.OrgPulse": {
            /** Format: date */
            asOf?: string;
            /** Format: int64 */
            headcount?: number;
            /** Format: int64 */
            presentToday?: number;
            /** Format: int64 */
            lateToday?: number;
            /** Format: int64 */
            onLeaveToday?: number;
            /** Format: int64 */
            absentToday?: number;
        };
        /** @description Generic queue row — `kind` discriminates so the FE renders the correct drawer / link. */
        "HrDashboard.QueueItem": {
            kind?: string;
            /** Format: int64 */
            id?: number;
            employee?: components["schemas"]["EmployeeSummary"];
            summary?: string;
            /** Format: date */
            due?: string | null;
        };
        "HrDashboard.QueueSection": {
            /** Format: int32 */
            count?: number;
            items?: components["schemas"]["HrDashboard.QueueItem"][];
        };
        "HrDashboard.Queues": {
            onboardingStuck?: components["schemas"]["HrDashboard.QueueSection"];
            contractsExpiring?: components["schemas"]["HrDashboard.QueueSection"];
            disciplinaryOpen?: components["schemas"]["HrDashboard.QueueSection"];
            leaveHotspots?: components["schemas"]["HrDashboard.LeaveHotspots"];
        };
        "HrDashboard.RateDay": {
            /** Format: date */
            date?: string;
            /** Format: double */
            rate?: number;
            anomaly?: boolean;
        };
        /** @description Composite payload for the /dashboard route when the caller is rendered as the Employee persona. Replaces ~6 fan-outs (employee me, payslips, leave balance, attendance month, holidays, notifications). */
        EmployeeDashboard: {
            nextPayday?: components["schemas"]["EmployeeDashboard.NextPayday"];
            today?: components["schemas"]["EmployeeDashboard.Today"];
            needsYou?: components["schemas"]["EmployeeDashboard.NeedsYou"];
            leaveBalance?: components["schemas"]["EmployeeDashboard.LeaveBalance"];
            attendanceHeatmap?: components["schemas"]["EmployeeDashboard.AttendanceHeatmap"];
            payslips?: components["schemas"]["EmployeeDashboard.Payslips"];
            upcomingHolidays?: components["schemas"]["EmployeeDashboard.UpcomingHoliday"][];
        };
        /** @description Per-day status for the current month — drives the heatmap viz. */
        "EmployeeDashboard.AttendanceHeatmap": {
            month?: string;
            days?: components["schemas"]["EmployeeDashboard.HeatmapDay"][];
        };
        "EmployeeDashboard.CountedDocs": {
            /** Format: int32 */
            count?: number;
            items?: components["schemas"]["EmployeeDashboard.DocRow"][];
        };
        "EmployeeDashboard.CountedPunches": {
            /** Format: int32 */
            count?: number;
            items?: components["schemas"]["EmployeeDashboard.PunchRow"][];
        };
        "EmployeeDashboard.CountedRequests": {
            /** Format: int32 */
            count?: number;
            items?: components["schemas"]["EmployeeDashboard.RequestRow"][];
        };
        /** @description Doc the employee still needs to acknowledge. Empty for v1 — no downstream contract yet. */
        "EmployeeDashboard.DocRow": {
            /** Format: int64 */
            id?: number;
            name?: string;
            kind?: string;
        };
        "EmployeeDashboard.HeatmapDay": {
            /** Format: date */
            date?: string;
            status?: string;
            /** Format: int32 */
            varianceMin?: number;
        };
        "EmployeeDashboard.LeaveBalance": {
            /** Format: date */
            asOf?: string;
            types?: components["schemas"]["EmployeeDashboard.LeaveTypeBalance"][];
        };
        "EmployeeDashboard.LeaveTypeBalance": {
            code?: string;
            label?: string;
            used?: number;
            pending?: number;
            remaining?: number;
            total?: number;
        };
        /** @description Action queues. Each carries a count for the badge plus the top 5 rows the FE renders inline; "see all" links into the module page. */
        "EmployeeDashboard.NeedsYou": {
            pendingRequests?: components["schemas"]["EmployeeDashboard.CountedRequests"];
            missingPunches?: components["schemas"]["EmployeeDashboard.CountedPunches"];
            documentsToAck?: components["schemas"]["EmployeeDashboard.CountedDocs"];
        };
        "EmployeeDashboard.NetTrendPoint": {
            period?: string;
            amount?: number;
        };
        /** @description Most recent payslip + the payroll run it belongs to. The FE displays this as the projected next payday — the run lifecycle (DRAFT / LOCKED / POSTED) tells the user whether numbers are final. */
        "EmployeeDashboard.NextPayday": {
            /** Format: date */
            date?: string | null;
            projectedNet?: components["schemas"]["DashboardMoney"];
            /** Format: int64 */
            runId?: number | null;
            runStatus?: string | null;
        };
        /** @description Compact payslip for the dashboard list. Full detail (lines, PDF) lives on /bff/payslips/me. */
        "EmployeeDashboard.PayslipRow": {
            /** Format: int64 */
            id?: number;
            /** Format: int32 */
            periodYear?: number;
            /** Format: int32 */
            periodMonth?: number;
            gross?: components["schemas"]["DashboardMoney"];
            net?: components["schemas"]["DashboardMoney"];
            status?: string;
            /** Format: date-time */
            generatedAt?: string | null;
        };
        "EmployeeDashboard.Payslips": {
            recent?: components["schemas"]["EmployeeDashboard.PayslipRow"][];
            netTrend12m?: components["schemas"]["EmployeeDashboard.NetTrendPoint"][];
        };
        /** @description Workday with no attendance record (no punches and the nightly close didn't leave a stub). The FE deep-links into the missing-punch flow. */
        "EmployeeDashboard.PunchRow": {
            /** Format: date */
            date?: string;
            reason?: string;
        };
        /** @description Pending leave / overtime / regularization request the caller submitted. */
        "EmployeeDashboard.RequestRow": {
            kind?: string;
            /** Format: int64 */
            id?: number;
            summary?: string;
            status?: string;
            /** Format: date-time */
            createdAt?: string;
        };
        /** @description Assigned shift snapshot — null when the upstream attendance record doesn't carry one yet. */
        "EmployeeDashboard.Shift": {
            start?: string | null;
            end?: string | null;
            name?: string | null;
        };
        /** @description Today's attendance status — pulled from this month's attendance roll-forward + today's punches. */
        "EmployeeDashboard.Today": {
            /** @description One of CHECKED_IN, ON_LEAVE, OFF, PENDING_PUNCH, HOLIDAY, WEEKEND, ABSENT. */
            status?: string;
            /** Format: date-time */
            checkInAt?: string | null;
            shift?: components["schemas"]["EmployeeDashboard.Shift"];
            /** Format: int32 */
            elapsedMinutes?: number | null;
        };
        "EmployeeDashboard.UpcomingHoliday": {
            /** Format: date */
            date?: string;
            name?: string;
            dayOfWeek?: string;
        };
        /** @description Composite for the Admin persona. System-health slice; feeds the Admin dashboard hero. */
        AdminDashboard: {
            systemHealth?: components["schemas"]["AdminDashboard.SystemHealth"];
            auditVolume7d?: components["schemas"]["AdminDashboard.AuditDay"][];
            needsYou?: components["schemas"]["AdminDashboard.NeedsYou"];
            authEventsByHour?: components["schemas"]["AdminDashboard.AuthHourBucket"][];
            recentRoleChanges?: components["schemas"]["AuditEventDto"][];
        };
        "AdminDashboard.AuditDay": {
            /** Format: date */
            date?: string;
            /** Format: int64 */
            count?: number;
        };
        "AdminDashboard.AuthHourBucket": {
            /** Format: int32 */
            hour?: number;
            /** Format: int64 */
            logins?: number;
            /** Format: int64 */
            failures?: number;
        };
        "AdminDashboard.NeedsYou": {
            failedSagas?: components["schemas"]["AdminDashboard.QueueSection"];
            permissionChanges24h?: components["schemas"]["AdminDashboard.QueueSection"];
            lockedAccounts?: components["schemas"]["AdminDashboard.QueueSection"];
        };
        /** @description Generic admin queue row. `retryHref` is set on rows where re-arm is supported (failed sagas). */
        "AdminDashboard.QueueItem": {
            /** Format: int64 */
            id?: number;
            label?: string;
            summary?: string;
            retryHref?: string | null;
        };
        "AdminDashboard.QueueSection": {
            /** Format: int32 */
            count?: number;
            items?: components["schemas"]["AdminDashboard.QueueItem"][];
        };
        /** @description Audit consumer-lag summary. Pulled from /admin/audit/health when available; defaults to zero/null when the caller lacks audit.admin. */
        "AdminDashboard.ReplayQueue": {
            /** Format: int32 */
            depth?: number;
            /** Format: int64 */
            oldestSeconds?: number;
        };
        "AdminDashboard.SagaHealth": {
            id?: string;
            name?: string;
            status?: string;
            failureRate24h?: number[];
        };
        /** @description Saga health rollup. Counts default to zero until the audit / saga list endpoint ships (D-S4). */
        "AdminDashboard.SagaSummary": {
            /** Format: int32 */
            running?: number;
            /** Format: int32 */
            failed24h?: number;
            list?: components["schemas"]["AdminDashboard.SagaHealth"][];
        };
        "AdminDashboard.SystemHealth": {
            overall?: string;
            sagas?: components["schemas"]["AdminDashboard.SagaSummary"];
            auditReplayQueue?: components["schemas"]["AdminDashboard.ReplayQueue"];
        };
        /** @description An audit event row. JSON fields are returned as-is from the emitting service. */
        AuditEventDto: {
            /**
             * Format: int64
             * @example 5421
             */
            id?: number;
            /** @example 01HZK7G6Y0PSV2TPC9N9XSH3WC */
            eventId?: string;
            /** @example employee */
            sourceService?: string;
            /** @example employee */
            entityType?: string;
            /** @example 1057 */
            entityId?: string;
            /** @example updated */
            action?: string;
            /**
             * Format: int64
             * @example 42
             */
            actorUserId?: number | null;
            /** @example FS-2026-0042 */
            actorUsername?: string | null;
            /** @example Saqib Mushtaq */
            actorLabel?: string | null;
            /** @description Snapshot before the change. */
            before?: components["schemas"]["JsonNode"];
            /** @description Snapshot after the change. */
            after?: components["schemas"]["JsonNode"];
            /** @description Pre-diffed { field: { from, to } } if the emitter supplied it. */
            changes?: components["schemas"]["JsonNode"];
            /** @description Producer metadata (ip, user-agent, source). */
            metadata?: components["schemas"]["JsonNode"];
            /** @example 8c2cc4e0-... */
            correlationId?: string;
            /** @example 203.0.113.5 */
            ipAddress?: string | null;
            /** @example true */
            userVisible?: boolean;
            /** @example Phone number updated. */
            message?: string | null;
            /**
             * Format: date-time
             * @example 2026-04-28T09:12:33Z
             */
            occurredAt?: string;
            /**
             * Format: date-time
             * @example 2026-04-28T09:12:34Z
             */
            ingestedAt?: string;
        };
        JsonNode: {
            array?: boolean;
            empty?: boolean;
            null?: boolean;
            float?: boolean;
            floatingPointNumber?: boolean;
            integralNumber?: boolean;
            missingNode?: boolean;
            number?: boolean;
            valueNode?: boolean;
            object?: boolean;
            pojo?: boolean;
            short?: boolean;
            int?: boolean;
            long?: boolean;
            double?: boolean;
            bigDecimal?: boolean;
            bigInteger?: boolean;
            /** @deprecated */
            textual?: boolean;
            boolean?: boolean;
            binary?: boolean;
            /** @enum {string} */
            nodeType?: "ARRAY" | "BINARY" | "BOOLEAN" | "MISSING" | "NULL" | "NUMBER" | "OBJECT" | "POJO" | "STRING";
            string?: boolean;
            container?: boolean;
            embeddedValue?: boolean;
        };
        /** @description Cold-load shape for the chat surface. Bundles the conversation list, identity resolution for every member, and a presence snapshot — the first paint of the chat UI now needs one round-trip instead of ~5+N. */
        ChatInboxResponse: {
            /** @description Caller's conversations (DMs, groups, broadcast). */
            conversations?: components["schemas"]["ConversationDto"][];
            /** @description userId → identity, for every member of every conversation. Null avatar for admin-only users with no employee row. */
            resolved?: {
                [key: string]: components["schemas"]["UserIdentitySummary"];
            };
            /** @description Presence snapshot for the same set of userIds. */
            presence?: components["schemas"]["PresenceDto"][];
        };
        /** @description Conversation header. Members and last-message preview included. */
        ConversationDto: {
            /** Format: int64 */
            id?: number;
            /** @enum {string} */
            type?: "DM" | "GROUP" | "BROADCAST";
            title?: string | null;
            /** Format: int64 */
            createdByUser?: number;
            /** Format: int64 */
            lastMessageId?: number | null;
            /** Format: date-time */
            lastMessageAt?: string | null;
            memberUserIds?: number[];
            /** Format: int64 */
            unreadCount?: number;
            /** @description Per-member read cursors: userId → lastReadMessageId. Empty for BROADCAST (no member rows; ✓✓ is noisy on org-wide channels). Lets the FE draw `seen by` / blue ticks without an extra fetch. */
            lastReadByUser?: {
                [key: string]: number;
            };
        };
        /** @description A user's connection status. `online` means we have a live SSE within the last ~30s; otherwise `offline` with a `lastSeenAt` if known. */
        PresenceDto: {
            /** Format: int64 */
            userId?: number;
            /** @description online | offline */
            status?: string;
            /** Format: date-time */
            lastSeenAt?: string | null;
        };
        /** @description Calendar month view. Each day's `kind` is the FE's render hint — pick the single overlay that wins for that cell. Precedence (high → low): HOLIDAY > LEAVE > the record's status (PRESENT, ABSENT, HALF_DAY, WEEKEND). */
        AttendanceMonth: {
            /** @description Per-day rows for the month, in chronological order. */
            days?: components["schemas"]["AttendanceMonth.Day"][];
            summary?: components["schemas"]["AttendanceMonth.Summary"];
        };
        "AttendanceMonth.Day": {
            /** Format: date */
            workDate?: string;
            /** @description Render hint — one of PRESENT, ABSENT, LEAVE, HOLIDAY, WEEKEND, HALF_DAY. */
            kind?: string;
            record?: components["schemas"]["AttendanceRecordDto"];
            holiday?: components["schemas"]["HolidayDto"];
            leave?: components["schemas"]["AttendanceMonth.DayLeave"];
        };
        /** @description Compact leave overlay — full request lives elsewhere. */
        "AttendanceMonth.DayLeave": {
            /** Format: int64 */
            requestId?: number;
            leaveTypeCode?: string;
            halfDay?: boolean;
            halfDayPart?: string | null;
        };
        "AttendanceMonth.Summary": {
            /** Format: int32 */
            present?: number;
            /** Format: int32 */
            absent?: number;
            /** Format: int32 */
            leave?: number;
            /** Format: int32 */
            holiday?: number;
            /** Format: int32 */
            halfDay?: number;
            /** Format: int32 */
            weekend?: number;
            /** Format: int32 */
            totalWorkedMinutes?: number;
            /** Format: int32 */
            totalOtMinutes?: number;
            /** Format: int32 */
            totalLateMinutes?: number;
        };
        /** @description Per-day attendance projection. Shift fields are the snapshot — they do NOT change when the shift catalog or assignment changes. */
        AttendanceRecordDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            workDate?: string;
            /** Format: date-time */
            checkIn?: string | null;
            /** Format: date-time */
            checkOut?: string | null;
            /** Format: int64 */
            effectiveShift?: number | null;
            shiftStartTime?: string | null;
            shiftEndTime?: string | null;
            shiftBreakStartTime?: string | null;
            shiftBreakEndTime?: string | null;
            /**
             * Format: int32
             * @description Derived from break window.
             */
            shiftBreakMinutes?: number;
            /** Format: int32 */
            shiftGraceMinutes?: number | null;
            status?: string;
            /** Format: int32 */
            lateMinutes?: number;
            /** Format: int32 */
            earlyMinutes?: number;
            /** Format: int32 */
            workedMinutes?: number;
            /** Format: int32 */
            otNormalMinutes?: number;
            /** Format: int32 */
            otExtraMinutes?: number;
            /** Format: int32 */
            otSpecialMinutes?: number;
            source?: string | null;
            notes?: string | null;
            /** @description §7.payroll #23 — paid-leave flag. Populated when status == LEAVE from the leave type's paid column at approval time. Null on every other status. */
            paid?: boolean | null;
        };
        HolidayDto: {
            /** Format: int64 */
            id?: number;
            /** Format: date */
            holidayDate?: string;
            name?: string;
            type?: string;
            description?: string | null;
        };
        /** @description Roll-call view for a manager's team on a given date. Each row carries the report's employee summary plus the attendance signal we already collect — status, punch times, lateness. Active-leave overlay is stubbed (always null) until an employee-scoped APPROVED-leave endpoint lands upstream; the FE can still flag leave days using `status === 'LEAVE'` from the record. */
        TeamAttendanceToday: {
            /**
             * Format: date
             * @description Date the rows describe (defaults to today, Asia/Karachi).
             */
            asOf?: string;
            /** @description One row per covered employee. Order = direct reports first, then delegated reports. Capped at 500 — beyond that the response is truncated and `truncated=true`. */
            rows?: components["schemas"]["TeamAttendanceToday.Row"][];
            /** @description True when the coverage set exceeded the 500-row cap and was truncated. */
            truncated?: boolean;
        };
        "TeamAttendanceToday.ActiveLeave": {
            leaveTypeCode?: string;
            halfDay?: boolean;
        };
        "TeamAttendanceToday.Row": {
            employee?: components["schemas"]["EmployeeSummary"];
            /** @description Attendance status — PRESENT, ABSENT, LEAVE, HOLIDAY, WEEKEND, HALF_DAY, or PENDING_PUNCH when no record exists yet for the date. */
            status?: string;
            /** Format: date-time */
            checkIn?: string | null;
            /** Format: date-time */
            checkOut?: string | null;
            /** Format: int32 */
            lateMinutes?: number;
            /** @description Reserved for an active-leave overlay; always null in v1. */
            activeLeave?: components["schemas"]["TeamAttendanceToday.ActiveLeave"];
        };
        ExecutiveOverviewDto: {
            /** Format: int64 */
            headcount?: number;
            attritionRolling12mPct?: number;
            latestMonthOnTimePct?: number;
            latestMonthAbsenteeismPct?: number;
            latestMonthOtHours?: number;
            latestMonthPayrollCost?: number;
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
        /** @description Parsed name + optional version. */
        NameAndVersion: {
            /** @example Chrome */
            name?: string;
            /** @example 118.0.5993.117 */
            version?: string | null;
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
        /** @description Composite payload for the admin /admin/users/{id} console: user, role catalog, active sessions, recent login history, recent admin-visible audit events. Replaces the five queries the page fans out today. */
        UserOverview: {
            /** @description Full user record (from /admin/users/{id}). */
            user?: components["schemas"]["UserDto"];
            /** @description Full role catalog so the available-roles panel can render without a separate fetch (from /admin/roles). */
            roles?: components["schemas"]["RoleDto"][];
            /** @description Active sessions for this user (from /admin/users/{id}/sessions). */
            sessions?: components["schemas"]["SessionDto"][];
            /** @description Last 50 login-related events for this user (from /admin/users/{id}/login-history). */
            loginHistory?: components["schemas"]["LoginHistoryEntry"][];
            /** @description Last 25 audit events authored by this user, across services (from /audit/by-actor/{id}). */
            recentActions?: components["schemas"]["AuditEventDto"][];
        };
        /** @description Caller's active sessions, partitioned for the user-stripe and security panel. */
        AccountSessionsView: {
            /**
             * Format: int32
             * @description Total active sessions for the caller.
             * @example 3
             */
            active?: number;
            /** @description The session backing the current request, if it could be identified. */
            current?: components["schemas"]["SessionDto"];
            /** @description Other active sessions (excludes `current`). */
            others?: components["schemas"]["SessionDto"][];
        };
        /** @description Composite payload for the /account layout: identity, sessions, recent timeline. Replaces the three queries the user-stripe and overview tab fan out today. */
        AccountSummary: {
            /** @description Caller identity, roles, and effective permissions (from /auth/me). */
            identity?: components["schemas"]["MeResponse"];
            /** @description Active sessions for the caller (from /auth/sessions). */
            sessions?: components["schemas"]["AccountSessionsView"];
            /** @description Top user-visible events for the caller (from /timeline/user/{id}). */
            recentEvents?: components["schemas"]["TimelineEntry"][];
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
        /** @description ESS counterpart to /bff/employee/{id} — single round-trip for the signed-in employee's /account/profile tab. */
        AccountProfile: {
            employee?: components["schemas"]["EmployeeDto"];
            resolved?: components["schemas"]["EmployeeResolvedLabels"];
            /** @description Active contract row. */
            activeContract?: components["schemas"]["EmploymentContractDto"];
            /** @description Direct-reports preview — empty when the caller has no team. */
            team?: components["schemas"]["EmployeeDetail.ReportSummary"][];
            /**
             * Format: date
             * @description Probation end date when one is active, null otherwise.
             */
            upcomingProbationDeadline?: string | null;
        };
        /** @description Pre-computed flags the FE persona resolver needs but cannot derive from /auth/me alone. Today this is just the manager signal — does the caller have any direct reports? Permission-based scopes (HR / payroll / admin) stay on the FE since they are trivially derivable from /auth/me.permissions. */
        PersonaHints: {
            /** @description True when the caller has at least one direct report — drives the Manager dashboard option in the persona switcher. */
            hasDirectReports?: boolean;
            /**
             * Format: int32
             * @description Number of direct reports. 0 when the caller is not a manager or has no ESS-linked employee record.
             * @example 12
             */
            directReportsCount?: number;
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
                    "application/json": components["schemas"]["UsersByIdsResponse"];
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
                    "application/json": components["schemas"]["UsersByIdsResponse"];
                };
            };
            /** @description Too many ids in a single request. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UsersByIdsResponse"];
                };
            };
        };
    };
    requestTimeline: {
        parameters: {
            query?: {
                /** @description Page size (default 50, max 200). */
                limit?: number;
                /** @description Opaque cursor from a previous response's `nextCursor`. */
                cursor?: string;
            };
            header?: never;
            path: {
                /** @description One of: leave, overtime, regularization. */
                requestType: string;
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Timeline page (newest first). */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TimelinePage"];
                };
            };
            /** @description Request not found / not visible. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TimelinePage"];
                };
            };
            /** @description requestType is not one of leave, overtime, regularization. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TimelinePage"];
                };
            };
        };
    };
    search_1: {
        parameters: {
            query: {
                /** @description Search query. Server-side minimum length is 2; the FE skips below 2. */
                q: string;
                /**
                 * @description CSV of provider types to consult. Omit to consult every provider the caller can read.
                 * @example employee,payslip-mine
                 */
                types?: string;
                /** @description Per-provider hit cap. Default 5, hard ceiling 10. */
                limit?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Merged hits + per-provider diagnostics. Cache-Control: private, max-age=10. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SearchResponse"];
                };
            };
            /** @description No authenticated caller. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SearchResponse"];
                };
            };
        };
    };
    list: {
        parameters: {
            query?: {
                /** @description Which service(s) to query. */
                service?: "employee" | "payroll" | "all";
                /** @description Filter by status (RUNNING / COMPLETED / FAILED). Omit for all. */
                status?: string;
                /** @description Max rows fetched from each upstream slice (cap 200). */
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Merged saga rows. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SagasOverview"];
                };
            };
        };
    };
    catalog: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description List of available reports for this caller. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReportCatalogEntry"][];
                };
            };
        };
    };
    stream: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description SSE stream opened. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/event-stream": unknown;
                };
            };
            /** @description Missing identity headers (gateway didn't authenticate). */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/event-stream": components["schemas"]["SseEmitter"];
                };
            };
        };
    };
    timeline: {
        parameters: {
            query?: {
                /** @description Page size (default 50, max 200). */
                limit?: number;
                /** @description Opaque cursor from a previous response's `nextCursor`. */
                cursor?: string;
            };
            header?: never;
            path: {
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
                    "application/json": components["schemas"]["TimelinePage"];
                };
            };
        };
    };
    byIds_1: {
        parameters: {
            query: {
                /** @description Comma-separated employee ids; capped at 200 per call. */
                ids: number[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Resolved summaries; missing ids dropped silently. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeesByIdsResponse"];
                };
            };
            /** @description More than 200 ids. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeesByIdsResponse"];
                };
            };
        };
    };
    employee: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Employee id */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Composite payload assembled from employee + audit. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDetail"];
                };
            };
            /** @description Employee not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDetail"];
                };
            };
            /** @description A required downstream was unreachable. */
            502: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDetail"];
                };
            };
        };
    };
    payroll: {
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
                    "application/json": components["schemas"]["PayrollDashboard"];
                };
            };
        };
    };
    manager: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Composite payload. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ManagerDashboard"];
                };
            };
            /** @description Caller has no direct reports. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ManagerDashboard"];
                };
            };
        };
    };
    hr: {
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
                    "application/json": components["schemas"]["HrDashboard"];
                };
            };
        };
    };
    employee_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Composite payload. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDashboard"];
                };
            };
            /** @description Missing identity headers. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeDashboard"];
                };
            };
        };
    };
    admin: {
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
                    "application/json": components["schemas"]["AdminDashboard"];
                };
            };
        };
    };
    inbox: {
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
                    "application/json": components["schemas"]["ChatInboxResponse"];
                };
            };
        };
    };
    teamMonth: {
        parameters: {
            query?: {
                /** @description YYYY-MM, defaults to current month (Asia/Karachi). */
                month?: string;
            };
            header?: never;
            path: {
                employeeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Per-day list + month summary for the target. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceMonth"];
                };
            };
            /** @description Caller does not manage this employee and holds no covering delegation. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceMonth"];
                };
            };
        };
    };
    teamToday: {
        parameters: {
            query?: {
                /** @description Date to evaluate. Defaults to today (Asia/Karachi). */
                date?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Roll-call rows + asOf date. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TeamAttendanceToday"];
                };
            };
            /** @description Caller has no direct reports and holds no covering delegation. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TeamAttendanceToday"];
                };
            };
        };
    };
    executiveOverview: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Composite overview. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExecutiveOverviewDto"];
                };
            };
        };
    };
    executiveDashboardPdf: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description PDF bytes. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/pdf": string;
                };
            };
        };
    };
    overview: {
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
            /** @description Composite payload assembled from auth + audit. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserOverview"];
                };
            };
            /** @description Missing identity headers (gateway didn't authenticate). */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserOverview"];
                };
            };
            /** @description Caller lacks one of the required downstream permissions. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserOverview"];
                };
            };
            /** @description User not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserOverview"];
                };
            };
            /** @description A required downstream (auth or audit) was unreachable. */
            502: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UserOverview"];
                };
            };
        };
    };
    summary: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Composite payload assembled from auth + audit. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AccountSummary"];
                };
            };
            /** @description Missing identity headers (gateway didn't authenticate). */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AccountSummary"];
                };
            };
            /** @description A required downstream (auth or audit) was unreachable. */
            502: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AccountSummary"];
                };
            };
        };
    };
    accountProfile: {
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
                    "application/json": components["schemas"]["AccountProfile"];
                };
            };
        };
    };
    personaHints: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Persona hints (worst case: all-false). */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PersonaHints"];
                };
            };
            /** @description Missing identity headers. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PersonaHints"];
                };
            };
        };
    };
}

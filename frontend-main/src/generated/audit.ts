/* eslint-disable */
/**
 * GENERATED FILE — do not edit by hand.
 * Source: docs/services/<group>.openapi.json (refresh via `npm run gen:api:fetch`).
 * Regenerate: `npm run gen:api`.
 */

export interface paths {
    "/admin/audit/replay": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Replay a bounded Kafka offset range
         * @description Idempotent ingest makes duplicates safe — they're counted as `skipped`. Use `dryRun = true` to size the work first. Requires `audit.admin`.
         */
        post: operations["replay"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/audit/dead-letters/{id}/replay": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Replay one dead-letter row
         * @description Re-runs the stored envelope through ingest. On INSERTED/DUPLICATE the dead-letter row is deleted; on STILL_FAILING the row is updated with the new reason and the attempt counter advances. Requires `audit.admin`.
         */
        post: operations["replayDeadLetter"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/timeline/{entityType}/{entityId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Read an entity timeline (user-visible events only)
         * @description Cursor pagination via the opaque `cursor` returned in the previous page's `nextCursor`.
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
    "/timeline/self/sign-in-events": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List my sign-in events (cursor-paginated)
         * @description Caller's login.success / login.failed / user.locked / user.unlocked events, newest first. Pass `cursor` from the previous response's `nextCursor` to scroll back. Always scoped to the calling user; no userId param.
         */
        get: operations["mySignInEvents"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/audit/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Search audit events
         * @description Substring match on `message` (case-insensitive) plus optional filters on entity type, action, correlationId, actorUserId, userVisible, and time range. Standard `page`, `size`, `sort` apply (default sort: `occurredAt,desc`). All filters are AND-combined; pass only what you need. The drawer's "View by correlation" deep-link uses `correlationId`. Time-window guard (§6.7): when `from` is omitted it defaults to 90 days back; the `(from..to)` window is capped at 1 year. Requires `audit.read`.
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
    "/audit/facets": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Distinct facet values for the explorer
         * @description Observed entity types, actions, and source services with frequencies. Cached for 5 minutes per process — fine for the explorer's filter dropdowns. Requires `audit.read`.
         */
        get: operations["facets"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/audit/events/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Single audit event by id
         * @description Returns the full event including before/after/changes/metadata. The drawer hits this for the "open detail" path. Owning audit-service lets us add redaction policy here later without touching producers. Requires `audit.read`.
         */
        get: operations["event"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/audit/by-entity/{entityType}/{entityId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Full history for one entity (cursor-paginated)
         * @description Newest first. Use `from`/`to` to bound; `limit` caps page size (default 100, max 500). Pass `cursor` from the previous response's `nextCursor` to fetch the next page; `nextCursor` is null when the list is exhausted. Requires `audit.read`.
         */
        get: operations["byEntity"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/audit/by-correlation/{correlationId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * All events sharing a correlation id
         * @description Paginated reconstruction of a single request's footprint across services. Default page size 200, max 500 — pathological correlations (long-running sagas with hundreds of audit emits) need cursoring. Requires `audit.read`.
         */
        get: operations["byCorrelation"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/audit/by-actor/{userId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Audit events authored by a user
         * @description Standard `page`, `size`, `sort` query params apply. Requires `audit.read`.
         */
        get: operations["byActor"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/audit/health": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Consumer health per audit topic
         * @description Per-topic and per-partition end offsets, committed offsets, and lag. Requires `audit.admin`.
         */
        get: operations["health"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/audit/dead-letters": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List parked envelopes
         * @description Filter by source service, reason, and lower bound on `receivedAt`. Listing projection drops `rawPayload` to keep responses small — fetch the full row via `GET /admin/audit/dead-letters/{id}`. Requires `audit.admin`.
         */
        get: operations["listDeadLetters"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/audit/dead-letters/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Fetch one dead-letter row (full raw payload)
         * @description Includes the original envelope JSON. Requires `audit.admin`.
         */
        get: operations["getDeadLetter"];
        put?: never;
        post?: never;
        /**
         * Discard one dead-letter row
         * @description Permanently drops the parked envelope. Use when ingest can never succeed (e.g. the data is genuinely corrupt and not worth recovering). Requires `audit.admin`.
         */
        delete: operations["discardDeadLetter"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /** @description Replay a bounded Kafka offset range from one audit topic. Idempotent ingest makes duplicates safe. */
        ReplayRequest: {
            /**
             * @description Audit topic to replay. Must be one of the configured `<svc>.audit` topics.
             * @example employee.audit
             */
            topic: string;
            /**
             * Format: int64
             * @description Inclusive lower bound (offset). 0-based.
             * @example 12345
             */
            fromOffset: number;
            /**
             * Format: int64
             * @description Exclusive upper bound (offset). Must be greater than `fromOffset`.
             * @example 13000
             */
            toOffset: number;
            /**
             * Format: int32
             * @description Restrict to a single partition. Omit to replay the offset range across all partitions.
             * @example 0
             */
            partition?: number | null;
            /**
             * @description If true, count what would be replayed without writing. The response includes a `replayToken` you must echo back on the non-dry call within the next ~5 minutes.
             * @default false
             * @example false
             */
            dryRun: boolean;
            /**
             * @description If true, return an approximate row count without polling Kafka. Cheap — useful for the "About to replay ~412 records" prompt before the real button click. Mutually exclusive with `dryRun`.
             * @default false
             * @example false
             */
            countOnly: boolean | null;
            /**
             * @description Token returned by a previous `dryRun: true` call. Required for `dryRun: false` to prevent firing a non-dry replay without dry-running first. Tokens are valid for ~5 minutes.
             * @example 01HZK7G6Y0PSV2TPC9N9XSH3WC
             */
            replayToken?: string | null;
        };
        /** @description Outcome of a replay request. */
        ReplayResult: {
            /**
             * Format: int32
             * @description Number of records inserted (or, in dry-run, that would have been inserted; in count-only mode, the approximate upper-bound row count from the offset range).
             * @example 412
             */
            replayed?: number;
            /**
             * Format: int32
             * @description Number of records skipped — duplicates (already ingested) and malformed payloads.
             * @example 53
             */
            skipped?: number;
            /**
             * Format: int32
             * @description Number of records that failed with an exception during ingest.
             * @example 0
             */
            errors?: number;
            /**
             * @description Echoes the request — true if no rows were written.
             * @example false
             */
            dryRun?: boolean;
            /**
             * @description Set to true on count-only responses (cheap upper-bound estimate, no Kafka poll).
             * @example false
             */
            countOnly?: boolean | null;
            /**
             * @description Short-lived token issued on dryRun:true responses. Echo back as `replayToken` on the non-dry call. Null on non-dry-run responses.
             * @example 01HZK7G6Y0PSV2TPC9N9XSH3WC
             */
            replayToken?: string | null;
        };
        /** @description Outcome of replaying a single dead-letter row. */
        DeadLetterReplayResult: {
            /**
             * @description Status: INSERTED (row written + dead-letter deleted), DUPLICATE (audit_events already had it; dead-letter deleted), STILL_FAILING (ingest rejected again; dead-letter row updated with new reason).
             * @example INSERTED
             * @enum {string}
             */
            status?: "INSERTED" | "DUPLICATE" | "STILL_FAILING";
            /**
             * @description If STILL_FAILING, the new reason ingest returned. Else null.
             * @enum {string|null}
             */
            reason?: "MALFORMED_JSON" | "MISSING_EVENT_ID" | "OVERSIZED_EVENT_ID" | "MISSING_REQUIRED_PAYLOAD" | "UNPARSEABLE_OCCURRED_AT" | null;
            /** @description If STILL_FAILING, free-form detail. Else null. */
            detail?: string | null;
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
        /** @description Cursor-paginated response. */
        CursorPageSignInEvent: {
            content?: components["schemas"]["SignInEvent"][];
            /**
             * @description Opaque cursor for the next page, null at end.
             * @example MjAyNi0wNC0yOFQwOToxMjozM1o6NTQyMQ
             */
            nextCursor?: string | null;
        };
        /** @description One sign-in event for the caller's own user record. */
        SignInEvent: {
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
             * @example false
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
            valueNode?: boolean;
            object?: boolean;
            pojo?: boolean;
            short?: boolean;
            int?: boolean;
            container?: boolean;
            number?: boolean;
            embeddedValue?: boolean;
        };
        PageResponseAuditEventDto: {
            content?: components["schemas"]["AuditEventDto"][];
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
        /** @description One distinct value with its row count. */
        Facet: {
            /** @example employee */
            value?: string;
            /**
             * Format: int64
             * @example 1248
             */
            count?: number;
        };
        /** @description Observed facet values across audit_events, ordered by frequency. */
        FacetsResponse: {
            entityTypes?: components["schemas"]["Facet"][];
            actions?: components["schemas"]["Facet"][];
            sourceServices?: components["schemas"]["Facet"][];
        };
        /** @description Cursor-paginated response. */
        CursorPageAuditEventDto: {
            content?: components["schemas"]["AuditEventDto"][];
            /**
             * @description Opaque cursor for the next page, null at end.
             * @example MjAyNi0wNC0yOFQwOToxMjozM1o6NTQyMQ
             */
            nextCursor?: string | null;
        };
        /** @description Per-topic consumer state — partition end offsets, committed offsets, and lag. */
        HealthResponse: {
            /** @description Per-topic snapshot. */
            topics?: components["schemas"]["TopicHealth"][];
        };
        /** @description Per-partition consumer state. */
        PartitionHealth: {
            /**
             * Format: int32
             * @example 0
             */
            partition?: number;
            /**
             * Format: int64
             * @example 13042
             */
            endOffset?: number;
            /**
             * Format: int64
             * @example 13042
             */
            committedOffset?: number;
            /**
             * Format: int64
             * @example 0
             */
            lag?: number;
        };
        /** @description Per-topic consumer state. */
        TopicHealth: {
            /** @example employee.audit */
            topic?: string;
            /** @example audit.employee */
            consumerGroup?: string;
            /**
             * Format: int64
             * @description Sum of (endOffset - committedOffset) across partitions.
             * @example 0
             */
            totalLag?: number;
            partitions?: components["schemas"]["PartitionHealth"][];
        };
        /** @description Audit envelope that ingest rejected. Held until an operator replays or discards. */
        AuditDeadLetterDto: {
            /**
             * Format: int64
             * @description Dead-letter row id.
             * @example 42
             */
            id?: number;
            /**
             * @description Service that emitted the envelope.
             * @example auth
             */
            sourceService?: string;
            /**
             * @description Source Kafka topic.
             * @example auth.audit
             */
            topic?: string;
            /**
             * Format: int32
             * @description Source Kafka partition.
             * @example 2
             */
            partition?: number;
            /**
             * Format: int64
             * @description Source Kafka offset.
             * @example 184273
             */
            offset?: number;
            /**
             * @description Source Kafka key.
             * @example user-1042
             */
            key?: string | null;
            /**
             * @description Why ingest rejected this envelope.
             * @example UNPARSEABLE_OCCURRED_AT
             * @enum {string}
             */
            reason?: "MALFORMED_JSON" | "MISSING_EVENT_ID" | "OVERSIZED_EVENT_ID" | "MISSING_REQUIRED_PAYLOAD" | "UNPARSEABLE_OCCURRED_AT";
            /** @description Free-form detail captured at rejection. */
            reasonDetail?: string | null;
            /**
             * Format: date-time
             * @description When the consumer parked the envelope.
             */
            receivedAt?: string;
            /**
             * Format: date-time
             * @description Last time an operator hit /replay.
             */
            lastReplayAt?: string | null;
            /**
             * Format: int32
             * @description Number of replay attempts.
             * @example 0
             */
            replayAttempts?: number;
            /** @description Original envelope JSON (truncated above 1MB). */
            rawPayload?: string;
        };
        PageResponseAuditDeadLetterDto: {
            content?: components["schemas"]["AuditDeadLetterDto"][];
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
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    replay: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReplayRequest"];
            };
        };
        responses: {
            /** @description Replay outcome. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReplayResult"];
                };
            };
            /** @description Unknown topic, or invalid offset range. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReplayResult"];
                };
            };
            /** @description Topic has no partitions matching the request. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReplayResult"];
                };
            };
        };
    };
    replayDeadLetter: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Replay outcome. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeadLetterReplayResult"];
                };
            };
            /** @description No row with that id. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeadLetterReplayResult"];
                };
            };
        };
    };
    timeline: {
        parameters: {
            query?: {
                /** @description Inclusive lower bound (ISO-8601 instant). */
                from?: string;
                /** @description Exclusive upper bound (ISO-8601 instant). */
                to?: string;
                /** @description Page size (default 50, max 200). */
                limit?: number;
                /** @description Opaque cursor from a previous response. */
                cursor?: string;
            };
            header?: never;
            path: {
                /**
                 * @description Entity type, e.g. `employee` or `user`.
                 * @example user
                 */
                entityType: string;
                /**
                 * @description Entity id (string for flexibility).
                 * @example 1042
                 */
                entityId: string;
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
            /** @description Caller may not read this entity's timeline. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TimelinePage"];
                };
            };
        };
    };
    mySignInEvents: {
        parameters: {
            query?: {
                /** @description Inclusive lower bound (ISO-8601 instant). */
                from?: string;
                /** @description Exclusive upper bound (ISO-8601 instant). */
                to?: string;
                /** @description Page size (default 100, max 500). */
                limit?: number;
                /** @description Opaque cursor from a previous response. */
                cursor?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Page of sign-in events for the caller. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CursorPageSignInEvent"];
                };
            };
            /** @description Authentication required. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CursorPageSignInEvent"];
                };
            };
        };
    };
    search: {
        parameters: {
            query?: {
                /** @description Free-text substring matched against `message`. */
                q?: string;
                /** @description Filter by entity type. */
                entityType?: string;
                /** @description Filter by action. */
                action?: string;
                /** @description Exact correlation-id match — supports the drawer's "View by correlation" deep-link without falling back to /audit/by-correlation/{id}. */
                correlationId?: string;
                /** @description Exact actor user id match. Same data as /audit/by-actor/{id} but composable with the other filters. */
                actorUserId?: number;
                /** @description Tri-state: omit for all, true for user-facing, false for system-only. */
                userVisible?: boolean;
                /** @description Window start (inclusive). Defaults to 90 days back when omitted. */
                from?: string;
                /** @description Window end (inclusive). Defaults to now. */
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Time window > 1 year — narrow `from`/`to` and retry. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseAuditEventDto"];
                };
            };
        };
    };
    facets: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Facets, ordered by frequency desc. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FacetsResponse"];
                };
            };
        };
    };
    event: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description Event id.
                 * @example 5421
                 */
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Audit event found. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AuditEventDto"];
                };
            };
            /** @description No event with that id. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AuditEventDto"];
                };
            };
        };
    };
    byEntity: {
        parameters: {
            query?: {
                /** @description Inclusive lower bound (ISO-8601 instant). */
                from?: string;
                /** @description Exclusive upper bound (ISO-8601 instant). */
                to?: string;
                /** @description Page size (default 100, max 500). */
                limit?: number;
                /** @description Opaque cursor from a previous response. */
                cursor?: string;
            };
            header?: never;
            path: {
                /**
                 * @description Entity type.
                 * @example employee
                 */
                entityType: string;
                /**
                 * @description Entity id.
                 * @example 1057
                 */
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Page of audit events for the entity. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CursorPageAuditEventDto"];
                };
            };
        };
    };
    byCorrelation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Correlation id. */
                correlationId: string;
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
                    "application/json": components["schemas"]["PageResponseAuditEventDto"];
                };
            };
        };
    };
    byActor: {
        parameters: {
            query?: {
                from?: string;
                to?: string;
            };
            header?: never;
            path: {
                /**
                 * @description Actor user id.
                 * @example 42
                 */
                userId: number;
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
                    "application/json": components["schemas"]["PageResponseAuditEventDto"];
                };
            };
        };
    };
    health: {
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
                    "application/json": components["schemas"]["HealthResponse"];
                };
            };
        };
    };
    listDeadLetters: {
        parameters: {
            query?: {
                /** @description Filter by source service (auth, employee, …). */
                source?: string;
                /** @description Filter by skip reason. */
                reason?: "MALFORMED_JSON" | "MISSING_EVENT_ID" | "OVERSIZED_EVENT_ID" | "MISSING_REQUIRED_PAYLOAD" | "UNPARSEABLE_OCCURRED_AT";
                /** @description Inclusive lower bound on receivedAt (ISO-8601). */
                since?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Page of dead-letter rows, newest first. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseAuditDeadLetterDto"];
                };
            };
        };
    };
    getDeadLetter: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Dead-letter row. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AuditDeadLetterDto"];
                };
            };
            /** @description No row with that id. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AuditDeadLetterDto"];
                };
            };
        };
    };
    discardDeadLetter: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Discarded. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description No row with that id. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
}

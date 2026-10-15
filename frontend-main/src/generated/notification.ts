/* eslint-disable */
/**
 * GENERATED FILE — do not edit by hand.
 * Source: docs/services/<group>.openapi.json (refresh via `npm run gen:api:fetch`).
 * Regenerate: `npm run gen:api`.
 */

export interface paths {
    "/notifications/{id}/read": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Mark a notification read */
        post: operations["markRead"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/notifications/read": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Bulk mark-read
         * @description Mark a set of ids read in one call. Ids that don't belong to the caller or that are already read are silently skipped — `marked` reflects only rows that actually transitioned.
         */
        post: operations["markRead_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/notifications/read-all": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Mark all my notifications read */
        post: operations["markAllRead"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/notifications/templates": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List templates
         * @description Requires `notification.template.read`.
         */
        get: operations["list"];
        put?: never;
        /**
         * Create a template
         * @description Requires `notification.template.write`.
         */
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/admin/notifications/announce": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Send a broadcast
         * @description Caller resolves the recipient list (employee-service department/role pickers etc.) and passes the user ids in. The service fans out N rows + N publishes — recipient resolution is not done here. Requires `notification.announce`.
         */
        post: operations["announce"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/notifications/preferences": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List my preferences
         * @description Returns only the explicit overrides. Anything not listed defaults to enabled.
         */
        get: operations["list_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Patch my preferences
         * @description Sparse — only the keys you pass are touched.
         */
        patch: operations["update"];
        trace?: never;
    };
    "/admin/notifications/templates/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Patch a template
         * @description Requires `notification.template.write`.
         */
        patch: operations["update_1"];
        trace?: never;
    };
    "/notifications": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List my notifications (newest first)
         * @description Standard `page`, `size`, `sort` query params apply (default sort: `createdAt,desc`). Filter by `category` to restrict to a single bell-UI grouping.
         */
        get: operations["list_2"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/notifications/unread-count": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Bell-badge count
         * @description Cheap unread count (Redis-cached). Falls back to a DB scan on cache miss.
         */
        get: operations["unreadCount"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/notifications/types": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Type catalog
         * @description All active notification types — what the FE preferences page renders as toggle rows, and what the admin templates list shows. Backed by the active rows in notification_templates so adding a new type means adding a template, not a code change.
         */
        get: operations["types"];
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
        /** @description A single bell-feed entry for a user. */
        NotificationDto: {
            /**
             * Format: int64
             * @description Internal id.
             * @example 10421
             */
            id?: number;
            /**
             * Format: int64
             * @description Recipient user id.
             * @example 42
             */
            userId?: number;
            /**
             * @description Canonical type, e.g. LEAVE_APPROVED.
             * @example LEAVE_APPROVED
             */
            type?: string;
            /** @description Rendered title. */
            title?: string;
            /** @description Rendered body. */
            body?: string | null;
            /** @description Deep link path the web app navigates to. */
            link?: string | null;
            /** @description Opaque JSON payload. */
            payload?: string | null;
            /**
             * @description Coarse grouping for the bell UI.
             * @enum {string|null}
             */
            category?: "APPROVAL" | "ANNOUNCEMENT" | "SYSTEM" | "PAYROLL" | null;
            /**
             * @description Priority hint; defaults to NORMAL.
             * @enum {string}
             */
            priority?: "LOW" | "NORMAL" | "HIGH";
            /**
             * Format: date-time
             * @description When the user marked it read; null if unread.
             */
            readAt?: string | null;
            /**
             * Format: date-time
             * @description When the row was created.
             */
            createdAt?: string;
        };
        /** @description Mark a set of notifications read in one call. Ids that don't belong to the caller or that are already read are silently skipped. */
        BulkMarkReadRequest: {
            /**
             * @description Notification ids to mark read.
             * @example [
             *       10421,
             *       10422,
             *       10423
             *     ]
             */
            ids: number[];
        };
        MarkReadResponse: {
            /**
             * Format: int32
             * @description Number of rows that transitioned from unread to read.
             */
            marked?: number;
        };
        /** @description Bell-badge count. */
        UnreadCountResponse: {
            /**
             * Format: int64
             * @description Number of unread notifications for the caller.
             * @example 3
             */
            count?: number;
        };
        CreateTemplateRequest: {
            /**
             * @description Canonical type key.
             * @example LEAVE_APPROVED
             */
            type: string;
            /**
             * @description Human-readable label for preference toggles and admin lists.
             * @example Leave fully approved
             */
            label?: string;
            /** @description One-line explanation of when this notification fires. */
            description?: string;
            /**
             * @description Title template.
             * @example Your leave was approved
             */
            titleTpl: string;
            /** @description Body template. */
            bodyTpl?: string;
            /** @description Link template. */
            linkTpl?: string;
            /**
             * @description Coarse grouping for the bell UI.
             * @enum {string}
             */
            category?: "APPROVAL" | "ANNOUNCEMENT" | "SYSTEM" | "PAYROLL";
        };
        /** @description Renderable template for a notification type. */
        TemplateDto: {
            /**
             * Format: int64
             * @description Internal id.
             */
            id?: number;
            /**
             * @description Canonical type key.
             * @example LEAVE_APPROVED
             */
            type?: string;
            /**
             * @description Human-readable label for preference toggles and admin lists.
             * @example Leave fully approved
             */
            label?: string | null;
            /** @description One-line explanation of when this notification fires. */
            description?: string | null;
            /** @description Title template (Mustache-style {{placeholders}}). */
            titleTpl?: string;
            /** @description Body template. */
            bodyTpl?: string | null;
            /** @description Link template. */
            linkTpl?: string | null;
            /**
             * @description Coarse grouping.
             * @enum {string|null}
             */
            category?: "APPROVAL" | "ANNOUNCEMENT" | "SYSTEM" | "PAYROLL" | null;
            /** @description Inactive templates are skipped at dispatch time. */
            active?: boolean;
        };
        /** @description Admin broadcast. The list of recipient user ids is resolved by the caller — typically the BFF's department/role-based picker. The notification service just fans out N rows + N publishes. */
        AnnounceRequest: {
            /** @description Recipient user ids. */
            userIds: number[];
            /** @description Title shown to recipients. */
            title: string;
            /** @description Body shown to recipients. */
            body?: string;
            /** @description Optional deep link. */
            link?: string;
            /**
             * @description Priority hint; defaults to NORMAL.
             * @enum {string}
             */
            priority?: "LOW" | "NORMAL" | "HIGH";
        };
        AnnounceResponse: {
            /**
             * Format: int32
             * @description Number of notifications inserted.
             */
            delivered?: number;
        };
        /** @description Sparse map of notification type → enabled flag. Only the keys present are touched. */
        UpdatePreferencesRequest: {
            /**
             * @description Type → enabled toggle. Pass `false` to opt out of a type.
             * @example {
             *       "LEAVE_APPROVED": false,
             *       "PAYSLIP_READY": true
             *     }
             */
            preferences: {
                [key: string]: boolean;
            };
        };
        /** @description Per-user-per-type opt-out toggle. */
        PreferenceDto: {
            /**
             * @description Notification type.
             * @example LEAVE_APPROVED
             */
            type?: string;
            /** @description True if the user wants this type delivered. */
            enabled?: boolean;
        };
        /** @description Patch — only non-null fields apply. */
        UpdateTemplateRequest: {
            /** @description New label. */
            label?: string;
            /** @description New description. */
            description?: string;
            /** @description New title template. */
            titleTpl?: string;
            /** @description New body template. */
            bodyTpl?: string;
            /** @description New link template. */
            linkTpl?: string;
            /**
             * @description New category.
             * @enum {string}
             */
            category?: "APPROVAL" | "ANNOUNCEMENT" | "SYSTEM" | "PAYROLL";
            /** @description Activate/deactivate. */
            active?: boolean;
        };
        PageResponseNotificationDto: {
            content?: components["schemas"]["NotificationDto"][];
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
        /** @description Type catalog entry — what the FE preferences page and admin templates list render. Backed by the active row in notification_templates for that type. */
        NotificationTypeDto: {
            /**
             * @description Canonical type key.
             * @example LEAVE_APPROVED
             */
            type?: string;
            /**
             * @description Human-readable label for the toggle row.
             * @example Leave fully approved
             */
            label?: string;
            /** @description One-line explanation of when this notification fires. */
            description?: string | null;
            /**
             * @description Coarse grouping for category filters.
             * @enum {string|null}
             */
            category?: "APPROVAL" | "ANNOUNCEMENT" | "SYSTEM" | "PAYROLL" | null;
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
    markRead: {
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
            /** @description Marked read. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["NotificationDto"];
                };
            };
            /** @description Not found or not yours. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["NotificationDto"];
                };
            };
        };
    };
    markRead_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BulkMarkReadRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MarkReadResponse"];
                };
            };
        };
    };
    markAllRead: {
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
                    "application/json": components["schemas"]["UnreadCountResponse"];
                };
            };
        };
    };
    list: {
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
                    "application/json": components["schemas"]["TemplateDto"][];
                };
            };
        };
    };
    create: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateTemplateRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TemplateDto"];
                };
            };
            /** @description Type already in use. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TemplateDto"];
                };
            };
        };
    };
    announce: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AnnounceRequest"];
            };
        };
        responses: {
            /** @description Broadcast queued; rows persisted. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AnnounceResponse"];
                };
            };
            /** @description No recipients, or list exceeds the configured cap. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AnnounceResponse"];
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
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PreferenceDto"][];
                };
            };
        };
    };
    update: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdatePreferencesRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PreferenceDto"][];
                };
            };
        };
    };
    update_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateTemplateRequest"];
            };
        };
        responses: {
            /** @description Template not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TemplateDto"];
                };
            };
        };
    };
    list_2: {
        parameters: {
            query?: {
                /** @description Set true to return only unread items. */
                unread?: boolean;
                /** @description Filter by category. Omit for all. */
                category?: "APPROVAL" | "ANNOUNCEMENT" | "SYSTEM" | "PAYROLL";
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
                    "application/json": components["schemas"]["PageResponseNotificationDto"];
                };
            };
        };
    };
    unreadCount: {
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
                    "application/json": components["schemas"]["UnreadCountResponse"];
                };
            };
        };
    };
    types: {
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
                    "application/json": components["schemas"]["NotificationTypeDto"][];
                };
            };
        };
    };
}

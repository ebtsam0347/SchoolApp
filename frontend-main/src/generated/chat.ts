/* eslint-disable */
/**
 * GENERATED FILE — do not edit by hand.
 * Source: docs/services/<group>.openapi.json (refresh via `npm run gen:api:fetch`).
 * Regenerate: `npm run gen:api`.
 */

export interface paths {
    "/chat/messages/{id}/delivery": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Acknowledge delivery of a message to the caller
         * @description Idempotent. Inserts (messageId, callerUserId) into `message_deliveries` and publishes `chat.message.delivered` to the sender's Redis channel.
         */
        post: operations["delivered"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/conversations/{id}/typing": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Signal that I'm typing in this conversation
         * @description Server publishes `chat.typing` to each conversation member except the caller. BROADCAST conversations are skipped — typing makes no sense for one-way channels.
         */
        post: operations["typing"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/conversations/{id}/read": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Update my read cursor
         * @description Sets `last_read_message_id` for caller's row in this conversation. Only moves forward — older values are ignored.
         */
        post: operations["markRead"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/conversations/{id}/messages": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Page messages in a conversation (newest first)
         * @description Keyset pagination by message id desc. Pass the lowest id from the previous page as `beforeId` for the next older page.
         */
        get: operations["page"];
        put?: never;
        /**
         * Send a message
         * @description Caller must be a member of the conversation. Returns the persisted message; live delivery to other members is dispatched after the transaction commits.
         */
        post: operations["send"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/conversations/{id}/members": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Add a member to a group
         * @description Owner-only. 409 if user already a member or member cap reached.
         */
        post: operations["addMember"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/conversations/group": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Create a group conversation
         * @description Caller becomes OWNER. Member cap configured via `hrms.chat.group-member-cap` (default 50, including caller).
         */
        post: operations["createGroup"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/conversations/dm": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Get-or-create a 1:1 DM
         * @description Idempotent. Returns the existing DM if one already exists between caller and `otherUserId`.
         */
        post: operations["createOrGetDm"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/broadcast/messages": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Post an announcement to the org-wide broadcast channel
         * @description Caller must hold `chat.broadcast.write`. Live SSE fanout reaches every connected user via the global `chat:broadcast` channel; bell entries are dropped only for users in `mentionedUserIds`.
         */
        post: operations["post"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/attachments/upload-url": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Mint a pre-signed PUT URL for a chat attachment
         * @description Validates content-type whitelist + size cap. The returned objectKey is bound to the caller's user prefix; only the caller can later attach this object to a message.
         */
        post: operations["uploadUrl"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/messages/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /** Soft-delete your own message */
        delete: operations["deleteOwn"];
        options?: never;
        head?: never;
        /**
         * Edit your own message
         * @description Allowed within the edit window (default 15 min). 409 outside the window or on a deleted message.
         */
        patch: operations["edit"];
        trace?: never;
    };
    "/chat/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Search messages in a conversation
         * @description Postgres tsvector + GIN under the hood. Multi-word queries are AND-combined; soft-deleted messages are excluded. Response embeds the conversation header so the FE can render thread title without a separate fetch.
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
    "/chat/presence": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get presence for a list of users
         * @description Bulk lookup. Caller passes a comma-separated `userIds` query param; server returns one row per id the caller is permitted to see (i.e. the caller themselves plus users they share a conversation with), preserving request order. IDs the caller doesn't share a conversation with are silently omitted to prevent presence enumeration of arbitrary user ids.
         */
        get: operations["get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/conversations": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List my conversations
         * @description Conversations where I'm a member, ordered by last-message time desc. Includes unread counts and member ids for chip rendering.
         */
        get: operations["mine"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/conversations/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Conversation header + members
         * @description Returns membership and unread count. Caller must be a member, except for the BROADCAST channel which everyone can fetch.
         */
        get: operations["get_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/admin/conversations": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List a user's conversations (admin)
         * @description Includes the org-wide BROADCAST. Unread counts are reported from the target user's perspective.
         */
        get: operations["listForUser"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/admin/conversations/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get any conversation header (admin)
         * @description Returns members + unread count from the target user's perspective. Membership is not enforced for the caller.
         */
        get: operations["getForUser"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/admin/conversations/{id}/messages": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Page any conversation's messages (admin)
         * @description Same shape as the regular paged messages endpoint, but skips the membership check. Useful for compliance review.
         */
        get: operations["pageMessages"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/messages/{id}/admin": {
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
         * Admin hard-delete (audit-emitting)
         * @description Soft-deletes any message; emits an audit event. Requires `chat.messages.delete`.
         */
        delete: operations["deleteAsAdmin"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/chat/conversations/{id}/members/{userId}": {
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
         * Remove a member or leave a group
         * @description Owners can remove anyone; members can remove themselves only. Last owner can't leave a non-empty group.
         */
        delete: operations["removeMember"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /** @description Fire-and-forget typing signal. The client sends `stopped: true` to clear early; otherwise the indicator self-clears client-side after a short timeout. */
        TypingRequest: {
            /** @description True to clear, false (or absent) to indicate typing. */
            stopped?: boolean | null;
        };
        /** @description Update my last-read pointer for a conversation. */
        MarkReadRequest: {
            /**
             * Format: int64
             * @description Highest message id I've read in this conversation.
             */
            lastReadMessageId: number;
        };
        /** @description Reference to an already-uploaded MinIO object. The server stat()s each before persisting; missing objects yield 422. */
        AttachmentRef: {
            /** @description Key returned by /chat/attachments/upload-url. */
            objectKey: string;
            /** @description Content-type from the upload-url request. */
            contentType: string;
            /**
             * Format: int64
             * @description Size in bytes; the server still verifies against MinIO.
             */
            sizeBytes: number;
            /** @description Original filename for display. */
            originalName: string;
        };
        /** @description Send a message to a conversation. At least one of body/attachments must be present. */
        SendMessageRequest: {
            /** @description Message body. Empty string allowed if at least one attachment is present. */
            body: string;
            /**
             * @description User ids the client tagged with @mentions. Server validates each is a member.
             * @example [
             *       203,
             *       204
             *     ]
             */
            mentionedUserIds?: number[] | null;
            /** @description Attachments to attach to this message. Each must already be uploaded via /chat/attachments/upload-url. */
            attachments?: components["schemas"]["AttachmentRef"][] | null;
        };
        /** @description Stored attachment metadata + a short-lived signed download URL. */
        AttachmentDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            messageId?: number;
            objectKey?: string;
            contentType?: string;
            /** Format: int64 */
            sizeBytes?: number;
            originalName?: string;
            /** @description Short-lived signed download URL. */
            downloadUrl?: string | null;
        };
        /** @description A message within a conversation. */
        MessageDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            conversationId?: number;
            /** Format: int64 */
            senderUserId?: number;
            /**
             * @description Sender's auth username, captured at send-time from the audit `created_by` column. Stable for rendering bubble headers / mention chips without a resolver round-trip.
             * @example ahmed.khan
             */
            senderUsername?: string;
            /** @description Message body. Empty when soft-deleted (deletedAt is set). */
            body?: string;
            /** Format: date-time */
            editedAt?: string | null;
            /** Format: date-time */
            deletedAt?: string | null;
            /** @enum {string|null} */
            deleteReason?: "SELF" | "ADMIN" | null;
            /** Format: date-time */
            createdAt?: string;
            /** @description Attachments uploaded with this message; each carries a short-lived signed download URL. */
            attachments?: components["schemas"]["AttachmentDto"][];
            /** @description User ids @-mentioned by the sender at send time. */
            mentionedUserIds?: number[];
        };
        /** @description Add a member to a group conversation. */
        AddMemberRequest: {
            /**
             * Format: int64
             * @description User id to add.
             */
            userId: number;
        };
        /** @description Create an ad-hoc group conversation. Caller becomes OWNER. */
        CreateGroupRequest: {
            /**
             * @description Group title shown in the conversation list.
             * @example Q3 launch room
             */
            title: string;
            /** @description Initial member user ids (excluding caller — caller is auto-added as OWNER). */
            memberUserIds: number[];
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
        /** @description Get-or-create a 1:1 DM with another employee. */
        CreateDmRequest: {
            /**
             * Format: int64
             * @description User id of the other participant.
             * @example 203
             */
            otherUserId: number;
        };
        /** @description Request a pre-signed PUT URL for a chat attachment. Server validates the content-type whitelist and size cap before signing. */
        ChatUploadUrlRequest: {
            /**
             * @description MIME type the client will PUT.
             * @example image/png
             */
            contentType: string;
            /**
             * Format: int64
             * @description Size in bytes the client will PUT. Used to enforce the per-attachment size cap.
             */
            sizeBytes: number;
            /** @description Original filename — preserved on the metadata row. */
            originalName: string;
        };
        /** @description Pre-signed PUT URL for a chat attachment. Pass the returned objectKey back in the next send-message request. */
        ChatUploadUrlResponse: {
            /**
             * @description Bucket the URL targets.
             * @example chat-attachments
             */
            bucket?: string;
            /** @description Object key the URL is signed for. Include this in the message's `attachments[]`. */
            objectKey?: string;
            /** @description Pre-signed PUT URL. */
            uploadUrl?: string;
            /**
             * Format: date-time
             * @description When the URL stops working (UTC).
             */
            expiresAt?: string;
        };
        /** @description Edit your own message within the edit window (15 minutes). */
        EditMessageRequest: {
            /** @description New message body. */
            body: string;
        };
        /** @description Lightweight conversation identity — id, title, type — for search-result grouping. */
        SearchConversationHeader: {
            /** Format: int64 */
            id?: number;
            /** @enum {string} */
            type?: "DM" | "GROUP" | "BROADCAST";
            /** @description Null for DMs (no group title). */
            title?: string | null;
        };
        /** @description Search hits inside a single conversation. The conversation header is embedded so the FE can render thread title + type without an extra fetch. */
        SearchResultsResponse: {
            /** @description Conversation header for the searched thread. */
            conversation?: components["schemas"]["SearchConversationHeader"];
            /** @description Matching messages, newest-first within the configured limit. */
            messages?: components["schemas"]["MessageDto"][];
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
        /** @description A page of messages for a conversation, newest first. Use the lowest id as `beforeId` to fetch the next older page. */
        MessagePageResponse: {
            items?: components["schemas"]["MessageDto"][];
            /** @description True when more older messages exist before the lowest id in `items`. */
            hasMore?: boolean;
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
    delivered: {
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
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    typing: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["TypingRequest"];
            };
        };
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    markRead: {
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
                "application/json": components["schemas"]["MarkReadRequest"];
            };
        };
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    page: {
        parameters: {
            query?: {
                beforeId?: number;
                limit?: number;
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
                    "application/json": components["schemas"]["MessagePageResponse"];
                };
            };
        };
    };
    send: {
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
                "application/json": components["schemas"]["SendMessageRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MessageDto"];
                };
            };
        };
    };
    addMember: {
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
                "application/json": components["schemas"]["AddMemberRequest"];
            };
        };
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    createGroup: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateGroupRequest"];
            };
        };
        responses: {
            /** @description Group created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ConversationDto"];
                };
            };
        };
    };
    createOrGetDm: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateDmRequest"];
            };
        };
        responses: {
            /** @description Existing DM returned. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ConversationDto"];
                };
            };
            /** @description New DM created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ConversationDto"];
                };
            };
        };
    };
    post: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SendMessageRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MessageDto"];
                };
            };
        };
    };
    uploadUrl: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ChatUploadUrlRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ChatUploadUrlResponse"];
                };
            };
            /** @description Size exceeds the per-attachment cap. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ChatUploadUrlResponse"];
                };
            };
            /** @description Content-type not allowed. */
            415: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ChatUploadUrlResponse"];
                };
            };
        };
    };
    deleteOwn: {
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
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    edit: {
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
                "application/json": components["schemas"]["EditMessageRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MessageDto"];
                };
            };
            /** @description Not the sender. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MessageDto"];
                };
            };
            /** @description Edit window expired or message deleted. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MessageDto"];
                };
            };
        };
    };
    search: {
        parameters: {
            query: {
                q: string;
                conversationId: number;
                limit?: number;
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
                    "application/json": components["schemas"]["SearchResultsResponse"];
                };
            };
            /** @description Caller is not a member of the conversation. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SearchResultsResponse"];
                };
            };
        };
    };
    get: {
        parameters: {
            query: {
                userIds: number[];
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
                    "application/json": components["schemas"]["PresenceDto"][];
                };
            };
        };
    };
    mine: {
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
                    "application/json": components["schemas"]["ConversationDto"][];
                };
            };
        };
    };
    get_1: {
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
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ConversationDto"];
                };
            };
            /** @description Not a member. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ConversationDto"];
                };
            };
            /** @description Conversation not found. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ConversationDto"];
                };
            };
        };
    };
    listForUser: {
        parameters: {
            query: {
                userId: number;
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
                    "application/json": components["schemas"]["ConversationDto"][];
                };
            };
        };
    };
    getForUser: {
        parameters: {
            query?: {
                asUserId?: number;
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
                    "application/json": components["schemas"]["ConversationDto"];
                };
            };
        };
    };
    pageMessages: {
        parameters: {
            query?: {
                beforeId?: number;
                limit?: number;
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
                    "application/json": components["schemas"]["MessagePageResponse"];
                };
            };
        };
    };
    deleteAsAdmin: {
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
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    removeMember: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                userId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
}

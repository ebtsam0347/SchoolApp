// Chat-service contract types. Mirrors `docs/services/chat.openapi.json`.
// Wire shape uses `bigint` ids; we model them as `number` like the rest of
// the app (max-safe int hasn't been a problem on any other service).
//
// Live UX signals arrive over the BFF SSE stream — see `stream-handlers.ts`.

export type ConversationType = 'DM' | 'GROUP' | 'BROADCAST';
export type MemberRole = 'MEMBER' | 'OWNER';
export type DeleteReason = 'SELF' | 'ADMIN';
export type PresenceStatus = 'online' | 'offline';

export type ConversationDto = {
  id: number;
  type: ConversationType;
  title: string | null;
  createdByUser: number;
  lastMessageId: number | null;
  lastMessageAt: string | null;
  memberUserIds: number[];
  unreadCount: number;
  /**
   * Per-member read cursor (`userId → lastReadMessageId`). The wire shape is
   * `Record<string, number>` because JSON object keys are strings; we
   * normalise to `Record<number, number>` in helpers.
   */
  lastReadByUser: Record<string, number>;
};

export type AttachmentDto = {
  id: number;
  messageId: number;
  objectKey: string;
  contentType: string;
  sizeBytes: number;
  originalName: string;
  /** Signed GET URL — present on read paths, absent on write echoes. */
  downloadUrl: string | null;
};

export type MessageDto = {
  id: number;
  conversationId: number;
  senderUserId: number;
  /** Sender's username at send-time. Persisted by the backend so old messages
   *  keep their author label even if the user is later renamed/disabled. */
  senderUsername: string | null;
  body: string;
  editedAt: string | null;
  deletedAt: string | null;
  deleteReason: DeleteReason | null;
  createdAt: string;
  attachments: AttachmentDto[];
  mentionedUserIds: number[];
};

export type MessagePageResponse = {
  items: MessageDto[];
  hasMore: boolean;
};

/** Embedded conversation header on `/chat/search` responses. */
export type SearchConversationHeader = {
  id: number;
  type: ConversationType;
  title: string | null;
};

export type SearchResultsResponse = {
  conversation: SearchConversationHeader;
  messages: MessageDto[];
};

/** BFF-resolved user identity for chat. PII-safe (no email/phone). */
export type UserIdentitySummary = {
  userId: number;
  username: string;
  fullName: string;
  profilePictureUrl: string | null;
  /** Employment status (`ACTIVE` / `TERMINATED` / etc.) — null for non-employee users. */
  status: string | null;
  /** Human-readable employee code (e.g. FS-2026-0042). Also the RFID/punch-card number used by attendance devices. */
  employeeCode?: string | null;
};

export type UsersByIdsResponse = {
  users: UserIdentitySummary[];
};

/** Single round-trip for the chat list on first paint. */
export type ChatInboxResponse = {
  conversations: ConversationDto[];
  /** `userId → identity` for every member of every conversation in the list. */
  resolved: Record<string, UserIdentitySummary>;
  presence: PresenceDto[];
};

export type PresenceDto = {
  userId: number;
  /** Server emits a free-form string (`online` / `offline`); we narrow at UI. */
  status: string;
  lastSeenAt: string | null;
};

/* ------------------------------ Requests ------------------------------- */

export type CreateDmRequest = {
  otherUserId: number;
};

export type CreateGroupRequest = {
  title: string;
  memberUserIds: number[];
};

export type AddMemberRequest = {
  userId: number;
};

export type AttachmentRef = {
  objectKey: string;
  contentType: string;
  sizeBytes: number;
  originalName: string;
};

export type SendMessageRequest = {
  body: string;
  mentionedUserIds?: number[];
  attachments?: AttachmentRef[];
};

export type EditMessageRequest = {
  body: string;
};

export type MarkReadRequest = {
  lastReadMessageId: number;
};

export type TypingRequest = {
  /** Default false — pass true to clear an active indicator. */
  stopped?: boolean;
};

export type ChatUploadUrlRequest = {
  contentType: string;
  sizeBytes: number;
  originalName: string;
};

export type ChatUploadUrlResponse = {
  bucket: string;
  objectKey: string;
  uploadUrl: string;
  expiresAt: string;
};

/* ------------------------- SSE envelope shapes ------------------------- */
//
// The BFF forwards Redis envelopes verbatim. The `type` field drives the SSE
// event name; payload shapes below are best-effort projections of what
// chat-service publishes (see chat.md §6 + ChatPublisher.java).

export type ChatStreamEnvelope =
  | ({ type: 'chat.message' } & ChatMessageEnvelope)
  | ({ type: 'chat.message.edited' } & ChatMessageEditedEnvelope)
  | ({ type: 'chat.message.deleted' } & ChatMessageDeletedEnvelope)
  | ({ type: 'chat.message.delivered' } & ChatMessageDeliveredEnvelope)
  | ({ type: 'chat.read' } & ChatReadEnvelope)
  | ({ type: 'chat.typing' } & ChatTypingEnvelope);

export type ChatMessageEnvelope = {
  conversationId: number;
  conversationType: ConversationType;
  messageId: number;
  senderUserId: number;
  preview?: string;
  createdAt: string;
};

export type ChatMessageEditedEnvelope = {
  conversationId: number;
  messageId: number;
  body: string;
  editedAt: string;
};

export type ChatMessageDeletedEnvelope = {
  conversationId: number;
  messageId: number;
  deleteReason: DeleteReason;
  deletedAt: string;
};

export type ChatMessageDeliveredEnvelope = {
  conversationId: number;
  messageId: number;
  recipientUserId: number;
  deliveredAt: string;
};

export type ChatReadEnvelope = {
  conversationId: number;
  userId: number;
  lastReadMessageId: number;
  at: string;
};

export type ChatTypingEnvelope = {
  conversationId: number;
  userId: number;
  stopped: boolean;
};

export type PresenceChangedEnvelope = {
  userId: number;
  status: PresenceStatus;
  at: string;
};

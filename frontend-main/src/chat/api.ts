// Typed wrappers for chat-service. Mirrors `docs/services/chat.openapi.json`.
// All requests go through the gateway via `src/lib/http.ts`
// (bearer + idempotency + 401-refresh-retry). Endpoints are split into
// per-controller namespaces matching the backend layout.

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';
import type {
  AddMemberRequest,
  ChatUploadUrlRequest,
  ChatUploadUrlResponse,
  ConversationDto,
  CreateDmRequest,
  CreateGroupRequest,
  EditMessageRequest,
  MarkReadRequest,
  MessageDto,
  MessagePageResponse,
  PresenceDto,
  SearchResultsResponse,
  SendMessageRequest,
  TypingRequest,
} from './types';

export type ListMessagesParams = {
  beforeId?: number;
  limit?: number;
};

export const conversationsApi = {
  list: (signal?: AbortSignal) =>
    http.get<ConversationDto[]>('/chat/conversations', { signal }),
  get: (id: number, signal?: AbortSignal) =>
    http.get<ConversationDto>(`/chat/conversations/${id}`, { signal }),
  createDm: (body: CreateDmRequest) =>
    http.post<ConversationDto>('/chat/conversations/dm', body),
  createGroup: (body: CreateGroupRequest) =>
    http.post<ConversationDto>('/chat/conversations/group', body),
  addMember: (id: number, body: AddMemberRequest) =>
    http.post<ConversationDto>(`/chat/conversations/${id}/members`, body),
  removeMember: (id: number, userId: number) =>
    http.del<ConversationDto>(`/chat/conversations/${id}/members/${userId}`),
  read: (id: number, body: MarkReadRequest) =>
    http.post<void>(`/chat/conversations/${id}/read`, body),
  typing: (id: number, body: TypingRequest = {}) =>
    http.post<void>(`/chat/conversations/${id}/typing`, body),
  messages: (id: number, params: ListMessagesParams = {}, signal?: AbortSignal) =>
    http.get<MessagePageResponse>(
      `/chat/conversations/${id}/messages${qs({
        beforeId: params.beforeId,
        limit: params.limit,
      })}`,
      { signal },
    ),
  send: (id: number, body: SendMessageRequest) =>
    http.post<MessageDto>(`/chat/conversations/${id}/messages`, body),
};

export const messagesApi = {
  edit: (id: number, body: EditMessageRequest) =>
    http.patch<MessageDto>(`/chat/messages/${id}`, body),
  remove: (id: number) =>
    http.del<void>(`/chat/messages/${id}`),
  /** Sender-side ack — issued by the BFF, exposed here for completeness. */
  markDelivered: (id: number) =>
    http.post<void>(`/chat/messages/${id}/delivery`),
  /** chat.messages.delete — hard delete, audit-emitting. */
  adminRemove: (id: number) =>
    http.del<void>(`/chat/messages/${id}/admin`),
};

export const attachmentsApi = {
  uploadUrl: (body: ChatUploadUrlRequest) =>
    http.post<ChatUploadUrlResponse>('/chat/attachments/upload-url', body),
};

export type SearchParams = {
  q: string;
  /** Required — chat-service scopes search to a single conversation. */
  conversationId: number;
  limit?: number;
};

export const searchApi = {
  search: (params: SearchParams, signal?: AbortSignal) =>
    http.get<SearchResultsResponse>(
      `/chat/search${qs({
        q: params.q,
        conversationId: params.conversationId,
        limit: params.limit,
      })}`,
      { signal },
    ),
};

export const presenceApi = {
  /** `userIds` is comma-joined per the gateway contract. */
  snapshot: (userIds: number[], signal?: AbortSignal) =>
    http.get<PresenceDto[]>(
      `/chat/presence${qs({ userIds: userIds.join(',') })}`,
      { signal },
    ),
};

export const broadcastApi = {
  send: (body: SendMessageRequest) =>
    http.post<MessageDto>('/chat/broadcast/messages', body),
};

export const adminChatApi = {
  list: (userId: number, signal?: AbortSignal) =>
    http.get<ConversationDto[]>(
      `/chat/admin/conversations${qs({ userId })}`,
      { signal },
    ),
  get: (id: number, asUserId?: number, signal?: AbortSignal) =>
    http.get<ConversationDto>(
      `/chat/admin/conversations/${id}${qs({ asUserId })}`,
      { signal },
    ),
  messages: (
    id: number,
    params: ListMessagesParams = {},
    signal?: AbortSignal,
  ) =>
    http.get<MessagePageResponse>(
      `/chat/admin/conversations/${id}/messages${qs({
        beforeId: params.beforeId,
        limit: params.limit,
      })}`,
      { signal },
    ),
};

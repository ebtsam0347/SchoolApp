// Chat SSE event handlers.
//
// The BFF multiplexes chat events onto the same `/bff/notifications/stream`
// connection that already powers the bell. Event names match the `type`
// field of the Redis envelope (chat.md §6 + NotificationStreamService.java):
//   chat.message            — durable; new message in any conversation
//   chat.message.edited     — body changed within the 15-min window
//   chat.message.deleted    — soft delete (sender) or admin hard delete
//   chat.message.delivered  — sender-side `✓✓` flip
//   chat.read               — read cursor moved
//   chat.typing             — transient typing indicator
//   presence.changed        — online/offline transition
//
// Strategy: invalidate React-Query keys for durable events (refetch is
// cheap and authoritative — the SSE payload is best-effort). Drive
// transient signals (typing, presence) through `presence-store` so the
// UI updates without a fetch.

import type { QueryClient } from '@tanstack/react-query';
import { chatKeys } from './query-keys';
import { useChatPresence } from './presence-store';
import type {
  ChatMessageDeletedEnvelope,
  ChatMessageEditedEnvelope,
  ChatMessageDeliveredEnvelope,
  ChatMessageEnvelope,
  ChatReadEnvelope,
  ChatTypingEnvelope,
  PresenceChangedEnvelope,
} from './types';

function safeParse<T>(raw: string): T | null {
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

export type StreamHandler = (qc: QueryClient, raw: string) => void;

export const chatStreamHandlers: Record<string, StreamHandler> = {
  'chat.message': (qc, raw) => {
    const env = safeParse<ChatMessageEnvelope>(raw);
    void qc.invalidateQueries({ queryKey: chatKeys.conversations });
    if (env?.conversationId != null) {
      void qc.invalidateQueries({
        queryKey: chatKeys.conversation(env.conversationId),
      });
      void qc.invalidateQueries({
        queryKey: chatKeys.messages(env.conversationId),
      });
      // Whoever sent it isn't typing anymore.
      if (env.senderUserId != null) {
        useChatPresence.getState().clearTyping(env.conversationId, env.senderUserId);
      }
    }
  },

  'chat.message.edited': (qc, raw) => {
    const env = safeParse<ChatMessageEditedEnvelope>(raw);
    if (env?.conversationId != null) {
      void qc.invalidateQueries({
        queryKey: chatKeys.messages(env.conversationId),
      });
    }
  },

  'chat.message.deleted': (qc, raw) => {
    const env = safeParse<ChatMessageDeletedEnvelope>(raw);
    if (env?.conversationId != null) {
      void qc.invalidateQueries({
        queryKey: chatKeys.conversation(env.conversationId),
      });
      void qc.invalidateQueries({
        queryKey: chatKeys.messages(env.conversationId),
      });
      void qc.invalidateQueries({ queryKey: chatKeys.conversations });
    }
  },

  'chat.message.delivered': (qc, raw) => {
    const env = safeParse<ChatMessageDeliveredEnvelope>(raw);
    if (env?.conversationId != null) {
      // Per-conversation header carries lastReadByUser; refresh it so the
      // sender's `✓✓` flip lands the moment the BFF acks delivery.
      void qc.invalidateQueries({
        queryKey: chatKeys.conversation(env.conversationId),
      });
      void qc.invalidateQueries({
        queryKey: chatKeys.messages(env.conversationId),
      });
    }
  },

  'chat.read': (qc, raw) => {
    const env = safeParse<ChatReadEnvelope>(raw);
    if (env?.conversationId != null) {
      // The conversation header (per-conv `get`) is what carries
      // `lastReadByUser` — must be invalidated alongside the inbox list,
      // otherwise the sender's bubble stays at single-tick until the
      // 30s staleTime expires (or they navigate away and back).
      void qc.invalidateQueries({
        queryKey: chatKeys.conversation(env.conversationId),
      });
      void qc.invalidateQueries({
        queryKey: chatKeys.messages(env.conversationId),
      });
      void qc.invalidateQueries({ queryKey: chatKeys.conversations });
    }
  },

  'chat.typing': (_qc, raw) => {
    const env = safeParse<ChatTypingEnvelope>(raw);
    if (env?.conversationId == null || env.userId == null) return;
    if (env.stopped) {
      useChatPresence.getState().clearTyping(env.conversationId, env.userId);
    } else {
      useChatPresence.getState().setTyping(env.conversationId, env.userId);
    }
  },

  'presence.changed': (_qc, raw) => {
    const env = safeParse<PresenceChangedEnvelope>(raw);
    if (env?.userId == null) return;
    useChatPresence
      .getState()
      .setOnline(env.userId, env.status, env.at ?? null);
  },
};

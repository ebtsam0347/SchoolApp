// Zustand store for transient chat signals — presence and typing indicators.
//
// Why a store (per CLAUDE.md tier rules):
//   - Both signals are server-driven but *not* server data — they arrive on
//     SSE, expire on a timer, and don't merit a TanStack Query cache.
//   - They need to be triggered from anywhere: SSE dispatcher writes,
//     composer's debounced `TypingPing` clears, presence query's onSuccess
//     seeds. Prop drilling can't reach all three.
//   - Co-located here because no caller outside `src/chat/**` reads them.

import { create } from 'zustand';
import type { PresenceStatus } from './types';

const TYPING_TIMEOUT_MS = 5000;

type PresenceEntry = {
  status: PresenceStatus;
  lastSeenAt: string | null;
};

interface ChatPresenceStore {
  /** userId → presence */
  presence: Map<number, PresenceEntry>;
  /** conversationId → set of userIds currently typing */
  typing: Map<number, Set<number>>;

  setOnline: (userId: number, status: PresenceStatus, lastSeenAt: string | null) => void;
  /** Bulk seed from `/chat/presence` snapshot. */
  seedPresence: (entries: { userId: number; status: PresenceStatus; lastSeenAt: string | null }[]) => void;

  setTyping: (conversationId: number, userId: number) => void;
  clearTyping: (conversationId: number, userId: number) => void;
}

const typingTimers = new Map<string, ReturnType<typeof setTimeout>>();

export const useChatPresence = create<ChatPresenceStore>((set, get) => ({
  presence: new Map(),
  typing: new Map(),

  setOnline: (userId, status, lastSeenAt) => {
    set((s) => {
      const next = new Map(s.presence);
      next.set(userId, { status, lastSeenAt });
      return { presence: next };
    });
  },

  seedPresence: (entries) => {
    set((s) => {
      const next = new Map(s.presence);
      for (const e of entries) {
        next.set(e.userId, {
          status: (e.status === 'online' ? 'online' : 'offline') as PresenceStatus,
          lastSeenAt: e.lastSeenAt,
        });
      }
      return { presence: next };
    });
  },

  setTyping: (conversationId, userId) => {
    set((s) => {
      const next = new Map(s.typing);
      const set_ = new Set(next.get(conversationId) ?? []);
      set_.add(userId);
      next.set(conversationId, set_);
      return { typing: next };
    });
    const key = `${conversationId}:${userId}`;
    const existing = typingTimers.get(key);
    if (existing) clearTimeout(existing);
    typingTimers.set(
      key,
      setTimeout(() => {
        get().clearTyping(conversationId, userId);
        typingTimers.delete(key);
      }, TYPING_TIMEOUT_MS),
    );
  },

  clearTyping: (conversationId, userId) => {
    set((s) => {
      const set_ = s.typing.get(conversationId);
      if (!set_ || !set_.has(userId)) return s;
      const nextSet = new Set(set_);
      nextSet.delete(userId);
      const next = new Map(s.typing);
      if (nextSet.size === 0) next.delete(conversationId);
      else next.set(conversationId, nextSet);
      return { typing: next };
    });
  },
}));

/** Hook: typing user-ids for a single conversation. */
export function useTypingFor(conversationId: number | undefined): number[] {
  // Subscribe to the underlying Set ref — Zustand returns a stable ref
  // between mutations so `Object.is` equality holds. The Compiler memoizes
  // the Array.from output keyed on `set`, so this stays stable too.
  const set = useChatPresence((s) =>
    conversationId == null ? undefined : s.typing.get(conversationId),
  );
  return set ? Array.from(set) : [];
}

/** Hook: presence entry for one userId. */
export function usePresenceFor(userId: number | undefined): PresenceEntry | undefined {
  return useChatPresence((s) => (userId == null ? undefined : s.presence.get(userId)));
}

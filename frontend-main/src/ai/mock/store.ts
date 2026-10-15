// Mock conversation store, persisted to localStorage. The shape mimics
// what the real BFF will expose so the chat UI stays unchanged when the
// mock is swapped out: list / get / append-message / rename / archive.
//
// Storage key bumps with the schema version (`fshrms.ai.mock.v1`) so a
// schema change can re-seed without clobbering unrelated keys.

import {
  seedConversations,
  otherUsersConversations,
  type Conversation,
  type Message,
} from './data';

const STORAGE_KEY = 'fshrms.ai.mock.v1';

interface PersistedShape {
  conversations: Conversation[];
}

function read(): PersistedShape {
  if (typeof localStorage === 'undefined') {
    return { conversations: [...seedConversations] };
  }
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      const shape: PersistedShape = { conversations: [...seedConversations] };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(shape));
      return shape;
    }
    const parsed = JSON.parse(raw) as PersistedShape;
    if (!Array.isArray(parsed.conversations)) {
      return { conversations: [...seedConversations] };
    }
    return parsed;
  } catch {
    return { conversations: [...seedConversations] };
  }
}

function write(shape: PersistedShape) {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(shape));
  } catch {
    // ignore quota / privacy errors — non-fatal in a mock.
  }
}

export const mockStore = {
  reset() {
    write({ conversations: [...seedConversations] });
  },

  listConversations(): Conversation[] {
    return read()
      .conversations.filter((c) => !c.archived)
      .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  },

  listAllConversations(): Conversation[] {
    // Includes archived + other users' (admin view).
    return [...read().conversations, ...otherUsersConversations].sort((a, b) =>
      b.updatedAt.localeCompare(a.updatedAt),
    );
  },

  getConversation(id: string): Conversation | undefined {
    return (
      read().conversations.find((c) => c.id === id) ??
      otherUsersConversations.find((c) => c.id === id)
    );
  },

  createConversation(title: string, userId: number, userName: string): Conversation {
    const shape = read();
    const conv: Conversation = {
      id: `conv-${Date.now().toString(36)}`,
      userId,
      userName,
      title,
      updatedAt: new Date().toISOString(),
      archived: false,
      messages: [],
    };
    shape.conversations.unshift(conv);
    write(shape);
    return conv;
  },

  appendMessage(conversationId: string, message: Message, titleHint?: string) {
    const shape = read();
    const conv = shape.conversations.find((c) => c.id === conversationId);
    if (!conv) return;
    conv.messages.push(message);
    conv.updatedAt = message.createdAt;
    if (titleHint && (conv.title === 'New conversation' || !conv.title)) {
      conv.title = titleHint.slice(0, 80);
    }
    write(shape);
  },

  /**
   * Replace a streaming-in-progress assistant message body. Called on every
   * delta so the persisted transcript matches what the user sees. Cheap on
   * a mock; the real path stamps the final body once at `done`.
   */
  patchMessage(conversationId: string, messageId: string, patch: Partial<Message>) {
    const shape = read();
    const conv = shape.conversations.find((c) => c.id === conversationId);
    if (!conv) return;
    const m = conv.messages.find((mm) => mm.id === messageId);
    if (!m) return;
    Object.assign(m, patch);
    if (patch.body !== undefined) conv.updatedAt = new Date().toISOString();
    write(shape);
  },

  rename(conversationId: string, title: string) {
    const shape = read();
    const conv = shape.conversations.find((c) => c.id === conversationId);
    if (!conv) return;
    conv.title = title;
    write(shape);
  },

  archive(conversationId: string) {
    const shape = read();
    const conv = shape.conversations.find((c) => c.id === conversationId);
    if (!conv) return;
    conv.archived = true;
    write(shape);
  },
};

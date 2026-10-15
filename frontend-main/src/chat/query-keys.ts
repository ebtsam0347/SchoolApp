// Single source of truth for TanStack Query keys. Keep them as `as const`
// arrays so invalidation matches by prefix.

export const chatKeys = {
  all: ['chat'] as const,
  conversations: ['chat', 'conversations'] as const,
  conversation: (id: number) => ['chat', 'conversation', id] as const,
  messages: (id: number) => ['chat', 'conversation', id, 'messages'] as const,
  presence: (userIds: number[]) => [
    'chat',
    'presence',
    [...userIds].sort((a, b) => a - b).join(','),
  ] as const,
  search: (q: string, conversationId?: number) =>
    ['chat', 'search', q, conversationId ?? 'all'] as const,
  adminConversations: (userId: number) => [
    'chat',
    'admin',
    'conversations',
    userId,
  ] as const,
  adminConversation: (id: number) => ['chat', 'admin', 'conversation', id] as const,
  adminMessages: (id: number) => [
    'chat',
    'admin',
    'conversation',
    id,
    'messages',
  ] as const,
};

// Single source of truth for the BFF overview query key. Every panel
// invalidates the same key on mutation so they stay in sync.

export function overviewKey(id: number) {
  return ['bff', 'admin', 'users', id, 'overview'] as const;
}

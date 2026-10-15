// Tiny permission shims so call-sites read like English. CASL subjects map
// to the auth-service grammar — `chat.broadcast.write` becomes
// `('write', 'chat.broadcast')`, etc. (see `src/auth/ability.ts`).

import type { AppAbility } from '@/auth';

export function canBroadcast(ability: AppAbility): boolean {
  return ability.can('write', 'chat.broadcast');
}

export function canAdminRead(ability: AppAbility): boolean {
  return ability.can('read', 'chat.admin');
}

export function canAdminDelete(ability: AppAbility): boolean {
  // `delete` is a custom verb — auth-service grants it explicitly via
  // `chat.messages.delete`. ROLE_ADMIN's `manage` over `'all'` also covers it.
  return ability.can('delete', 'chat.messages') || ability.can('manage', 'all');
}

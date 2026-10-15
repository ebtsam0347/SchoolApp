// Permission-driven CASL ability for the SPA.
//
// We model the auth-service permission grammar (`<service>.<resource>.<verb>`)
// directly: a permission code from /auth/me becomes a CASL `(action, subject)`
// pair where the action is the verb (`read` / `write` / `admin`) and the
// subject is the `<service>.<resource>` pair. The `manage` action is CASL's
// wildcard verb; `'all'` is the wildcard subject. We only use those two on a
// short whitelist (e.g. `audit.admin` grants `manage` over `audit`).
//
// Examples:
//   "auth.users.read"        → can('read', 'auth.users')
//   "auth.users.write"       → can('write', 'auth.users') and can('read', …)
//   "auth.sessions.admin"    → can('manage', 'auth.sessions')
//   "audit.read"             → can('read', 'audit')
//   "audit.admin"            → can('manage', 'audit')
//
// The `ADMIN` role is treated as a god-mode escape hatch — `manage` over
// `'all'`. auth-service doesn't enforce this; it's a UI convenience for
// admin dashboards where a single role check is more readable than
// enumerating every permission. Role codes come back bare (`ADMIN`, `HR`,
// `PAYROLL`, …) — no `ROLE_` prefix — matching src/auth/format.ts's
// ROLE_LABELS/ROLE_COLORS maps.

import { AbilityBuilder, createMongoAbility, type MongoAbility } from '@casl/ability';
import { createContextualCan } from '@casl/react';
import { createContext } from 'react';
import type { UserIdentity } from './types';

export type AppAbility = MongoAbility<[string, string]>;

export function buildAbility(user: UserIdentity | null): AppAbility {
  const { can, build } = new AbilityBuilder<AppAbility>(createMongoAbility);

  if (!user) return build();

  // ADMIN gets full access in the UI. Server still enforces per-permission.
  if (user.roles.includes('ADMIN')) {
    can('manage', 'all');
    return build();
  }

  for (const perm of user.permissions) {
    // Try the verb-as-suffix form first (e.g. "auth.users.read").
    const lastDot = perm.lastIndexOf('.');
    if (lastDot < 0) continue;
    const subject = perm.slice(0, lastDot);
    const verb = perm.slice(lastDot + 1);

    if (verb === 'admin') {
      can('manage', subject);
      continue;
    }
    if (verb === 'write') {
      can('write', subject);
      can('read', subject); // write implies read in the UI
      continue;
    }
    if (verb === 'read') {
      can('read', subject);
      continue;
    }
    // Unknown verb — register it as-is so callers can opt in by code.
    can(verb, subject);
  }

  return build();
}

export const AbilityContext = createContext<AppAbility>(buildAbility(null));
export const Can = createContextualCan(AbilityContext.Consumer);

/**
 * True when `held` contains any of the `required` role codes.
 *
 * Deliberately separate from the CASL ability: a handful of gates key off a
 * role rather than a permission (see `NavItem.requiresRole`), and those must
 * NOT inherit ADMIN's `manage:all` bypass. Comparison strips any `ROLE_`
 * prefix and ignores case, so `ADMIN` / `ROLE_ADMIN` / `role_admin` all
 * match — auth-service returns bare codes, but Spring-side tokens sometimes
 * carry the prefix.
 *
 * Single source of truth for the sidebar gate and the matching route
 * guards; the two used to drift apart when only the sidebar checked.
 */
export function hasAnyRole(
  required: readonly string[],
  held: readonly string[] | undefined | null,
): boolean {
  const norm = (r: string) => r.replace(/^ROLE_/i, '').toUpperCase();
  const set = new Set((held ?? []).map(norm));
  return required.some((r) => set.has(norm(r)));
}

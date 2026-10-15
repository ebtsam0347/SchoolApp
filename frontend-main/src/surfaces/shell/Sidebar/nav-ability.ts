// Pure helpers shared across the sidebar peer files. No React.
//
//   filterByAbility — strip nav items the caller can't read. Items without
//     a `subject` or `anyOf` always pass; parent items survive only if they
//     still have visible children OR they themselves have a `to` route that
//     the caller can reach. Items marked `requiresEmployee` additionally
//     drop when the caller's account isn't linked to an employee record.
//     Items marked `requiresDirectReports` drop when the caller has no
//     direct reports — unless the caller is a full (ROLE_ADMIN) admin, who
//     bypasses that gate. Items marked `requiresRole` drop unless the
//     caller holds one of the listed role codes — with no admin bypass,
//     unlike `requiresDirectReports`.
//
//   isItemActive  — true if a row matches the active key OR contains a
//     descendant that does (drives the chevron + parent highlight).

import { hasAnyRole, type AppAbility } from '@/auth';
import type { NavItem } from '../nav-config';

export interface NavGateContext {
  employeeLinked: boolean;
  hasDirectReports: boolean;
  /** Caller's role codes from /auth/me. Bare (`ADMIN`, `HR`), but compared
   *  with any `ROLE_` prefix stripped so either shape works. */
  roles?: readonly string[];
}

function canSeeItem(
  item: NavItem,
  ability: AppAbility,
  ctx: NavGateContext,
): boolean {
  if (item.requiresEmployee && !ctx.employeeLinked) return false;
  if (item.requiresDirectReports && !ctx.hasDirectReports && !ability.can('manage', 'all')) {
    return false;
  }
  // No admin bypass here by design — see NavItem.requiresRole. Shared with
  // the matching route guards via @/auth so a page and its sidebar row can't
  // disagree about who gets in.
  if (item.requiresRole && !hasAnyRole(item.requiresRole, ctx.roles)) {
    return false;
  }
  const verb = item.verb ?? 'read';
  if (item.subject && ability.can(verb, item.subject)) return true;
  if (item.anyOf?.some((c) => ability.can(c.verb ?? 'read', c.subject))) return true;
  return !item.subject && !item.anyOf;
}

export function filterByAbility(
  items: NavItem[],
  ability: AppAbility,
  ctx: NavGateContext,
): NavItem[] {
  return items
    .map((item) => {
      if (item.children) {
        const kids = filterByAbility(item.children, ability, ctx);
        if (kids.length > 0) return { ...item, children: kids };
        if (!item.to) return null;
        return canSeeItem(item, ability, ctx)
          ? { ...item, children: kids }
          : null;
      }
      return canSeeItem(item, ability, ctx) ? item : null;
    })
    .filter((x): x is NavItem => x !== null);
}

export function isItemActive(item: NavItem, activeKey: string): boolean {
  if (item.key === activeKey) return true;
  return !!item.children?.some((c) => isItemActive(c, activeKey));
}

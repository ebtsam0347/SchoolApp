// Builds the static (no-backend) entry list for the command palette.
//
//   buildPaletteEntries(ability) → quick actions + nav leaves the caller can
//   read. Recents are layered in by the palette itself; this module is pure
//   so it can be tested without React context.

import {
  ClipboardList,
  CornerDownRight,
  Plane,
  UserPlus,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import type { AppAbility } from '@/auth';
import { filterByAbility, type NavGateContext } from './Sidebar/nav-ability';
import { navGroups, type NavGroup, type NavItem } from './nav-config';

export type PaletteGroup = 'Recent' | 'Quick actions' | 'Navigate';

export interface PaletteEntry {
  key: string;
  label: string;
  hint?: string;
  icon: LucideIcon;
  group: PaletteGroup;
  to: string;
  /** Pre-filtered subject — kept for completeness; ability is checked upstream. */
  subject?: string;
  /** Override the verb checked against `subject`. Defaults to `read`. */
  verb?: string;
}

const QUICK_ACTIONS: PaletteEntry[] = [
  { key: 'qa-apply-leave', label: 'Apply for leave', icon: Plane, group: 'Quick actions', to: '/time/leaves' },
  { key: 'qa-add-employee', label: 'Add employee', icon: UserPlus, group: 'Quick actions', to: '/people/onboard', subject: 'employee' },
  { key: 'qa-run-payroll', label: 'Run payroll', icon: Wallet, group: 'Quick actions', to: '/payroll/runs', subject: 'payroll' },
  { key: 'qa-submit-timesheet', label: 'Submit timesheet', icon: ClipboardList, group: 'Quick actions', to: '/time/today' },
];

export function buildPaletteEntries(
  ability: AppAbility,
  ctx: NavGateContext,
): PaletteEntry[] {
  const quick = QUICK_ACTIONS.filter(
    (q) => !q.subject || ability.can(q.verb ?? 'read', q.subject),
  );
  const nav = navGroups.flatMap((group) =>
    flattenLeaves(filterByAbility(group.items, ability, ctx), group),
  );
  return [...quick, ...nav];
}

function flattenLeaves(items: NavItem[], group: NavGroup): PaletteEntry[] {
  const out: PaletteEntry[] = [];
  const visit = (item: NavItem, inheritedIcon?: LucideIcon) => {
    const icon = item.icon ?? inheritedIcon ?? CornerDownRight;
    if (item.to) {
      out.push({
        key: `nav-${item.key}`,
        label: item.label,
        icon,
        group: 'Navigate',
        hint: group.label,
        to: item.to,
        subject: item.subject,
        verb: item.verb,
      });
    }
    if (item.children) item.children.forEach((c) => visit(c, icon));
  };
  items.forEach((i) => visit(i));
  return out;
}

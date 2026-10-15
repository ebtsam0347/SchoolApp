// Maps a notification's `link` to a route this app actually has.
//
// notification-service renders `link` from each template's `link_tpl` when
// the notification is created, and the seeded templates (V0002) use
// API-shaped paths the FE never had — `/approvals/overtime/67`,
// `/leaves/42`, `/payslips/9`, `/team`, … — so clicking one landed on a
// "not found" page. The rendered link is stored per row, so fixing the
// templates alone wouldn't repair notifications already sent; translating
// here covers old and new rows alike.
//
//   /approvals/<type>/<id>  → /inbox?tab=<type>&open=<id>  (approver: open it)
//   /approvals/<type>       → /inbox?tab=<type>            (no drawer for it, e.g. travel advances)
//   /leaves/<id>            → /time/leaves                 (requester: own list)
//   /short-leaves/<id>      → /time/short-leave
//   /overtime/<id>          → /time/overtime
//   /regularizations/<id>   → /time/attendance
//   /payslips/<id>          → /payroll/payslips
//   /employees/<id>         → /people/<id>
//   /team                   → /time/team
//   /attendance             → /time/attendance
//   /chat/conversations/<id>→ /chat/<id>
// Anything else (already a real route, e.g. /me, /account/security, or an
// announcement's own link) passes through unchanged.

import type { InboxTab } from '@/attendance/inbox';

export type ResolvedLink = { to: string; search?: Record<string, string | number> };

const APPROVAL_TAB: Record<string, InboxTab> = {
  leaves: 'leaves',
  leave: 'leaves',
  'short-leaves': 'short-leave',
  'short-leave': 'short-leave',
  overtime: 'overtime',
  regularizations: 'regularization',
  regularization: 'regularization',
  // Generic approval-chain bells (notification-service ApprovalEventHandler).
  'long-term-advances': 'long-term-advances',
  'short-term-advances': 'short-term-advances',
  requisitions: 'requisitions',
  'tour-requests': 'tour-requests',
};

const OWN_LIST: Record<string, string> = {
  leaves: '/time/leaves',
  'short-leaves': '/time/short-leave',
  overtime: '/time/overtime',
  regularizations: '/time/attendance',
  payslips: '/payroll/payslips',
};

const STATIC: Record<string, string> = {
  '/team': '/time/team',
  '/attendance': '/time/attendance',
};

export function resolveNotificationLink(link: string): ResolvedLink {
  // Strip any origin / query / trailing slash — templates are bare paths.
  const path = link.replace(/^https?:\/\/[^/]+/i, '').split(/[?#]/)[0]!.replace(/\/+$/, '') || '/';
  const parts = path.split('/').filter(Boolean);

  if (parts[0] === 'approvals' && parts[1]) {
    const tab = APPROVAL_TAB[parts[1]];
    const id = Number(parts[2]);
    if (tab) {
      return {
        to: '/inbox',
        search: Number.isInteger(id) && id > 0 ? { tab, open: id } : { tab },
      };
    }
    return { to: '/inbox' };
  }
  if (parts[0] && parts.length <= 2 && OWN_LIST[parts[0]]) return { to: OWN_LIST[parts[0]]! };
  if (parts[0] === 'employees' && parts[1]) return { to: `/people/${parts[1]}` };
  if (parts[0] === 'chat' && parts[1] === 'conversations' && parts[2]) {
    return { to: `/chat/${parts[2]}` };
  }
  if (STATIC[path]) return { to: STATIC[path]! };
  return { to: link };
}

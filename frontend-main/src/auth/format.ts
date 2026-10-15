// Display helpers shared across the /admin/users + /admin/users/$id +
// /admin/roles screens. Keeping them in one place stops the role tag
// colors and the rel-time formatter from drifting between routes.

import type { SessionDto } from './api';
import { formatBrowser, formatOs } from '@/lib/user-agent';

/** AntD Tag color name per role code. Falls back to 'default'. */
const ROLE_COLORS: Record<string, string> = {
  ADMIN: 'purple',
  HR: 'blue',
  PAYROLL: 'gold',
  MANAGER: 'cyan',
  EMPLOYEE: 'default',
};

/** Pretty label per role code. Falls back to a title-cased version of the code. */
const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Admin',
  HR: 'HR',
  PAYROLL: 'Payroll',
  MANAGER: 'Manager',
  EMPLOYEE: 'Employee',
};

export function roleTagColor(code: string): string {
  return ROLE_COLORS[code.toUpperCase()] ?? 'default';
}

export function prettyRoleCode(code: string): string {
  const upper = code.toUpperCase();
  if (ROLE_LABELS[upper]) return ROLE_LABELS[upper];
  return upper
    .split('_')
    .map((part) => (part.length <= 3 ? part : part[0] + part.slice(1).toLowerCase()))
    .join(' ');
}

/** Maps a login_history.action code to a human-readable verb. */
export function prettyLoginAction(a: string): string {
  switch (a) {
    case 'login.success':
      return 'Signed in';
    case 'login.failed':
      return 'Failed sign-in';
    case 'user.locked':
      return 'Account locked';
    case 'user.unlocked':
      return 'Account unlocked';
    default:
      return a;
  }
}

/** Maps a login_history.reason code to a human-readable phrase. */
export function prettyLoginReason(r: string): string {
  switch (r.toUpperCase()) {
    case 'BAD_PASSWORD':
      return 'Wrong password';
    case 'BAD_USERNAME':
      return 'Unknown username';
    case 'ACCOUNT_LOCKED':
      return 'Account locked';
    case 'ACCOUNT_DISABLED':
      return 'Account disabled';
    case 'PASSWORD_EXPIRED':
      return 'Password expired';
    case 'TOO_MANY_ATTEMPTS':
      return 'Too many attempts';
    default:
      // Fallback: turn SCREAMING_SNAKE into Sentence case.
      return r
        .toLowerCase()
        .split(/[_\s]+/)
        .map((p, i) => (i === 0 ? p[0]?.toUpperCase() + p.slice(1) : p))
        .join(' ');
  }
}

/** "5m ago", "2h ago", "3d ago", or a localized date. */
export function relTime(iso: string): string {
  const ms = Date.now() - new Date(iso).getTime();
  if (Number.isNaN(ms)) return '—';
  const sec = Math.round(ms / 1000);
  if (sec < 60) return 'just now';
  const min = Math.round(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.round(min / 60);
  if (hr < 24) return `${hr}h ago`;
  const day = Math.round(hr / 24);
  if (day < 30) return `${day}d ago`;
  return new Date(iso).toLocaleDateString();
}

/** "JK", "FS" — first letters of first + last name. */
export function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '·';
  if (parts.length === 1) return parts[0]!.slice(0, 2).toUpperCase();
  return (parts[0]![0]! + parts[parts.length - 1]![0]!).toUpperCase();
}

export function isSessionExpired(s: SessionDto): boolean {
  return new Date(s.expiresAt).getTime() < Date.now();
}

/** Best-effort device label for messages and toasts. */
export function describeDevice(s: SessionDto): string {
  if (s.deviceLabel) return s.deviceLabel;
  const browser = formatBrowser(s.browser);
  const os = formatOs(s.os);
  if (browser && os) return `${browser} on ${os}`;
  return browser ?? os ?? 'unknown device';
}

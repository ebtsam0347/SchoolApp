// Presentation metadata for notification types and categories.
//
// Notification types are open-ended on the server (driven by the
// `notification_templates` table), but every type the backend currently
// emits is enumerated in `docs/services/notification.md` §5. We map each
// to an icon + tone for the bell feed; unknown types fall back to a
// neutral bell glyph.

import {
  Banknote,
  Bell,
  Briefcase,
  CalendarClock,
  CircleAlert,
  ClipboardList,
  Coins,
  KeyRound,
  MapPin,
  Megaphone,
  Plane,
  ShieldAlert,
  ShieldCheck,
  ShieldX,
  Timer,
  UserCheck,
  UserPlus,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import type { NotificationCategory, NotificationPriority } from './types';

export type NotificationTone = 'info' | 'success' | 'warning' | 'error' | 'neutral';

type Meta = {
  icon: LucideIcon;
  tone: NotificationTone;
};

const TYPE_META: Record<string, Meta> = {
  // Leave
  LEAVE_APPLIED: { icon: Plane, tone: 'info' },
  LEAVE_STAGE_APPROVED: { icon: Plane, tone: 'info' },
  LEAVE_STAGE_REJECTED: { icon: Plane, tone: 'warning' },
  LEAVE_APPROVED: { icon: Plane, tone: 'success' },
  LEAVE_PARTIALLY_APPROVED: { icon: Plane, tone: 'warning' },
  LEAVE_REJECTED: { icon: Plane, tone: 'error' },
  LEAVE_CANCELLED: { icon: Plane, tone: 'neutral' },
  // Overtime
  OT_REQUESTED: { icon: ClipboardList, tone: 'info' },
  OT_STAGE_APPROVED: { icon: ClipboardList, tone: 'info' },
  OT_STAGE_REJECTED: { icon: ClipboardList, tone: 'warning' },
  OT_APPROVED: { icon: ClipboardList, tone: 'success' },
  OT_REJECTED: { icon: ClipboardList, tone: 'error' },
  // Regularization
  REGULARIZATION_REQUESTED: { icon: CalendarClock, tone: 'info' },
  REGULARIZATION_STAGE_APPROVED: { icon: CalendarClock, tone: 'info' },
  REGULARIZATION_STAGE_REJECTED: { icon: CalendarClock, tone: 'warning' },
  REGULARIZATION_APPROVED: { icon: CalendarClock, tone: 'success' },
  REGULARIZATION_REJECTED: { icon: CalendarClock, tone: 'error' },
  // Payroll
  // Generic approval chains — short leave, advances, requisitions, travel.
  SHORT_LEAVE_REQUESTED: { icon: Timer, tone: 'info' },
  SHORT_LEAVE_STAGE_APPROVED: { icon: Timer, tone: 'info' },
  SHORT_LEAVE_APPROVED: { icon: Timer, tone: 'success' },
  SHORT_LEAVE_REJECTED: { icon: Timer, tone: 'error' },
  ADVANCE_REQUESTED: { icon: Banknote, tone: 'info' },
  ADVANCE_STAGE_APPROVED: { icon: Banknote, tone: 'info' },
  ADVANCE_APPROVED: { icon: Banknote, tone: 'success' },
  ADVANCE_REJECTED: { icon: Banknote, tone: 'error' },
  SHORT_TERM_ADVANCE_REQUESTED: { icon: Coins, tone: 'info' },
  SHORT_TERM_ADVANCE_STAGE_APPROVED: { icon: Coins, tone: 'info' },
  SHORT_TERM_ADVANCE_APPROVED: { icon: Coins, tone: 'success' },
  SHORT_TERM_ADVANCE_REJECTED: { icon: Coins, tone: 'error' },
  REQUISITION_REQUESTED: { icon: UserPlus, tone: 'info' },
  REQUISITION_STAGE_APPROVED: { icon: UserPlus, tone: 'info' },
  REQUISITION_APPROVED: { icon: UserPlus, tone: 'success' },
  REQUISITION_REJECTED: { icon: UserPlus, tone: 'error' },
  TRAVEL_REQUESTED: { icon: MapPin, tone: 'info' },
  TRAVEL_STAGE_APPROVED: { icon: MapPin, tone: 'info' },
  TRAVEL_APPROVED: { icon: MapPin, tone: 'success' },
  TRAVEL_REJECTED: { icon: MapPin, tone: 'error' },
  PAYSLIP_READY: { icon: Wallet, tone: 'success' },
  // Auth
  PASSWORD_RESET_BY_ADMIN: { icon: KeyRound, tone: 'warning' },
  SESSION_REVOKED_BY_ADMIN: { icon: ShieldX, tone: 'warning' },
  // Employee lifecycle
  MANAGER_CHANGED: { icon: UserCheck, tone: 'info' },
  EMPLOYEE_SUSPENDED: { icon: ShieldAlert, tone: 'error' },
  EMPLOYEE_SUSPENSION_LIFTED: { icon: ShieldCheck, tone: 'success' },
  // Attendance
  SHIFT_OVERRIDDEN: { icon: CalendarClock, tone: 'info' },
};

const CATEGORY_FALLBACK: Record<NotificationCategory, Meta> = {
  APPROVAL: { icon: ClipboardList, tone: 'info' },
  ANNOUNCEMENT: { icon: Megaphone, tone: 'info' },
  PAYROLL: { icon: Wallet, tone: 'success' },
  SYSTEM: { icon: Briefcase, tone: 'neutral' },
};

export function notificationMeta(type: string, category: NotificationCategory | null): Meta {
  return (
    TYPE_META[type] ??
    (category ? CATEGORY_FALLBACK[category] : null) ??
    { icon: Bell, tone: 'neutral' }
  );
}

export function priorityIcon(priority: NotificationPriority): LucideIcon | null {
  return priority === 'HIGH' ? CircleAlert : null;
}

// Human-readable label for a notification type (e.g. for the preferences
// screen). Server doesn't ship a label so we generate from the code.
export function humanizeType(type: string): string {
  return type
    .toLowerCase()
    .split('_')
    .map((p) => (p.length === 0 ? p : p[0].toUpperCase() + p.slice(1)))
    .join(' ');
}

export const ALL_CATEGORIES: NotificationCategory[] = [
  'APPROVAL',
  'ANNOUNCEMENT',
  'PAYROLL',
  'SYSTEM',
];

export function categoryLabel(c: NotificationCategory): string {
  switch (c) {
    case 'APPROVAL':
      return 'Approvals';
    case 'ANNOUNCEMENT':
      return 'Announcements';
    case 'PAYROLL':
      return 'Payroll';
    case 'SYSTEM':
      return 'System';
  }
}

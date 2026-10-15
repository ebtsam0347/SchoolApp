// Display helpers shared across the /time/* + /inbox screens. Keep these in
// the module so individual routes don't reimplement minute-formatting / tone
// resolution.
//
// Date helpers (formatDateShort, formatDateLong, formatDateMonthDay,
// formatDateTime, parseDateOnly, toIsoDate, toIsoMonth) live in @/lib/dates
// — import from there directly.

import dayjs from 'dayjs';

import type {
  ApprovalStepStatus,
  AttendanceStatus,
  DelegationStatus,
  HolidayType,
  LeaveCycleAnchor,
  LeaveDayStatus,
  LeaveStatus,
  OvertimeStatus,
  OvertimeType,
  FactoryCode,
  PunchSource,
  RegularizationStatus,
  ShortLeaveStatus,
} from './types';

import type { StatusPillTone } from '@/components';
import {
  formatDateLong,
  formatDateMonthDay,
  formatDateShort,
  formatDateTime,
  parseDateOnly,
  toIsoDate,
  toIsoMonth,
} from '@/lib/dates';

// Re-export canonical date helpers so attendance/people callers can keep
// importing them from `@/attendance`.
export {
  formatDateLong,
  formatDateMonthDay,
  formatDateShort,
  formatDateTime,
  parseDateOnly,
  toIsoDate,
  toIsoMonth,
};

/** "8h 14m", "45m", "0m". Negative values clamp to 0. */
export function formatMinutes(mins: number | null | undefined): string {
  if (mins == null || Number.isNaN(mins) || mins <= 0) return '0m';
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  if (h === 0) return `${m}m`;
  if (m === 0) return `${h}h`;
  return `${h}h ${m}m`;
}

/** "08:42", "—". Reads a date-time iso. Bare `LocalDateTime` strings render
 * the literal `HH:mm` verbatim — no timezone conversion, since the server's
 * wall clock is the source of truth. */
export function formatClock(iso: string | null | undefined): string {
  if (!iso) return '—';
  const bare = /^\d{4}-\d{2}-\d{2}[T ](\d{2}):(\d{2})/.exec(iso);
  if (bare) return `${bare[1]}:${bare[2]}`;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleTimeString('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

/**
 * {@link formatClock}, plus the calendar date when the instant falls on a
 * different day than the record's work date — an OT check-out past midnight
 * or a night shift's check-out reads "02:00 (10 Oct)", not a bare "02:00".
 */
export function formatClockOn(
  iso: string | null | undefined,
  workDate: string | null | undefined,
): string {
  const clock = formatClock(iso);
  if (!iso || !workDate || clock === '—') return clock;
  const day = iso.slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day) || day === workDate.slice(0, 10)) return clock;
  return `${clock} (${dayjs(day).format('D MMM')})`;
}

/** Mirrors the backend's PunchService.PRE_SHIFT_WINDOW. */
export const PRE_SHIFT_WINDOW_MINUTES = 120;

/**
 * The punch instant (`YYYY-MM-DDTHH:mm:00`) that a clock time typed against a
 * record lands on, by the backend's work-date rule: a work date opens 2h
 * before its shift starts and runs until the next one opens. So for a
 * 07:45 shift, 02:00 is the next morning (OT check-out); for a 20:00–05:00
 * night shift, 05:00 is the next morning; for a 00:30 shift, 23:00 is the
 * evening before. Without a shift the time stays on the work date. Assumes
 * the following day has the same shift.
 */
export function punchInstantFor(
  workDate: string,
  hhmm: string,
  shiftStartTime: string | null | undefined,
): string {
  const onWorkDate = dayjs(`${workDate}T${hhmm.slice(0, 5)}`);
  const fmt = (d: dayjs.Dayjs) => d.format('YYYY-MM-DDTHH:mm:00');
  if (!shiftStartTime) return fmt(onWorkDate);
  const opens = dayjs(`${workDate}T${shiftStartTime.slice(0, 5)}`).subtract(
    PRE_SHIFT_WINDOW_MINUTES,
    'minute',
  );
  const closes = opens.add(1, 'day');
  const hit = [onWorkDate.subtract(1, 'day'), onWorkDate, onWorkDate.add(1, 'day')].find(
    (c) => !c.isBefore(opens) && c.isBefore(closes),
  );
  return fmt(hit ?? onWorkDate);
}

/** Short label for a leave type's entitlement cycle anchor. */
export const LEAVE_CYCLE_ANCHOR_LABEL: Record<LeaveCycleAnchor, string> = {
  JOINING: 'Joining anniversary',
  FISCAL: 'Fiscal year',
  NONE: 'Occasional',
};

/** "ANNUAL_LEAVE" → "Annual leave". Used wherever leave-type codes surface. */
export function prettyLeaveCode(code: string): string {
  return code
    .split('_')
    .map((s) => s[0] + s.slice(1).toLowerCase())
    .join(' ');
}

/** YYYY-MM-DD strings, inclusive, between `from` and `to`. Used for per-day decision UIs. */
export function generateDateRange(from: string, to: string): string[] {
  const out: string[] = [];
  const start = parseDateOnly(from);
  const end = parseDateOnly(to);
  if (!start || !end) return out;
  const cursor = new Date(start);
  while (cursor.getTime() <= end.getTime()) {
    out.push(toIsoDate(cursor));
    cursor.setDate(cursor.getDate() + 1);
  }
  return out;
}

/* ---------------------- status → tone + label ------------------------- */

export const ATTENDANCE_STATUS_LABEL: Record<AttendanceStatus, string> = {
  PRESENT: 'Present',
  ABSENT: 'Absent',
  LEAVE: 'On leave',
  HOLIDAY: 'Holiday',
  WEEKEND: 'Weekend',
  HALF_DAY: 'Half day',
};

export const ATTENDANCE_STATUS_TONE: Record<AttendanceStatus, StatusPillTone> = {
  PRESENT: 'success',
  ABSENT: 'error',
  LEAVE: 'info',
  HOLIDAY: 'primary',
  WEEKEND: 'neutral',
  HALF_DAY: 'warning',
};

export const LEAVE_STATUS_LABEL: Record<LeaveStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  PARTIALLY_APPROVED: 'Partially approved',
  CANCELLED: 'Cancelled',
};

export const LEAVE_STATUS_TONE: Record<LeaveStatus, StatusPillTone> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
  PARTIALLY_APPROVED: 'info',
  CANCELLED: 'neutral',
};

export const LEAVE_DAY_STATUS_LABEL: Record<LeaveDayStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
};

export const LEAVE_DAY_STATUS_TONE: Record<LeaveDayStatus, StatusPillTone> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
};

export const SHORT_LEAVE_STATUS_LABEL: Record<ShortLeaveStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

export const SHORT_LEAVE_STATUS_TONE: Record<ShortLeaveStatus, StatusPillTone> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
  CANCELLED: 'neutral',
};

export const OVERTIME_STATUS_LABEL: Record<OvertimeStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

export const OVERTIME_STATUS_TONE: Record<OvertimeStatus, StatusPillTone> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
  CANCELLED: 'neutral',
};

export const OVERTIME_TYPE_LABEL: Record<OvertimeType, string> = {
  NORMAL: 'Normal',
  EXTRA: 'Extra',
  SPECIAL: 'Special',
};

/** Factory / unit picker options, in display order. */
export const FACTORY_CODES: readonly FactoryCode[] = [
  'B34002',
  'B34003',
  'B34004',
  'B34005',
  'B34006',
  'B34007',
  'Administrator',
];

export const OVERTIME_TYPE_DESCRIPTION: Record<OvertimeType, string> = {
  NORMAL: 'First 120 min beyond shift end on a working day.',
  EXTRA: 'Minutes past the first 120 min beyond shift end.',
  SPECIAL: 'Work on a Sunday or holiday only.',
};

/** Tag colors — kept in sync with `OtMinuteCell` so the inbox / table / cell read the same. */
export const OVERTIME_TYPE_COLOR: Record<OvertimeType, string> = {
  NORMAL: 'blue',
  EXTRA: 'orange',
  SPECIAL: 'purple',
};

export const REGULARIZATION_STATUS_LABEL: Record<RegularizationStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

export const REGULARIZATION_STATUS_TONE: Record<RegularizationStatus, StatusPillTone> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
  CANCELLED: 'neutral',
};

export const APPROVAL_STEP_STATUS_LABEL: Record<ApprovalStepStatus, string> = {
  NOT_YET: 'Awaiting',
  PENDING: 'In review',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SKIPPED: 'Skipped',
};

export const APPROVAL_STEP_STATUS_TONE: Record<ApprovalStepStatus, StatusPillTone> = {
  NOT_YET: 'neutral',
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
  SKIPPED: 'neutral',
};

export const PUNCH_SOURCE_LABEL: Record<PunchSource, string> = {
  BIOMETRIC: 'Biometric',
  WEB: 'Web portal',
  OPERATOR: 'Operator',
  REGULARIZED: 'Regularized',
  SYSTEM: 'System',
};

export const HOLIDAY_TYPE_LABEL: Record<HolidayType, string> = {
  NATIONAL: 'National',
  COMPANY: 'Company',
  PROVINCIAL: 'Provincial',
};

export const HOLIDAY_TYPE_TONE: Record<HolidayType, StatusPillTone> = {
  NATIONAL: 'error',
  COMPANY: 'primary',
  PROVINCIAL: 'info',
};

export const DELEGATION_STATUS_LABEL: Record<DelegationStatus, string> = {
  ACTIVE: 'Active',
  REVOKED: 'Revoked',
};

export const DELEGATION_STATUS_TONE: Record<DelegationStatus, StatusPillTone> = {
  ACTIVE: 'success',
  REVOKED: 'neutral',
};

/* ------------------------- shift helpers ------------------------------ */

/**
 * Working minutes between two HH:mm[:ss] times, handling midnight crossings
 * (end <= start ⇒ next-day end). Used to render "Shift: 09:00 – 18:00 (8h)".
 */
export function shiftDurationMinutes(
  start: string | null,
  end: string | null,
  breakMins: number | null,
): number {
  if (!start || !end) return 0;
  const s = parseClockToMinutes(start);
  let e = parseClockToMinutes(end);
  if (s == null || e == null) return 0;
  if (e <= s) e += 24 * 60;
  return Math.max(0, e - s - (breakMins ?? 0));
}

function parseClockToMinutes(t: string): number | null {
  const m = /^(\d{1,2}):(\d{2})/.exec(t);
  if (!m) return null;
  return Number(m[1]) * 60 + Number(m[2]);
}

/** "09:00 – 18:00". Strips seconds if present. */
export function formatShiftRange(start: string | null, end: string | null): string {
  if (!start || !end) return '—';
  return `${trimSeconds(start)} – ${trimSeconds(end)}`;
}

function trimSeconds(t: string): string {
  const m = /^(\d{1,2}:\d{2})/.exec(t);
  return m ? m[1] : t;
}

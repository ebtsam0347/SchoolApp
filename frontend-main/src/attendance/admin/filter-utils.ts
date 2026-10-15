// Helpers shared by the records / punches browser routes.
//
// Search-param parsing + the date-range collapse rule that maps a UI
// RangePicker value into the upstream `date` (single-day shortcut) vs
// `from/to` pair the BFF expects.

import type { AttendanceStatus, PunchSource } from '../types';

export function numOrUndef(v: unknown): number | undefined {
  if (v === undefined || v === null || v === '') return undefined;
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? n : undefined;
}

export function strOrUndef(v: unknown): string | undefined {
  return typeof v === 'string' && v.length > 0 ? v : undefined;
}

export function boolOrUndef(v: unknown): boolean | undefined {
  if (typeof v === 'boolean') return v;
  if (v === 'true') return true;
  if (v === 'false') return false;
  return undefined;
}

const ATTENDANCE_STATUSES: readonly AttendanceStatus[] = [
  'PRESENT',
  'ABSENT',
  'LEAVE',
  'HOLIDAY',
  'WEEKEND',
  'HALF_DAY',
];

export function isAttendanceStatus(v: unknown): v is AttendanceStatus {
  return typeof v === 'string' && (ATTENDANCE_STATUSES as readonly string[]).includes(v);
}

const PUNCH_SOURCES: readonly PunchSource[] = [
  'BIOMETRIC',
  'WEB',
  'OPERATOR',
  'REGULARIZED',
  'SYSTEM',
];

export function isPunchSource(v: unknown): v is PunchSource {
  return typeof v === 'string' && (PUNCH_SOURCES as readonly string[]).includes(v);
}

/**
 * Collapse a UI date range into the upstream filter shape: single-day
 * ranges go through `date` (the BFF shortcut), multi-day ranges go through
 * `from/to`. Returns `{}` when neither end is set — server will default to
 * today.
 */
export function collapseDateRange(
  from: string | undefined,
  to: string | undefined,
): { date?: string; from?: string; to?: string } {
  if (!from && !to) return {};
  if (from && !to) return { from };
  if (!from && to) return { to };
  if (from === to) return { date: from };
  return { from, to };
}

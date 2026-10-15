// Shared helpers for the live sections added to the operator dashboard
// (distribution ring, department-wise breakdown, live punch feed, activity
// timeline). Pure/derivation logic only — no React, no I/O — so the same
// aggregation can be unit-tested and reused if a server-side aggregate lands
// later (see `dashboard-live.md` / the SSE contract note in the dashboard route).

import type { ChartTone } from '@/components/charts';
import type { StatusPillTone } from '@/components/StatusPill';
import type { OrgPulseView, AttendanceRecordsRow } from '../bff-api';
import type { AttendanceStatus } from '../types';

/**
 * Poll cadence for the department-wise breakdown card on the (non-live) operator
 * dashboard. When the backend starts publishing `attendance.*` events onto
 * `/bff/notifications/stream` (see `attendanceStreamHandlers`), this interval
 * becomes a safety net rather than the primary update path.
 */
export const DEPARTMENT_REFETCH_MS = 45_000;

/**
 * Shared poll cadence for the `/attendance/live` wall-board — the summary
 * (present/absent/on leave), the recent-activity feed, and the department
 * counts all use this same constant so the three panels refresh in lockstep
 * instead of drifting apart on independent timers.
 */
export const RECENT_ACTIVITY_REFETCH_MS = 3_000;

/** How long a freshly-arrived feed row keeps its highlight, in ms. */
export const NEW_ROW_HIGHLIGHT_MS = 2_400;

/**
 * Max rows we pull to build the client-side department breakdown. The
 * `records/with-employee` endpoint caps page size at 500. Larger orgs need a
 * server-side aggregate (documented contract:
 * `GET /attendance/operator/department-breakdown?date=`); until then we render
 * the first page and flag when the sample is partial.
 */
export const DEPARTMENT_SAMPLE_SIZE = 500;

/* ------------------------------ Distribution -------------------------------- */

export type DistributionSlice = {
  key: 'present' | 'absent' | 'leave' | 'halfDay' | 'unmarked';
  label: string;
  value: number;
  tone: ChartTone;
};

/**
 * Turn the org pulse into donut segments. `unmarked` is the balancing figure —
 * employees with no attendance record resolved yet for the day (total minus
 * every accounted-for bucket). Holiday/weekend are intentionally excluded from
 * the ring (they're "not expected in" rather than an attendance outcome) and
 * surfaced as a caption instead.
 */
export function deriveDistribution(pulse: OrgPulseView | undefined): {
  slices: DistributionSlice[];
  total: number;
  offDuty: number;
} {
  const present = pulse?.present ?? 0;
  const absent = pulse?.absent ?? 0;
  const leave = pulse?.onLeave ?? 0;
  const halfDay = pulse?.halfDay ?? 0;
  const holiday = pulse?.holiday ?? 0;
  const weekend = pulse?.weekend ?? 0;
  const total = pulse?.total ?? 0;
  const offDuty = holiday + weekend;
  const unmarked = Math.max(0, total - present - absent - leave - halfDay - offDuty);

  const slices: DistributionSlice[] = [
    { key: 'present', label: 'Present', value: present, tone: 'success' },
    { key: 'absent', label: 'Absent', value: absent, tone: 'error' },
    { key: 'leave', label: 'On leave', value: leave, tone: 'info' },
    { key: 'halfDay', label: 'Half day', value: halfDay, tone: 'warning' },
    { key: 'unmarked', label: 'Unmarked', value: unmarked, tone: 'neutral' },
  ];
  return { slices, total, offDuty };
}

/* --------------------------- Department breakdown --------------------------- */

export type DepartmentAttendanceRow = {
  departmentId: number | null;
  departmentName: string;
  present: number;
  absent: number;
  leave: number;
  halfDay: number;
  /** present + absent + leave + halfDay + holiday + weekend for the dept. */
  total: number;
};

/** The three metrics the breakdown can rank departments by. */
export type DepartmentMetric = 'present' | 'absent' | 'leave';

export const DEPARTMENT_METRIC_TONE: Record<DepartmentMetric, ChartTone> = {
  present: 'success',
  absent: 'error',
  leave: 'info',
};

const UNASSIGNED_KEY = -1;

/**
 * Fold `records/with-employee` rows into per-department counts. Rows whose
 * employee summary is missing a department land under an "Unassigned" bucket
 * so the totals still reconcile. O(n) single pass.
 */
export function aggregateDepartmentBreakdown(
  rows: AttendanceRecordsRow[],
): DepartmentAttendanceRow[] {
  const byDept = new Map<number, DepartmentAttendanceRow>();

  for (const row of rows) {
    const deptId = row.employee?.departmentId ?? UNASSIGNED_KEY;
    const deptName = row.employee?.departmentName ?? 'Unassigned';
    let bucket = byDept.get(deptId);
    if (!bucket) {
      bucket = {
        departmentId: deptId === UNASSIGNED_KEY ? null : deptId,
        departmentName: deptName,
        present: 0,
        absent: 0,
        leave: 0,
        halfDay: 0,
        total: 0,
      };
      byDept.set(deptId, bucket);
    }
    bucket.total += 1;
    bumpStatus(bucket, row.record.status);
  }

  return [...byDept.values()];
}

function bumpStatus(bucket: DepartmentAttendanceRow, status: AttendanceStatus): void {
  switch (status) {
    case 'PRESENT':
      bucket.present += 1;
      break;
    case 'ABSENT':
      bucket.absent += 1;
      break;
    case 'LEAVE':
      bucket.leave += 1;
      break;
    case 'HALF_DAY':
      bucket.halfDay += 1;
      // A half day is a partial attendance — count it toward present too so the
      // "present" bar doesn't understate who actually showed up.
      bucket.present += 1;
      break;
    // HOLIDAY / WEEKEND contribute to `total` only.
    default:
      break;
  }
}

/* ------------------------------- Punch feed --------------------------------- */

/** Tone for the punch-source tag in the live feed. */
export const PUNCH_SOURCE_TONE: Record<string, StatusPillTone> = {
  BIOMETRIC: 'success',
  WEB: 'info',
  OPERATOR: 'warning',
  REGULARIZED: 'neutral',
  SYSTEM: 'neutral',
};

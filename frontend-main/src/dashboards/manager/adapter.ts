/**
 * Adapter: BFF `ManagerDashboard` + `TeamAttendanceToday` → view-model
 * `ManagerDashboardDto`.
 *
 * The BFF composite stopped carrying a `team.today` block; per-person
 * status now lives on `/bff/attendance/team/today`. We fan out to both
 * endpoints and derive the today rollup (counts + people) here.
 */
import type {
  BffManagerApprovalRow,
  BffManagerDashboard,
  BffManagerLeaveItem,
  BffTeamAttendanceToday,
  BffTeamAttendanceTodayRow,
} from '../bff-types';
import type {
  ApprovalKind,
  ApprovalRow,
  LeaveCalendarItemDto,
  ManagerDashboardDto,
  ManagerTodayRollup,
  TeamMemberRow,
  TeamMemberStatus,
} from '../types';
import type { ChartTone } from '@/components';

export function toManagerDashboardDto(
  src: BffManagerDashboard,
  today: BffTeamAttendanceToday | null,
): ManagerDashboardDto {
  return {
    team: {
      size: src.team.size,
      directReports: src.team.directReports,
      today: toTodayRollup(today),
    },
    attendance14d: src.attendance14d,
    approvalQueue: {
      totalPending: src.approvalQueue.totalPending,
      items: src.approvalQueue.items.map(toApprovalRow),
      velocity: src.approvalQueue.velocity,
    },
    leaveCalendar30d: {
      from: src.leaveCalendar30d.from,
      to: src.leaveCalendar30d.to,
      items: src.leaveCalendar30d.items.map(toLeaveCalendarItem),
    },
    context: src.context,
  };
}

/* —————— row mappers —————— */

function toTodayRollup(today: BffTeamAttendanceToday | null): ManagerTodayRollup {
  if (!today) {
    return { present: 0, late: 0, absent: 0, onLeave: 0, pendingPunch: 0, people: [] };
  }
  const people = today.rows.map(toTeamMember);
  let present = 0;
  let late = 0;
  let absent = 0;
  let onLeave = 0;
  let pendingPunch = 0;
  for (const p of people) {
    switch (p.status) {
      case 'PRESENT':
        present += 1;
        break;
      case 'LATE':
        late += 1;
        present += 1;
        break;
      case 'ABSENT':
        absent += 1;
        break;
      case 'ON_LEAVE':
        onLeave += 1;
        break;
      case 'PENDING_PUNCH':
        pendingPunch += 1;
        break;
    }
  }
  return { present, late, absent, onLeave, pendingPunch, people };
}

function toTeamMember(row: BffTeamAttendanceTodayRow): TeamMemberRow {
  const status = toMemberStatus(row.status, row.lateMinutes ?? null);
  return {
    id: row.employee.id,
    name: row.employee.fullName,
    employeeCode: row.employee.employeeCode ?? null,
    departmentName: row.employee.departmentName ?? null,
    profilePictureUrl: row.employee.profilePictureUrl ?? null,
    status,
    checkInAt: hhmmFromIso(row.checkIn),
    varianceMin: row.lateMinutes ?? null,
  };
}

function toApprovalRow(row: BffManagerApprovalRow): ApprovalRow {
  const kind = toApprovalKind(row.kind);
  return {
    id: String(row.id),
    kind,
    kindLabel: APPROVAL_KIND_LABEL[kind],
    person: {
      id: row.employee.id,
      name: row.employee.fullName,
      employeeCode: row.employee.employeeCode ?? null,
      departmentName: row.employee.departmentName ?? null,
      profilePictureUrl: row.employee.profilePictureUrl ?? null,
    },
    // BFF doesn't surface a date range — use createdAt as a single-day
    // anchor; layouts fall back to summary when start === end.
    start: row.createdAt.slice(0, 10),
    end: row.createdAt.slice(0, 10),
    submittedAt: row.createdAt,
    reason: row.summary,
    tone: APPROVAL_KIND_TONE[kind],
    href: APPROVAL_KIND_HREF[kind],
  };
}

function toLeaveCalendarItem(item: BffManagerLeaveItem): LeaveCalendarItemDto {
  return {
    id: `${item.personId}-${item.start}`,
    personId: item.personId,
    personName: item.personName,
    start: item.start,
    end: item.end,
    leaveType: item.leaveType,
    tone: leaveTone(item.leaveType),
  };
}

/* —————— enums + lookups —————— */

const APPROVAL_KIND_LABEL: Record<ApprovalKind, string> = {
  LEAVE: 'Leave',
  SHORT_LEAVE: 'Short leave',
  OVERTIME: 'Overtime',
  REGULARIZATION: 'Regularization',
  SHIFT_OVERRIDE: 'Shift override',
};

const APPROVAL_KIND_TONE: Record<ApprovalKind, ChartTone> = {
  LEAVE: 'primary',
  SHORT_LEAVE: 'primary',
  OVERTIME: 'warning',
  REGULARIZATION: 'info',
  SHIFT_OVERRIDE: 'info',
};

const APPROVAL_KIND_HREF: Record<ApprovalKind, string> = {
  LEAVE: '/time/leaves',
  SHORT_LEAVE: '/time/short-leave',
  OVERTIME: '/time/overtime',
  REGULARIZATION: '/attendance/inbox',
  SHIFT_OVERRIDE: '/attendance/shift-overrides',
};

function toApprovalKind(kind: string): ApprovalKind {
  const u = (kind ?? '').toUpperCase();
  if (u.includes('SHORT')) return 'SHORT_LEAVE';
  if (u.includes('OVERTIME') || u === 'OT') return 'OVERTIME';
  if (u.includes('REGULAR')) return 'REGULARIZATION';
  if (u.includes('SHIFT')) return 'SHIFT_OVERRIDE';
  return 'LEAVE';
}

function toMemberStatus(s: string, lateMinutes: number | null): TeamMemberStatus {
  switch (s) {
    case 'PRESENT':
      return lateMinutes !== null && lateMinutes > 0 ? 'LATE' : 'PRESENT';
    case 'LATE':
      return 'LATE';
    case 'LEAVE':
    case 'ON_LEAVE':
    case 'HALF_DAY':
      return 'ON_LEAVE';
    case 'PENDING_PUNCH':
      return 'PENDING_PUNCH';
    case 'ABSENT':
    case 'HOLIDAY':
    case 'WEEKEND':
    default:
      return 'ABSENT';
  }
}

function leaveTone(leaveType: string): ChartTone {
  const u = (leaveType ?? '').toUpperCase();
  if (u.includes('SICK')) return 'error';
  if (u.includes('CASUAL')) return 'info';
  if (u.includes('BEREAV')) return 'neutral';
  return 'primary';
}

function hhmmFromIso(iso: string | null): string | null {
  if (!iso) return null;
  const hhmm = /^(\d{2}):(\d{2})$/.exec(iso);
  if (hhmm) return iso;
  const t = /T(\d{2}):(\d{2})/.exec(iso);
  if (t) return `${t[1]}:${t[2]}`;
  return null;
}

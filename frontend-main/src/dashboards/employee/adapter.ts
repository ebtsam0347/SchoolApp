/**
 * Adapter: BFF `EmployeeDashboard` → view-model `EmployeeDashboardDto`.
 *
 * BFF returns a slightly leaner shape (e.g. queue items only carry
 * `kind` + `summary`, no per-row hrefs); the adapter derives the
 * fields the layout depends on (kind label, deep links, formatted
 * check-in time) so we can keep layout rendering simple.
 */
import type {
  BffEmployeeDashboard,
  BffEmployeeRequestRow,
} from '../bff-types';
import type {
  AttendanceHeatmapDayDto,
  DocumentAckRow,
  EmployeeDashboardDto,
  EmployeeTodayStatus,
  MissingPunchRow,
  RequestRow,
  RunStatus,
} from '../types';

export function toEmployeeDashboardDto(
  src: BffEmployeeDashboard,
): EmployeeDashboardDto {
  return {
    nextPayday: {
      date: src.nextPayday?.date ?? null,
      projectedNet: src.nextPayday?.projectedNet
        ? {
            amount: src.nextPayday.projectedNet.amount,
            currency: (src.nextPayday.projectedNet.currency ?? 'PKR') as 'PKR',
          }
        : null,
      runId: src.nextPayday?.runId ?? null,
      runStatus: src.nextPayday?.runStatus
        ? toRunStatus(src.nextPayday.runStatus)
        : null,
    },
    today: {
      status: toTodayStatus(src.today.status),
      checkInAt: hhmmFromIso(src.today.checkInAt),
      shift: src.today.shift ?? null,
      elapsedMinutes: src.today.elapsedMinutes ?? null,
    },
    needsYou: {
      pendingRequests: {
        count: src.needsYou.pendingRequests.count,
        items: src.needsYou.pendingRequests.items.map(toRequestRow),
      },
      missingPunches: {
        count: src.needsYou.missingPunches.count,
        items: src.needsYou.missingPunches.items.map<MissingPunchRow>((r) => ({
          date: r.date,
          shiftName: null,
          hint: r.reason,
          href: '/time/today',
        })),
      },
      documentsToAck: {
        count: src.needsYou.documentsToAck.count,
        items: src.needsYou.documentsToAck.items.map<DocumentAckRow>((d) => ({
          id: String(d.id),
          title: d.name,
          effectiveOn: null,
          href: '/account',
        })),
      },
    },
    leaveBalance: src.leaveBalance,
    attendanceHeatmap: {
      month: src.attendanceHeatmap.month,
      days: src.attendanceHeatmap.days.map<AttendanceHeatmapDayDto>((d) => ({
        date: d.date,
        status: toHeatmapStatus(d.status),
        varianceMin: d.varianceMin ?? null,
        checkInAt: null,
      })),
    },
    payslips: {
      recent: src.payslips.recent.map((p) => ({
        id: p.id,
        period: `${p.periodYear}-${String(p.periodMonth).padStart(2, '0')}`,
        net: {
          amount: p.net.amount,
          currency: (p.net.currency ?? 'PKR') as 'PKR',
        },
        pdfUrl: null,
      })),
      netTrend12m: src.payslips.netTrend12m,
    },
    upcomingHolidays: src.upcomingHolidays.map((h) => ({
      date: h.date,
      name: h.name,
      dayOfWeek: toDow(h.dayOfWeek),
    })),
  };
}

/* —————— small helpers —————— */

const REQUEST_KIND_LABEL: Record<string, string> = {
  LEAVE: 'Leave',
  ANNUAL_LEAVE: 'Annual leave',
  SICK_LEAVE: 'Sick leave',
  CASUAL_LEAVE: 'Casual leave',
  OVERTIME: 'Overtime',
  REGULARIZATION: 'Regularization',
};

const REQUEST_KIND_HREF: Record<string, string> = {
  LEAVE: '/time/leaves',
  ANNUAL_LEAVE: '/time/leaves',
  SICK_LEAVE: '/time/leaves',
  CASUAL_LEAVE: '/time/leaves',
  OVERTIME: '/time/overtime',
  REGULARIZATION: '/time/attendance',
};

function toRequestRow(r: BffEmployeeRequestRow): RequestRow {
  const kind = (r.kind ?? '').toUpperCase();
  const kindLabel = REQUEST_KIND_LABEL[kind] ?? prettify(kind);
  // The BFF returns a pre-shaped summary; we don't have explicit start/end
  // so we use createdAt as the only date anchor available. The layout
  // formats the range and falls back to summary when dates aren't useful.
  return {
    id: String(r.id),
    kind: (kind === 'OVERTIME' || kind === 'REGULARIZATION'
      ? kind
      : 'LEAVE') as RequestRow['kind'],
    kindLabel,
    start: r.createdAt.slice(0, 10),
    end: r.createdAt.slice(0, 10),
    status:
      r.status === 'APPROVED' || r.status === 'REJECTED' || r.status === 'PENDING'
        ? r.status
        : 'PENDING',
    summary: r.summary,
    href: REQUEST_KIND_HREF[kind] ?? '/inbox',
  };
}

function toTodayStatus(s: string): EmployeeTodayStatus {
  switch (s) {
    case 'CHECKED_IN':
    case 'ON_LEAVE':
    case 'OFF':
    case 'PENDING_PUNCH':
      return s;
    default:
      return 'OFF';
  }
}

function toRunStatus(s: string): RunStatus {
  switch (s) {
    case 'DRAFT':
    case 'LOCKED':
    case 'POSTED':
    case 'RUNNING':
      return s;
    default:
      return 'DRAFT';
  }
}

function toHeatmapStatus(s: string): AttendanceHeatmapDayDto['status'] {
  switch (s) {
    case 'PRESENT':
    case 'LATE':
    case 'ABSENT':
    case 'ON_LEAVE':
    case 'OFF':
    case 'HOLIDAY':
      return s;
    default:
      return 'OFF';
  }
}

function toDow(s: string): EmployeeDashboardDto['upcomingHolidays'][number]['dayOfWeek'] {
  const u = (s ?? '').slice(0, 3).toUpperCase();
  if (u === 'MON' || u === 'TUE' || u === 'WED' || u === 'THU' || u === 'FRI' || u === 'SAT' || u === 'SUN') {
    return u;
  }
  return 'MON';
}

function hhmmFromIso(iso: string | null): string | null {
  if (!iso) return null;
  // BFF returns either an ISO datetime ("2026-04-30T09:04:00+05:00") or
  // a plain HH:mm string. Handle both.
  const hhmmMatch = /^(\d{2}):(\d{2})$/.exec(iso);
  if (hhmmMatch) return iso;
  const tMatch = /T(\d{2}):(\d{2})/.exec(iso);
  if (tMatch) return `${tMatch[1]}:${tMatch[2]}`;
  return null;
}

function prettify(kind: string): string {
  return kind
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

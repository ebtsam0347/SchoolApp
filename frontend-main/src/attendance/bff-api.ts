// BFF composites for the attendance module. One round-trip per page surface
// (today / month / inbox / apply-context / overtime-me / employee resolver).
// Same `http` wrapper as everything else, so bearer + idempotency + 401-refresh
// behave identically to the direct attendance-service wrappers in `./api.ts`.
//
// Contract source: BFF_ATTENDANCE_WISHLIST.md (Shipped section). When the
// attendance-service / BFF specs evolve, mirror them here and keep the
// service-direct wrappers in sync.

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';
import type { Page } from '@/lib';
import type {
  ApprovalDecisionRow,
  ApprovalRequestType,
  AttendanceRecordDto,
  AttendanceStatus,
  HolidayDto,
  LeaveBalanceDto,
  LeaveRequestDto,
  LeaveTypeDto,
  OvertimeRequestDto,
  OvertimeType,
  PunchDto,
  PunchSource,
  RegularizationRequestDto,
  ShiftPeriod,
  ShortLeaveRequestDto,
} from './types';
import type { RequisitionDto, RequisitionType } from '@/requisition';
import type { PermanentAdvanceDto, ShortTermAdvanceBatchDto } from '@/payroll';
import type { TravelRequestResponse } from '@/travel';

/* ---------------------------- Shared shapes ---------------------------- */

/**
 * Lightweight employee descriptor returned by the by-ids resolver and inlined
 * on every approvals-inbox row. Stable across surfaces — matches the
 * `EmployeeSummaryResolver` output on the BFF.
 */
export type EmployeeSummary = {
  id: number;
  fullName: string;
  employeeCode: string | null;
  departmentId: number | null;
  departmentName: string | null;
  sectionId: number | null;
  sectionName: string | null;
  /** Resolved on the BFF's EmployeeSummaryResolver. */
  designationId: number | null;
  designationName: string | null;
  profilePictureUrl: string | null;
};

/* -------------- T1: GET /attendance/me/today/composite ---------------- */

export type TodayShortcut = {
  fromDate: string;
  toDate: string;
  leaveTypeCode: string;
};

export type TodayRecord = {
  workDate: string;
  status: AttendanceStatus;
  checkIn: string | null;
  checkOut: string | null;
  lateMinutes: number;
  workedMinutes: number;
  otNormalMinutes: number;
  otExtraMinutes: number;
  otSpecialMinutes: number;
  shiftStartTime: string | null;
  shiftEndTime: string | null;
  shiftBreakMinutes: number | null;
};

export type AttendanceTodayResponse = {
  today: TodayRecord | null;
  punches: PunchDto[];
  activeLeave: LeaveRequestDto | null;
  todayHoliday: HolidayDto | null;
  upcoming: {
    nextHoliday: HolidayDto | null;
    nextApprovedLeave: TodayShortcut | null;
  };
  balanceSummary: {
    annualAvailable: number;
    sickAvailable: number;
    casualAvailable: number;
  };
};

/* -------------- T2: GET /attendance/me/month/composite ---------------- */

export type MonthDayKind =
  | 'PRESENT'
  | 'ABSENT'
  | 'LEAVE'
  | 'HOLIDAY'
  | 'WEEKEND'
  | 'HALF_DAY'
  | 'FUTURE';

export type MonthDayLeaveRef = {
  requestId: number;
  leaveTypeCode: string;
  halfDay: boolean;
  halfDayPart: 'AM' | 'PM' | null;
};

export type MonthDay = {
  workDate: string;
  /**
   * Resolved status with the BFF's precedence baked in:
   * HOLIDAY > LEAVE > rec.status > WEEKEND/ABSENT.
   */
  kind: MonthDayKind;
  record: AttendanceRecordDto | null;
  holiday: HolidayDto | null;
  leave: MonthDayLeaveRef | null;
};

export type MonthSummary = {
  present: number;
  absent: number;
  leave: number;
  holiday: number;
  halfDay: number;
  weekend: number;
  totalWorkedMinutes: number;
  totalOtMinutes: number;
  totalLateMinutes: number;
};

export type AttendanceMonthResponse = {
  days: MonthDay[];
  summary: MonthSummary;
};

/* ---- GET /bff/attendance/team/today + /bff/attendance/team/{id}/month  */

/**
 * Per-row status on the team roll-call. Includes PENDING_PUNCH for
 * employees who haven't checked in yet today — that case never appears
 * on `me/month` (the BFF closes the day) but matters in real-time on
 * the team page.
 */
export type TeamAttendanceStatus =
  | 'PRESENT'
  | 'ABSENT'
  | 'LEAVE'
  | 'HOLIDAY'
  | 'WEEKEND'
  | 'HALF_DAY'
  | 'PENDING_PUNCH';

/** Reserved for v2 — always null in v1. */
export type TeamAttendanceActiveLeave = {
  leaveTypeCode: string;
  halfDay: boolean;
};

export type TeamAttendanceTodayRow = {
  employee: EmployeeSummary;
  status: TeamAttendanceStatus;
  checkIn: string | null;
  checkOut: string | null;
  lateMinutes: number;
  /** Always null until the upstream active-leave endpoint lands. */
  activeLeave: TeamAttendanceActiveLeave | null;
};

export type TeamAttendanceTodayResponse = {
  /** Date the rows describe (defaults to today, Asia/Karachi). */
  asOf: string;
  /**
   * Direct reports first, then delegated reports. Capped at 500; if the
   * coverage set exceeds that, `truncated=true` and the FE should drop
   * to the filtered org page.
   */
  rows: TeamAttendanceTodayRow[];
  truncated: boolean;
};

/* ----------------------- T3: GET /approvals/inbox --------------------- */

/**
 * Per-row inbox shape: nested `{ request, employee }`. The BFF intentionally
 * doesn't intersect — the upstream Dto is preserved verbatim under
 * `request`, and the resolved employee summary sits next to it.
 */
export type ApprovalInboxLeaveRow = {
  request: LeaveRequestDto;
  employee: EmployeeSummary;
};

export type ApprovalInboxOvertimeRow = {
  request: OvertimeRequestDto;
  employee: EmployeeSummary;
  /** The work date's punch-derived minutes — set on the approvals inbox and
   *  the team / department / filed-by-me lists (absent on API builds that
   *  predate it). The OT buckets stay 0 until approval; `projectedOtMinutes`
   *  is what approving would credit. */
  minutes?: {
    otNormalMinutes: number;
    otExtraMinutes: number;
    otSpecialMinutes: number;
    workedMinutes: number;
    projectedOtMinutes?: number;
  } | null;
};

export type ApprovalInboxRegularizationRow = {
  request: RegularizationRequestDto;
  employee: EmployeeSummary;
};

export type ApprovalInboxShortLeaveRow = {
  request: ShortLeaveRequestDto;
  employee: EmployeeSummary;
};

export type ApprovalsInboxResponse = {
  leaves: ApprovalInboxLeaveRow[];
  overtime: ApprovalInboxOvertimeRow[];
  regularization: ApprovalInboxRegularizationRow[];
  /**
   * The standalone `/short-leaves/pending-approvals` endpoint 404s (never
   * shipped), so short leave fans into this same composite like leaves/
   * overtime/regularization. Confirmed against a live response the key is
   * singular (`shortLeave`), unlike every other slice here — still marked
   * optional so callers fall back to `[]` if a future backend rev drops it.
   */
  shortLeave?: ApprovalInboxShortLeaveRow[];
  /**
   * Cross-service pending lists fanned into the same composite (requisition-
   * service / payroll-service / travel-request-service). Unlike leaves/
   * overtime/regularization these aren't attendance-domain requests, so rows
   * come through as the plain upstream DTO — no `{ request, employee }` wrap.
   *
   * Every slice here is optional: `erroredSources` reports a fan-in that
   * failed, and the backend omits that key entirely rather than sending `[]`.
   * Read them through `?? []` — a bare `.map()` on one of these took the whole
   * app down when `factoryTours` was renamed to `travelRequests`.
   */
  requisitions?: RequisitionDto[];
  /** Wire shape is `{ request, approvalStage, employee }`; typed as a union
   *  so `unwrapRequest` still accepts a bare DTO. */
  permanentAdvances?: (PermanentAdvanceDto | ApprovalInboxPermanentAdvanceRow)[];
  shortTermAdvances?: ShortTermAdvanceBatchDto[];
  /**
   * Raw /travel wire shape (header + `legs[]` + `participants[]`), not the
   * flat presentation DTO — run rows through `toFactoryTourDto` before
   * handing them to the Factory Tour table/drawer.
   */
  travelRequests?: TravelRequestResponse[];
  counts: {
    leaves: number;
    overtime: number;
    regularization: number;
    /** Singular, matching the `shortLeave` slice above. */
    shortLeave?: number;
    requisitions: number;
    permanentAdvances: number;
    shortTermAdvances: number;
    travelRequests: number;
    total: number;
  };
  /** Upstream sources that failed to fan in — that slice comes back empty
   *  rather than failing the whole composite. */
  erroredSources: string[];
};

/**
 * Requisitions/advances/factory-tour rows on the combined composite may
 * come back either as the plain upstream DTO or wrapped `{ request,
 * employee }` (same shape leaves/overtime/regularization already use).
 * Unwrap defensively so a row's own fields (createdAt, status, …) are
 * always where callers expect them — a raw DTO with a field literally
 * named `request` would be misread, but none of these DTOs have one, so
 * the check is safe in practice.
 */
export function unwrapRequest<T>(item: T | { request: T }): T {
  return item != null && typeof item === 'object' && 'request' in item
    ? (item as { request: T }).request
    : (item as T);
}

/* ----------------------- T4: GET /bff/employees/by-ids ---------------- */

export type EmployeesByIdsResponse = {
  employees: EmployeeSummary[];
};

/* --------------- T5: GET /overtime/me/with-records -------------------- */

/**
 * OT row enriched with attendance_records minutes for the same
 * (employee, workDate). Minutes are zero when the OT is still
 * PENDING / REJECTED, or when no attendance record exists yet.
 */
export type OvertimeMineRow = {
  request: OvertimeRequestDto;
  otNormalMinutes: number;
  otExtraMinutes: number;
  otSpecialMinutes: number;
  workedMinutes: number;
};

/* --------------- T6: GET /leaves/apply-context ------------------------ */

export type LeaveApplyContextResponse = {
  types: LeaveTypeDto[];
  balances: LeaveBalanceDto[];
  /** Holidays in [today, today + 90d]. Cross-year aware. */
  holidaysInNext90Days: HolidayDto[];
};

/* ----------- T17: GET /bff/timeline/{requestType}/{id} ---------------- */

export type RequestTimelineKind = 'leave' | 'short-leave' | 'overtime' | 'regularization';

export type TimelineEntryView = {
  id: number;
  /** e.g. SUBMITTED, STAGE_APPROVED, STAGE_REJECTED, DAY_DECIDED, CANCELLED. */
  action: string;
  actorUsername: string | null;
  actorLabel: string | null;
  message: string | null;
  occurredAt: string;
};

export type TimelinePageView = {
  content: TimelineEntryView[];
  /** Opaque cursor — null when no more pages. */
  nextCursor: string | null;
};

export type RequestTimelineParams = {
  /** Page size. Default 50, max 200. */
  limit?: number;
  /** Cursor from a previous response's `nextCursor`. */
  cursor?: string;
};

/* ----------- GET /attendance/operator/dashboard ----------------------- */

/**
 * Org-wide attendance roll-up for a single date. One SQL aggregate over
 * `attendance_records` upstream — drives the dashboard's pulse card.
 */
export type OrgPulseView = {
  asOf: string;
  present: number;
  late: number;
  absent: number;
  onLeave: number;
  halfDay: number;
  holiday: number;
  weekend: number;
  total: number;
};

export type OperatorAttendanceRow = {
  record: AttendanceRecordDto;
  employee: EmployeeSummary | null;
};

/**
 * Composite landing payload for the attendance-operator UI. One round-trip
 * — fan-out happens server-side. `missingCheckouts` and `lateArrivals` are
 * the first page only (size 25); `totalElements` carries the full count.
 * The endpoint takes no paging params, so the UI shows the first page and
 * surfaces the true total.
 */
export type OperatorDashboardResponse = {
  date: string;
  pulse: OrgPulseView;
  totalPunchesToday: number;
  activeOverridesToday: number;
  pendingRegularizations: number;
  missingCheckouts: BffPage<OperatorAttendanceRow>;
  lateArrivals: BffPage<OperatorAttendanceRow>;
};

/* ---- GET /attendance/records/with-employee + /attendance/punches/...   */

/**
 * Standard Spring-style page envelope used by the records / punches list
 * endpoints. Mirrors `PageResponse<T>` from attendance-service 1:1 but the
 * BFF wraps each row in a discriminated `{ record | punch, employee }` shape
 * so the table can render in one round-trip.
 */
export type BffPage<T> = Page<T>;

export type AttendanceRecordsRow = {
  record: AttendanceRecordDto;
  employee: EmployeeSummary | null;
};

export type AttendancePunchesRow = {
  punch: PunchDto;
  employee: EmployeeSummary | null;
};

export type AttendanceRecordsPage = BffPage<AttendanceRecordsRow>;
export type AttendancePunchesPage = BffPage<AttendancePunchesRow>;

/**
 * Filters for `GET /attendance/records/with-employee`. Server defaults to today (local)
 * when no date filter lands. `employeeId` wins over `employeeCode`. Default
 * sort is `attendanceDate,desc`. Page size 50, max 500.
 */
export type AttendanceRecordsListParams = {
  /** Single-day shortcut. Mutually exclusive with from/to (server picks date). */
  date?: string;
  from?: string;
  to?: string;
  employeeId?: number;
  employeeCode?: string;
  departmentId?: number;
  sectionId?: number;
  status?: AttendanceStatus;
  lateOnly?: boolean;
  page?: number;
  size?: number;
  /** e.g. `attendanceDate,desc`. */
  sort?: string;
};

/**
 * Filters for `GET /attendance/punches/with-employee`. Same conventions as records;
 * default sort is `punchedAt,desc`.
 */
export type AttendancePunchesListParams = {
  date?: string;
  from?: string;
  to?: string;
  employeeId?: number;
  employeeCode?: string;
  departmentId?: number;
  sectionId?: number;
  source?: PunchSource;
  /** Device numeric id (upstream column is bigint). */
  deviceId?: number;
  page?: number;
  size?: number;
  /** e.g. `punchedAt,desc`. */
  sort?: string;
};

/* -------------- T7: GET /employees/delegation-candidates -------------- */

export type DelegationCandidate = {
  userId: number;
  fullName: string;
  employeeCode: string | null;
  /** Reserved for v2 — auth role of the candidate. Currently always null. */
  role: string | null;
};

export type DelegationCandidatesResponse = {
  users: DelegationCandidate[];
};

/* ------------------------------ Wrappers ------------------------------ */

export const attendanceBff = {
  today: (signal?: AbortSignal) =>
    http.get<AttendanceTodayResponse>('/attendance/me/today/composite', { signal }),
  month: (month: string, signal?: AbortSignal) =>
    http.get<AttendanceMonthResponse>(
      `/attendance/me/month/composite${qs({ month })}`,
      { signal },
    ),
  /** Org-wide records browser. Gated upstream by `attendance.admin` OR
   *  `attendance.operator`. */
  records: (params: AttendanceRecordsListParams = {}, signal?: AbortSignal) =>
    http.get<AttendanceRecordsPage>(
      `/attendance/records/with-employee${qs({ ...params })}`,
      { signal },
    ),
  /** Org-wide punches browser. Same gating as `records()`. */
  punches: (params: AttendancePunchesListParams = {}, signal?: AbortSignal) =>
    http.get<AttendancePunchesPage>(
      `/attendance/punches/with-employee${qs({ ...params })}`,
      { signal },
    ),
  /**
   * Operator landing payload: org pulse, today's punch / override / pending-
   * regularization counts, missing-checkouts + late-arrivals tables. One
   * round-trip; defaults to today (Asia/Karachi) when `date` is omitted.
   * `period` limits it to employees on a day / night shift that date.
   */
  operatorDashboard: (date?: string, period?: ShiftPeriod, signal?: AbortSignal) =>
    http.get<OperatorDashboardResponse>(
      `/attendance/operator/dashboard${qs({ date, period })}`,
      { signal },
    ),
  /**
   * Manager roll-call for the caller's direct reports + delegated reports
   * on the supplied date (defaults to today, Asia/Karachi). Returns
   * `rows: []` when the caller has no team — never 403. Capped at 500
   * rows; check `truncated` to drop to a filtered org view.
   */
  teamToday: (date?: string, signal?: AbortSignal) =>
    http.get<TeamAttendanceTodayResponse>(
      `/bff/attendance/team/today${qs({ date })}`,
      { signal },
    ),
  /**
   * Per-employee month view from the manager perspective — same shape as
   * `me/month` so the existing MonthGrid renders without changes.
   * 403 when the caller doesn't manage the target and holds no covering
   * delegation.
   */
  teamMonth: (employeeId: number, month?: string, signal?: AbortSignal) =>
    http.get<AttendanceMonthResponse>(
      `/bff/attendance/team/${employeeId}/month${qs({ month })}`,
      { signal },
    ),
};

/**
 * `/approvals/history` is rewritten by the gateway to the BFF composite
 * `/bff/approvals/history`, which fans out to attendance, payroll and
 * employee in parallel and merges them caller-scoped — all eleven request
 * types in one call.
 *
 * The payload is `{ rows, total }`, NOT a Spring `PageResponse`
 * (`{ content, totalElements }`). Typing it as a page silently yields
 * `undefined` rows and an always-empty table, which is exactly what this
 * view used to render.
 *
 * `type` and `decision` are honoured server-side (a `type` fans out only to
 * its owning source). There is still no server paging — the composite does
 * one generous fetch per source — so the caller pages the merged list.
 */
export type ApprovalHistoryParams = {
  from: string;
  to: string;
  type?: ApprovalRequestType;
  decision?: 'APPROVED' | 'REJECTED';
};

export type ApprovalHistoryResponse = {
  rows: ApprovalDecisionRow[];
  total: number;
  /** Sources that soft-failed; their rows are simply absent. */
  erroredSources?: string[];
};

/**
 * Per-bucket filters. Each one narrows only its own bucket (and that
 * bucket's count); the other buckets come back unfiltered. Every `…Q`
 * matches the request id (exact) or a substring of request code, employee
 * code or employee name.
 */
export type ApprovalsInboxParams = {
  leaveQ?: string;
  leaveTypeId?: number;
  shortLeaveQ?: string;
  overtimeQ?: string;
  overtimeType?: OvertimeType;
  regularizationQ?: string;
  requisitionQ?: string;
  requisitionType?: RequisitionType;
  requisitionDepartmentId?: number;
  /** Short-term advances: batch id (exact), or substring of request code. */
  shortTermAdvanceQ?: string;
  /** Permanent advances: advance id (exact), or substring of request code,
   *  employee code, or employee name. */
  permanentAdvanceQ?: string;
};

/** One `permanentAdvances` row of `/approvals/inbox`. */
export type ApprovalInboxPermanentAdvanceRow = {
  request: PermanentAdvanceDto;
  approvalStage: string | null;
  /** Resolved server-side; null if the employee couldn't be resolved. */
  employee: EmployeeSummary | null;
};

/** Employee summaries inlined on the inbox's permanent-advance rows, by employee id. */
export function permanentAdvanceEmployees(
  rows: readonly (PermanentAdvanceDto | ApprovalInboxPermanentAdvanceRow)[],
): Map<number, EmployeeSummary> {
  const m = new Map<number, EmployeeSummary>();
  for (const r of rows) {
    if ('request' in r && r.employee) m.set(r.request.employeeId, r.employee);
  }
  return m;
}

export const approvalsBff = {

  inbox: (signal?: AbortSignal, params: ApprovalsInboxParams = {}) =>
    http.get<ApprovalsInboxResponse>(`/approvals/inbox${qs({ ...params })}`, { signal }),

  history: (params: ApprovalHistoryParams, signal?: AbortSignal) =>
    http.get<ApprovalHistoryResponse>(
      `/approvals/history${qs({
        from: params.from,
        to: params.to,
        type: params.type,
        decision: params.decision,
      })}`,
      { signal },
    ),
};

export const employeesBff = {
  /** 200-id cap, server de-dupes the input. */
  byIds: (ids: number[], signal?: AbortSignal) => {
    if (ids.length === 0) {
      return Promise.resolve<EmployeesByIdsResponse>({ employees: [] });
    }
    const sp = new URLSearchParams();
    sp.set('ids', ids.join(','));
    return http.get<EmployeesByIdsResponse>(`/bff/employees/by-ids?${sp}`, { signal });
  },
};

export const overtimeBff = {
  /** OT rows + derived minutes from attendance_records. Nested shape: each
   * element is `{ request, otNormalMinutes, otExtraMinutes, otSpecialMinutes,
   * workedMinutes }`. */
  mine: (signal?: AbortSignal) =>
    http.get<OvertimeMineRow[]>('/overtime/me/with-records', { signal }),
};

export const leavesBff = {
  /** Types + balances + next-90d holidays in one call. */
  applyContext: (signal?: AbortSignal) =>
    http.get<LeaveApplyContextResponse>('/leaves/apply-context', { signal }),
};

export const timelineBff = {
  /**
   * Approval-request audit feed. The BFF probes the upstream request first
   * (404 if it doesn't exist or the caller can't see it) and then reads the
   * audit timeline. Cursor pagination via the audit-service cursor.
   */
  forRequest: (
    requestType: RequestTimelineKind,
    id: number,
    params: RequestTimelineParams = {},
    signal?: AbortSignal,
  ) =>
    http.get<TimelinePageView>(
      `/bff/timeline/${requestType}/${id}${qs({ ...params })}`,
      { signal },
    ),
};

export const delegationsBff = {
  /**
   * Delegate-picker scope: ACTIVE employees with ESS, excluding the caller.
   * `q` substring-matches name + employee code; `limit` defaults to 20.
   */
  candidates: (
    params: { q?: string; limit?: number } = {},
    signal?: AbortSignal,
  ) =>
    http.get<DelegationCandidatesResponse>(
      `/employees/delegation-candidates${qs({ ...params })}`,
      { signal },
    ),
};

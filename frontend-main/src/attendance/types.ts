// Attendance-service contract types. Mirrors
// `docs/services/attendance.openapi.json` — keep in sync if the spec drifts.
// Domain rules + status semantics are documented in
// `docs/services/attendance.md` §3 + §7.

import type { Page } from '@/lib';
import type { TeamAttendanceTodayRow } from './bff-api';

/* --------------------------- Pagination ------------------------------- */

/** Standard Spring-style page envelope. Canonical `Page<T>` lives in `@/lib`. */
export type PageResponse<T> = Page<T>;

/* ------------------------------ Enums --------------------------------- */

export type RequestType = 'LEAVE' | 'SHORT_LEAVE' | 'OVERTIME' | 'REGULARIZATION';

export type ApprovalStepStatus =
  | 'NOT_YET'
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'SKIPPED';

/** Stage role lives in code — extend as new roles appear (DEPT_HEAD etc.). */
export type ApprovalStageRole = 'MANAGER' | 'DEPT_HEAD' | 'HR' | (string & {});

/** What the calling user can do on the current step. Server-computed. */
export type ApprovalAction = 'APPROVE' | 'REJECT' | 'DECIDE_PER_DAY' | 'CANCEL';

export type LeaveStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'PARTIALLY_APPROVED'
  | 'CANCELLED';

export type LeaveDayStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type ShortLeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export type OvertimeStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

/**
 * What kind of OT the submitter is asking for. Picked at submit time — the
 * day's minute bucket recompute respects this choice, so a NORMAL request
 * only pays the first 120 min beyond shift end, EXTRA only the minutes past
 * that 120-min cap, and SPECIAL is reserved for work on a Sunday / holiday.
 */
export type OvertimeType = 'NORMAL' | 'EXTRA' | 'SPECIAL';

/** Factory / unit an overtime request is worked for. Literal wire values. */
export type FactoryCode =
  | 'B34002'
  | 'B34003'
  | 'B34004'
  | 'B34005'
  | 'B34006'
  | 'B34007'
  | 'Administrator';

export type RegularizationStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED';

export type AttendanceStatus =
  | 'PRESENT'
  | 'ABSENT'
  | 'LEAVE'
  | 'HOLIDAY'
  | 'WEEKEND'
  | 'HALF_DAY';

export type PunchSource =
  | 'BIOMETRIC'
  | 'WEB'
  | 'OPERATOR'
  | 'REGULARIZED'
  | 'SYSTEM';

export type HolidayType = 'NATIONAL' | 'COMPANY' | 'PROVINCIAL';

export type HalfDayPart = 'AM' | 'PM';

/**
 * When a leave type's entitlement cycle renews.
 * - `JOINING` — anniversary of the employee's join date (e.g. annual leave).
 * - `FISCAL` — the Jul–Jun fiscal year (e.g. casual / M1).
 * - `NONE` — occasional, no fixed renewal (calendar-year, lazily allocated).
 */
export type LeaveCycleAnchor = 'JOINING' | 'FISCAL' | 'NONE';

export type DelegationStatus = 'ACTIVE' | 'REVOKED';

export type PerDayAction = 'APPROVE' | 'REJECT';

export type AttendanceDeviceStatus = 'UNKNOWN' | 'ONLINE' | 'OFFLINE' | 'ERROR';

/**
 * Physical role of a device. Default ATTENDANCE; CANTEEN / ACCESS_LOG units
 * emit punches that must NOT count towards attendance. The canteen module
 * (`src/canteen`) keys its device list off `CANTEEN`.
 */
export type AttendanceDeviceType = 'ATTENDANCE' | 'CANTEEN' | 'ACCESS_LOG';

/* ----------------------------- Shifts --------------------------------- */

/** Day or night shift. The operator dashboard filters on it. */
export type ShiftPeriod = 'DAY' | 'NIGHT';

export const SHIFT_PERIOD_LABEL: Record<ShiftPeriod, string> = {
  DAY: 'Day',
  NIGHT: 'Night',
};

export type ShiftDto = {
  id: number;
  code: string;
  name: string;
  /** "HH:mm" or "HH:mm:ss" */
  startTime: string;
  /** "HH:mm" — if <= startTime, shift crosses midnight. */
  endTime: string;
  /** "HH:mm" — break window start. */
  breakStartTime: string | null;
  /** "HH:mm" — break window end. */
  breakEndTime: string | null;
  /** Derived from break window. Read-only on the wire. */
  breakMinutes: number;
  graceMinutes: number;
  /** Extra minutes added to break + end on Fridays only. Null/0 = no Friday adjustment. */
  fridayExtraMinutes: number | null;
  active: boolean;
  period: ShiftPeriod;
};

export type CreateShiftRequest = {
  code: string;
  name: string;
  startTime: string;
  endTime: string;
  /** "HH:mm" — break window start. Optional. */
  breakStartTime?: string;
  /** "HH:mm" — break window end. Optional. */
  breakEndTime?: string;
  graceMinutes?: number;
  fridayExtraMinutes?: number;
  period: ShiftPeriod;
};

export type AssignShiftRequest = {
  shiftId: number;
  effectiveFrom: string;
  effectiveTo?: string | null;
};

/**
 * An employee's shift assignment over a time range. `effectiveTo` is null
 * for the currently-open assignment. Returned newest-first by
 * `effectiveFrom`; the first row whose range covers today is the active one.
 */
export type EmployeeShiftDto = {
  id: number;
  employeeId: number;
  shiftId: number;
  shiftCode: string;
  shiftName: string;
  effectiveFrom: string;
  effectiveTo: string | null;
};

/* ---------------------------- Approvals ------------------------------- */

/**
 * Every request type the combined approval history can return, across all
 * three owning services. Doubles as the `type` filter's allowed values on
 * `/bff/approvals/history`.
 */
export const APPROVAL_REQUEST_TYPES = [
  'LEAVE',
  'SHORT_LEAVE',
  'OVERTIME',
  'REGULARIZATION',
  'PERMANENT_ADVANCE',
  'SHORT_TERM_ADVANCE',
  'REQUISITION_NEW_HIRE',
  'REQUISITION_REPLACEMENT',
  'TRAVEL_REQUEST',
  'TRAVEL_ADVANCE',
  'TRAVEL_SETTLEMENT',
] as const;

export type ApprovalRequestType = (typeof APPROVAL_REQUEST_TYPES)[number];

/**
 * One row in the "approvals I have decided" history view (§12.2 backend).
 * Unified across all eleven request types. The `type` discriminator tells
 * the FE which date / detail fields are populated — the leave-shaped ones
 * (`leaveTypeCode`, `days`, `endDate`) are null for advances and travel.
 */
export type ApprovalDecisionRow = {
  type: ApprovalRequestType;
  requestId: number;
  /** Human-readable code (LR-…, OT-…, REG-…). */
  requestCode: string;
  employeeId: number;
  employeeCode: string | null;
  fullName: string | null;
  /** When the caller closed the stage. */
  decidedAt: string;
  decision: 'APPROVED' | 'REJECTED';
  /** 1-based stage index. */
  stageNo: number;
  /** MANAGER / DEPT_HEAD / HR / …  */
  stageRole: ApprovalStageRole | null;
  note: string | null;
  /** LEAVE: fromDate. OVERTIME / REGULARIZATION: workDate. */
  startDate: string | null;
  /** LEAVE: toDate. Null for OVERTIME / REGULARIZATION. */
  endDate: string | null;
  /** LEAVE: leave-type code (e.g. ANNUAL). */
  leaveTypeCode: string | null;
  /** LEAVE: total working days requested. */
  days: number | null;
  /** LEAVE / REGULARIZATION: free-text reason. */
  reason: string | null;
};

export type ApprovalStepDto = {
  stageNo: number;
  stageRole: ApprovalStageRole;
  /** Snapshot user id for MANAGER / DEPT_HEAD; null for role-only stages (HR). */
  assignedTo: number | null;
  assignedToName: string | null;
  status: ApprovalStepStatus;
  /** The user who actually decided (may be a delegate, not assignedTo). */
  decidedBy: number | null;
  decidedByName: string | null;
  decidedAt: string | null;
  /** Approver note on approve, rejection reason on reject. */
  note: string | null;
};

export type CurrentStageDto = {
  stageNo: number;
  stageRole: ApprovalStageRole;
  assignedTo: number | null;
  assignedToName: string | null;
};

export type ApproveRequest = {
  note?: string;
};

export type RejectRequest = {
  reason: string;
};

/* -------------------------- Regularization ---------------------------- */

export type RegularizationRequest = {
  workDate: string;
  checkIn?: string | null;
  checkOut?: string | null;
  reason: string;
  /**
   * Submit on behalf of a direct report (manager) or any employee
   * (`attendance.approval.override`). When set, the MANAGER stage is
   * auto-approved; for an override holder, the whole chain is auto-approved.
   */
  onBehalfOf?: number;
};

export type RegularizationRequestDto = {
  id: number;
  /** Human-readable code, e.g. REG-2026-0008. */
  requestCode: string;
  employeeId: number;
  workDate: string;
  requestedCheckIn: string | null;
  requestedCheckOut: string | null;
  reason: string;
  status: RegularizationStatus;
  currentStage: CurrentStageDto | null;
  steps: ApprovalStepDto[];
  actions: ApprovalAction[];
  createdAt: string;
};

/* ---------------------------- Overtime -------------------------------- */

export type CreateOvertimeRequest = {
  workDate: string;
  /** Which bucket to pay — see `OvertimeType`. Required. */
  overtimeType: OvertimeType;
  /** Factory / unit the overtime is worked for. Required. */
  factoryCode: FactoryCode;
  reason?: string;
  /**
   * Submit on behalf of a direct report (manager) or any employee
   * (`attendance.approval.override`). When set, the MANAGER stage is
   * auto-approved; for an override holder, the whole chain is auto-approved.
   */
  onBehalfOf?: number;
};

export type OvertimeRequestDto = {
  id: number;
  /** Human-readable code, e.g. OT-2026-0017. */
  requestCode: string;
  employeeId: number;
  workDate: string;
  /** Which bucket the submitter requested — drives which minutes are paid. */
  type: OvertimeType;
  /** The same bucket as the server actually spells it; prefer this, fall back to `type`. */
  overtimeType?: OvertimeType;
  /** Null only on requests filed before the field existed. */
  factoryCode: FactoryCode | null;
  reason: string | null;
  /** Null for self-applied; shared across rows from one bulk submit. */
  bulkBatchId: string | null;
  status: OvertimeStatus;
  currentStage: CurrentStageDto | null;
  steps: ApprovalStepDto[];
  actions: ApprovalAction[];
  createdAt: string;
};

export type BulkOvertimeRequest = {
  workDate: string;
  /**
   * Which bucket to pay — applied to every row in the batch. Required.
   * Spelled `overtimeType` on the wire, same as POST /overtime; sending
   * `type` answers 400 VALIDATION_FAILED (`overtimeType: must not be null`).
   */
  overtimeType: OvertimeType;
  /** Factory / unit — applied to every row in the batch. Required. */
  factoryCode: FactoryCode;
  reason?: string;
  employeeIds: number[];
};

export type BulkOvertimeResponse = {
  bulkBatchId: string;
  created: OvertimeRequestDto[];
  /** Employee ids silently dropped for having no punch on the date. */
  skipped: number[];
};

/**
 * GET /overtime/department/attendance — one page of the caller's own
 * department on a date, for bulk OT from Team requests. Rows match the
 * manager's `/bff/attendance/team/today`.
 */
export type DepartmentAttendanceRow = TeamAttendanceTodayRow;

export type DepartmentAttendanceParams = {
  /** YYYY-MM-DD. Required. */
  date: string;
  /** One section of the caller's department. */
  sectionId?: number;
  /** Case-insensitive substring of the full name or employee code. */
  search?: string;
  /** One attendance status. Employees with no record that day
   *  (PENDING_PUNCH) only appear when this is unset. */
  status?: AttendanceStatus;
  /** 0-based. */
  page?: number;
  size?: number;
};

/* ----------------------------- Leaves --------------------------------- */

export type CreateLeaveRequest = {
  leaveTypeId: number;
  fromDate: string;
  toDate: string;
  halfDay?: boolean;
  halfDayPart?: HalfDayPart;
  reason?: string;
  /**
   * Submit on behalf of a direct report (manager) or any employee
   * (`attendance.approval.override`). When set, the MANAGER stage is
   * auto-approved; for an override holder, the whole chain is auto-approved.
   */
  onBehalfOf?: number;
};

export type LeaveRequestDto = {
  id: number;
  /** Human-readable code, e.g. LR-2026-0042. */
  requestCode: string;
  employeeId: number;
  leaveTypeId: number;
  /** Leave type code, denormalized (ANNUAL | SICK | CASUAL | …). */
  leaveTypeCode: string;
  fromDate: string;
  toDate: string;
  halfDay: boolean;
  halfDayPart: HalfDayPart | null;
  /** Working-day count, half-day adjusted; computed at submit and locked. */
  days: number;
  reason: string | null;
  status: LeaveStatus;
  currentStage: CurrentStageDto | null;
  steps: ApprovalStepDto[];
  actions: ApprovalAction[];
  createdAt: string;
};

export type LeaveDayDto = {
  id: number;
  workDate: string;
  status: LeaveDayStatus;
  decidedBy: number | null;
  decidedAt: string | null;
  decidedAtStage: number | null;
  rejectionReason: string | null;
};

export type LeaveTypeDto = {
  id: number;
  code: string;
  name: string;
  paid: boolean;
  defaultPerYear: number;
  cycleAnchor: LeaveCycleAnchor;
  /** Prorate the first cycle for a mid-cycle joiner. FISCAL types only. */
  prorateOnEntry: boolean;
  /** Encash unused balance when the cycle closes at renewal. */
  encashable: boolean;
  carryForwardMax: number;
  requiresApproval: boolean;
  active: boolean;
  /** If false, the dashboard balance card hides this type. Apply-leave drawer still lists it. */
  showOnDashboard: boolean;
};

/**
 * Admin create — `code` is normalized to upper-case server-side and is
 * immutable post-create (denormalized into balance rows / leave-request views /
 * audit events). Optional booleans default true at the server when omitted.
 */
export type CreateLeaveTypeRequest = {
  code: string;
  name: string;
  defaultPerYear: number;
  cycleAnchor: LeaveCycleAnchor;
  prorateOnEntry?: boolean;
  encashable?: boolean;
  paid?: boolean;
  carryForwardMax?: number;
  requiresApproval?: boolean;
  showOnDashboard?: boolean;
};

/** Admin partial update. `code` is intentionally absent — see CreateLeaveTypeRequest. */
export type UpdateLeaveTypeRequest = {
  name?: string;
  paid?: boolean;
  defaultPerYear?: number;
  cycleAnchor?: LeaveCycleAnchor;
  prorateOnEntry?: boolean;
  encashable?: boolean;
  carryForwardMax?: number;
  requiresApproval?: boolean;
  active?: boolean;
  showOnDashboard?: boolean;
};

export type LeaveBalanceDto = {
  id: number;
  employeeId: number;
  leaveTypeId: number;
  /** Leave type code, denormalized for the FE. */
  leaveTypeCode: string;
  /** Calendar year the cycle starts in — a grouping label, not the cycle window. */
  year: number;
  /** Inclusive first day of this entitlement cycle (YYYY-MM-DD). */
  cycleStart: string;
  /** Inclusive last day of this entitlement cycle (YYYY-MM-DD). */
  cycleEnd: string;
  allocated: number;
  used: number;
  pending: number;
  carried: number;
  /** Server-computed: allocated + carried − used − pending. */
  available: number;
};

export type PerDayDecision = {
  workDate: string;
  action: PerDayAction;
  reason?: string;
};

export type PerDayDecideRequest = {
  decisions: PerDayDecision[];
};

/**
 * Dry-run response for the apply-leave drawer. Same business rules submit
 * enforces (Sundays + holidays skipped, half-day collapses to 0.5, balance
 * projection uses current balances). `warnings[]` are soft hints — submit
 * may still succeed even when these are set.
 */
export type LeavePreviewDto = {
  workingDays: number;
  sundaysSkipped: number;
  holidaysSkipped: number;
  /** Caller's available balance for the leave type after the request lands. Null when the leave type isn't balance-tracked. */
  balanceAfter: number | null;
  /** Soft warnings (e.g. NEGATIVE_BALANCE, CROSSES_FY). */
  warnings: string[];
};

/* ------------------------- Short leaves -------------------------------- */

/**
 * Sub-day leave: leave work between (startTime, endTime) on a given date
 * with HR approval. Distinct from regularization (forgot-to-punch) and
 * from full/half-day leave_requests.
 */
export type CreateShortLeaveRequest = {
  leaveDate: string;
  /** "HH:mm" */
  startTime: string;
  /** "HH:mm" — must be strictly greater than startTime, no midnight wrap. */
  endTime: string;
  reason?: string;
  /**
   * File for someone else: a manager for a direct report (MANAGER stage
   * auto-approved), HR with `attendance.approval.override` for anyone, or a
   * `team.request.generation.shortLeave` holder for their own department.
   * Anyone else gets 403 ON_BEHALF_NOT_AUTHORIZED.
   */
  onBehalfOf?: number;
};

export type ShortLeaveRequestDto = {
  id: number;
  /** Human-readable code, e.g. SL-2026-0017. */
  requestCode: string;
  employeeId: number;
  leaveDate: string;
  startTime: string;
  endTime: string;
  /** Derived from (startTime, endTime). */
  totalMinutes: number;
  reason: string | null;
  status: ShortLeaveStatus;
  currentStage: CurrentStageDto | null;
  steps: ApprovalStepDto[];
  actions: ApprovalAction[];
  createdAt: string;
};

/* ---------------------------- Holidays -------------------------------- */

export type HolidayDto = {
  id: number;
  holidayDate: string;
  name: string;
  type: HolidayType;
  description: string | null;
};

export type CreateHolidayRequest = {
  holidayDate: string;
  name: string;
  type: HolidayType;
  description?: string;
};

export type UpdateHolidayRequest = {
  holidayDate?: string;
  name?: string;
  type?: HolidayType;
  description?: string;
};

/* ------------------------- Shift overrides ---------------------------- */

export type ShiftOverrideDto = {
  id: number;
  employeeId: number;
  fromDate: string;
  toDate: string;
  shiftStartTime: string;
  shiftEndTime: string;
  /** "HH:mm" — break window start. */
  shiftBreakStartTime: string;
  /** "HH:mm" — break window end. */
  shiftBreakEndTime: string;
  /** Derived from break window. Read-only on the wire. */
  shiftBreakMinutes: number;
  shiftGraceMinutes: number;
  reason: string | null;
};

export type CreateShiftOverrideRequest = {
  employeeIds: number[];
  fromDate: string;
  toDate: string;
  shiftStartTime: string;
  shiftEndTime: string;
  shiftBreakStartTime: string;
  shiftBreakEndTime: string;
  shiftGraceMinutes: number;
  reason?: string;
};

export type UpdateShiftOverrideRequest = {
  shiftStartTime?: string;
  shiftEndTime?: string;
  shiftBreakStartTime?: string;
  shiftBreakEndTime?: string;
  shiftGraceMinutes?: number;
  fromDate?: string;
  toDate?: string;
  reason?: string;
};

/* ----------------------------- Punches -------------------------------- */

export type PunchDto = {
  id: number;
  employeeId: number;
  punchedAt: string;
  workDate: string;
  source: PunchSource;
  deviceId: string | null;
  reason: string | null;
  /** IN / OUT when an operator pinned this punch as the day's check-in /
   *  check-out; null (or absent on older API builds) when derived by
   *  position — earliest in, latest out. */
  direction?: PunchDirection | null;
};

export type PunchDirection = 'IN' | 'OUT';

export type PunchRequest = {
  /** Punch instant. Server stamps now() when null. */
  at?: string;
};

export type OperatorPunchRequest = {
  employeeId: number;
  punchedAt: string;
  reason: string;
  /** Single-punch endpoint only: pins the punch as the day's check-in (IN)
   *  or check-out (OUT) whatever other punches exist. Omit to derive by
   *  position. */
  direction?: PunchDirection;
};

/**
 * Auto attendance over a date range — `POST /attendance/operator/auto-attendance`.
 * `inTime` / `outTime` are `HH:mm`; leave them out to use each day's own
 * shift start / end. Times are placed on the calendar date the shift window
 * implies — an 18:00–02:00 shift's out is the next morning.
 */
export type AutoAttendanceRequest = {
  employeeId: number;
  from: string;
  to: string;
  inTime?: string;
  outTime?: string;
  /** Also fill Sundays and holidays (skipped by default). */
  includeOffDays?: boolean;
  /** Also fill days that already have punches (skipped by default). */
  replaceExisting?: boolean;
  remarks?: string;
};

/** Why a day was left alone. */
export type AutoAttendanceSkipReason =
  | 'WEEKEND'
  | 'HOLIDAY'
  | 'LEAVE'
  | 'LOCKED'
  | 'HAS_PUNCHES'
  /** No times given and no shift that day. */
  | 'NO_SHIFT'
  /** A time outside the day's shift window, or out not after in. */
  | 'OUTSIDE_SHIFT_WINDOW';

export type AutoAttendanceResponse = {
  /** Days filled, with the instants written — check-out can be the next day. */
  applied: { date: string; checkIn: string; checkOut: string }[];
  skipped: { date: string; reason: AutoAttendanceSkipReason }[];
  /** What landed on each applied record's notes and punches' reason. */
  narration: string;
};

/**
 * End-of-shift / paper-form bulk operator-punch insert. The whole batch
 * runs in one transaction — any single row failing rolls back the lot.
 * Capped server-side at 100 rows.
 */
export type BulkOperatorPunchRequest = {
  punches: OperatorPunchRequest[];
};

/**
 * Operator amendment to an existing punch. Both fields mandatory — the
 * server records the before/after on the audit timeline.
 */
export type AmendPunchRequest = {
  punchedAt: string;
  reason: string;
};

/**
 * Hard-delete envelope. Reason becomes the audit trail.
 */
export type DeletePunchRequest = {
  reason: string;
};

/**
 * Lock state for a payroll-period (year + month) in attendance. Once locked,
 * punch ingest, regularizations, and operator amends for the period are
 * refused — payroll uses this as a precondition to compute.
 */
export type AttendancePeriodLock = {
  periodYear: number;
  periodMonth: number;
  lockedAt: string;
  lockedBy: string;
};

/* --------------------- Device snapshots / sync tasks ------------------ */

export type DeviceSnapshotStatus = 'PENDING' | 'COMPLETED' | 'FAILED';

export type DeviceSnapshotSummaryDto = {
  id: number;
  deviceId: number;
  syncTaskId: number | null;
  status: DeviceSnapshotStatus;
  requestedBy: string;
  requestedAt: string;
  completedAt: string | null;
  userCount: number | null;
  recordCount: number | null;
  error: string | null;
};

export type DeviceSnapshotDto = DeviceSnapshotSummaryDto & {
  /** Captured device metadata. Populated only when status is COMPLETED. */
  deviceInfo: unknown;
  /** Captured device users payload. Populated only when status is COMPLETED. */
  users: unknown;
  /** Captured attendance payload. Populated only when status is COMPLETED. */
  attendance: unknown;
};

/**
 * Outcome of adopting a COMPLETED snapshot's user dump into the device's
 * enrolled-users bridge. Each device user is matched to an employee by code;
 * rows are created or refreshed, and users with no matching employee are
 * skipped and returned for reconciliation.
 */
export type DeviceUserAdoptionDto = {
  deviceId: number;
  snapshotId: number;
  totalUsers: number;
  created: number;
  updated: number;
  skipped: number;
  /** Device user_ids (as reported, before zero-strip) that matched no employee. */
  unmatchedUserIds: string[];
};

export type DeviceSyncTaskType =
  | 'UPSERT_USER'
  | 'DELETE_USER'
  | 'PUSH_TEMPLATE'
  | 'SNAPSHOT_DEVICE';

export type DeviceSyncTaskStatus = 'PENDING' | 'IN_PROGRESS' | 'DONE' | 'FAILED';

export type DeviceSyncTaskDto = {
  id: number;
  deviceId: number;
  employeeId: number | null;
  taskType: DeviceSyncTaskType;
  payload: unknown;
  status: DeviceSyncTaskStatus;
  attempts: number;
  scheduledFor: string;
  claimedAt: string | null;
  lastError: string | null;
};

export type DeviceUserDto = {
  id: number;
  deviceId: number;
  employeeId: number;
  privilege: number;
  hasFingerprint: boolean;
  hasFace: boolean;
  syncedAt: string | null;
};

export type ReconciliationCategory =
  | 'MATCHED_ACTIVE'
  | 'MATCHED_INACTIVE'
  | 'UNMATCHED';

/**
 * One device-enrolled user reconciled against the employee mirror. Category
 * tells HR whether to keep (matched-active) or un-enroll (matched-inactive /
 * unmatched).
 */
export type DeviceUserReconciliationDto = {
  /** Raw user_id as stored on the device (before zero-strip). Pass back verbatim to un-enroll UNMATCHED rows. */
  deviceUserId: string;
  /** employee_code matched (device user_id, zeros stripped); null when blank. */
  employeeCode: string | null;
  /** Matched employee id; null when UNMATCHED. */
  employeeId: number | null;
  /** Employee full name when matched. */
  name: string | null;
  /** Employment status from the mirror when matched (ACTIVE / TERMINATED). */
  employmentStatus: string | null;
  category: ReconciliationCategory;
  privilege: number;
};

/** Per-category counts for a COMPLETED snapshot's device users. */
export type DeviceReconciliationSummaryDto = {
  deviceId: number;
  snapshotId: number;
  total: number;
  /** Matched to an ACTIVE employee — keep. */
  matchedActive: number;
  /** Matched to a non-ACTIVE (e.g. TERMINATED) employee — un-enroll candidate. */
  matchedInactive: number;
  /** No matching employee_code — stray enrollment, un-enroll candidate. */
  unmatched: number;
};

/** Generated Excel reconciliation report; `downloadUrl` is a short-lived signed link the FE opens directly. */
export type ReconciliationExportDto = {
  downloadUrl: string;
  objectKey: string;
};

export type UnenrollDeviceUsersRequest = {
  /** Matched employees to un-enroll — bridge row dropped + DELETE_USER (by employee_code) queued. */
  employeeIds?: number[];
  /** Raw device user_ids with no employee record (UNMATCHED rows) — DELETE_USER by raw id; no bridge row. */
  deviceUserIds?: string[];
};

/** Outcome of a bulk un-enroll. DELETE_USER tasks drain on the next poller cycle. */
export type UnenrollResultDto = {
  queued: number;
  droppedBridges: number;
  skipped: number;
};

export type BiometricPunch = {
  employeeCode: string;
  punchedAt: string;
  deviceId: number;
};

export type BiometricBatchRequest = {
  punches: BiometricPunch[];
};

/* --------------------------- Attendance ------------------------------- */

export type AttendanceRecordDto = {
  id: number;
  employeeId: number;
  workDate: string;
  checkIn: string | null;
  checkOut: string | null;
  effectiveShift: number | null;
  /** Snapshot — does NOT change when the shift catalog or assignment changes. */
  shiftStartTime: string | null;
  shiftEndTime: string | null;
  /** workDate + shiftStartTime (bare LocalDateTime). */
  shiftStartDateTime: string | null;
  /** workDate + shiftEndTime; next day when the shift crosses midnight. */
  shiftEndDateTime: string | null;
  shiftBreakStartTime: string | null;
  shiftBreakEndTime: string | null;
  shiftBreakMinutes: number | null;
  shiftGraceMinutes: number | null;
  status: AttendanceStatus;
  /** Paid-leave flag. Populated when status == LEAVE from the leave type's `paid` setting. */
  paid: boolean;
  lateMinutes: number;
  earlyMinutes: number;
  workedMinutes: number;
  /** ≤120 min beyond shift end on a working day, only if APPROVED OT exists. */
  otNormalMinutes: number;
  /** Beyond the first 120 min of OT on a working day. */
  otExtraMinutes: number;
  /** All worked_minutes on Sunday / holiday, if APPROVED OT exists. */
  otSpecialMinutes: number;
  source: PunchSource | null;
  notes: string | null;
};

/** `/attendance/employee/{id}/today` projection — always 200. `record` is null
 *  when `state === 'NOT_PUNCHED'` (no punches + no nightly close yet). */
export type EmployeeTodayState = 'PRESENT' | 'NOT_PUNCHED';
export type EmployeeTodayDto = {
  state: EmployeeTodayState;
  record: AttendanceRecordDto | null;
};

/**
 * `/attendance/org/not-checked-in` row — active employees whose
 * `shiftStartTime + graceMinutes` has elapsed today with no check-in punch.
 * Self-clears when a punch creates the PRESENT record; settled ABSENT stays
 * end-of-day. Empty on Sunday/holiday. Excludes approved-leave today.
 * Ordered most-overdue first.
 */
export type NotCheckedInDto = {
  employeeId: number;
  employeeCode: string;
  fullName: string;
  shiftId: number;
  /** "HH:mm[:ss]" — assigned shift start (after any override). */
  shiftStartTime: string;
  graceMinutes: number;
  /** "HH:mm:ss" — local time of `shiftStartTime + graceMinutes`. */
  cutoff: string;
  minutesOverdue: number;
};

/* --------------------------- Delegations ------------------------------ */

/**
 * What a delegation hands over. `APPROVE` = act on the delegator's approval
 * steps (their queue joins your inbox). `SUBMIT` = file requests for the
 * delegator's people, carrying the delegator's reach — their department and
 * direct reports — not your own.
 */
export type DelegationGrant = 'APPROVE' | 'SUBMIT';

export const ALL_DELEGATION_GRANTS: DelegationGrant[] = ['APPROVE', 'SUBMIT'];

export const DELEGATION_GRANT_LABEL: Record<DelegationGrant, string> = {
  APPROVE: 'Approvals',
  SUBMIT: 'Team request generation',
};

export type DelegationDto = {
  id: number;
  delegatorId: number;
  delegateId: number;
  /** Subset of {LEAVE, OVERTIME, REGULARIZATION}; empty/null = all three. */
  requestTypes: RequestType[] | null;
  /**
   * Always populated on read — rows written before grants existed come back
   * as `['APPROVE']`, which is all they ever meant.
   */
  grants: DelegationGrant[];
  fromDate: string;
  /** Null = open-ended (until revoked). */
  toDate: string | null;
  reason: string | null;
  status: DelegationStatus;
  revokedAt: string | null;
  revokedBy: number | null;
};

/* ----------------------------- Devices -------------------------------- */

/**
 * Catalog row for a biometric / kiosk device. `commPasswordSet` is a
 * boolean stand-in for the write-only ZK comm password — set via
 * `POST /devices/{id}/comm-password`. `status` + `lastSeenAt` come from
 * the poller's heartbeat.
 */
export type AttendanceDeviceDto = {
  id: number;
  /** Stable, immutable code. */
  code: string;
  name: string;
  location: string | null;
  /** IP address the poller dials. */
  deviceIp: string | null;
  /** ZK protocol TCP port; default 4370. */
  commPort: number | null;
  /** True when a non-zero ZK comm password is configured. */
  commPasswordSet: boolean;
  /** True for the single device HR uses to enroll fingerprints / faces. */
  enrollment: boolean;
  /** Physical role. Absent on older server builds — treat as ATTENDANCE. */
  deviceType?: AttendanceDeviceType;
  status: AttendanceDeviceStatus;
  lastSeenAt: string | null;
  /**
   * High-water-mark for biometric ingest. Null when no punch has been
   * received yet or after `POST /devices/{id}/reset-hwm`. The poller
   * filters its ZK pull to records strictly after this instant.
   */
  lastPunchedAt: string | null;
  /** Number of unmatched-card punches waiting in the replay queue. */
  unmatchedCount: number;
  active: boolean;
};

export type CreateAttendanceDeviceRequest = {
  code: string;
  name: string;
  location?: string;
  deviceIp?: string;
  /** Default 4370 server-side. */
  commPort?: number;
  enrollment?: boolean;
  /** Default ATTENDANCE server-side. */
  deviceType?: AttendanceDeviceType;
};

/**
 * Partial update — null fields stay unchanged. `code` is intentionally
 * absent (immutable). The ZK comm password lives on a dedicated endpoint.
 */
export type UpdateAttendanceDeviceRequest = {
  name?: string;
  location?: string;
  deviceIp?: string;
  commPort?: number;
  enrollment?: boolean;
  /** Reclassify the unit — e.g. flip an ATTENDANCE reader to CANTEEN. */
  deviceType?: AttendanceDeviceType;
  active?: boolean;
};

/** Numeric ZK comm password. Send 0 to clear. */
export type SetDeviceCommPasswordRequest = {
  commPassword: number;
};

export type CreateDelegationRequest = {
  delegateId: number;
  requestTypes?: RequestType[];
  /** Omitted or empty = APPROVE only, matching pre-grants behaviour. */
  grants?: DelegationGrant[];
  fromDate: string;
  toDate?: string;
  reason?: string;
};

export type CreateDelegationOnBehalfRequest = {
  delegatorId: number;
  delegateId: number;
  requestTypes?: RequestType[];
  /** Omitted or empty = APPROVE only, matching pre-grants behaviour. */
  grants?: DelegationGrant[];
  fromDate: string;
  toDate?: string;
  reason: string;
};

export type UpdateDelegationRequest = {
  toDate?: string | null;
  reason?: string;
};

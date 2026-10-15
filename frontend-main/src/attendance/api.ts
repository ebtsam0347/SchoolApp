// Typed wrappers for attendance-service. Mirrors
// `docs/services/attendance.openapi.json`. Routed through the gateway via
// `src/lib/http.ts` (bearer + idempotency + 401-refresh-retry).

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';
import type {
  AmendPunchRequest,
  ApproveRequest,
  AssignShiftRequest,
  AttendanceDeviceDto,
  AttendancePeriodLock,
  AttendanceRecordDto,
  DeviceSnapshotDto,
  DeviceSnapshotStatus,
  DeviceSnapshotSummaryDto,
  DeviceReconciliationSummaryDto,
  DeviceSyncTaskDto,
  DeviceSyncTaskStatus,
  DeviceUserAdoptionDto,
  DeviceUserDto,
  DeviceUserReconciliationDto,
  ReconciliationCategory,
  ReconciliationExportDto,
  UnenrollDeviceUsersRequest,
  UnenrollResultDto,
  BiometricBatchRequest,
  AutoAttendanceRequest,
  AutoAttendanceResponse,
  BulkOperatorPunchRequest,
  BulkOvertimeRequest,
  BulkOvertimeResponse,
  DepartmentAttendanceParams,
  DepartmentAttendanceRow,
  CreateAttendanceDeviceRequest,
  CreateDelegationOnBehalfRequest,
  CreateDelegationRequest,
  CreateHolidayRequest,
  CreateLeaveRequest,
  CreateLeaveTypeRequest,
  CreateOvertimeRequest,
  CreateShiftOverrideRequest,
  CreateShiftRequest,
  DelegationStatus,
  DelegationDto,
  DeletePunchRequest,
  EmployeeShiftDto,
  EmployeeTodayDto,
  HolidayDto,
  UpdateHolidayRequest,
  NotCheckedInDto,
  LeaveBalanceDto,
  LeaveDayDto,
  LeavePreviewDto,
  LeaveRequestDto,
  LeaveStatus,
  LeaveTypeDto,
  OperatorPunchRequest,
  OvertimeRequestDto,
  OvertimeStatus,
  PageResponse,
  PerDayDecideRequest,
  PunchDto,
  PunchRequest,
  RegularizationRequest,
  RegularizationRequestDto,
  RegularizationStatus,
  RejectRequest,
  SetDeviceCommPasswordRequest,
  ShiftDto,
  ShiftOverrideDto,
  CreateShortLeaveRequest,
  ShortLeaveRequestDto,
  ShortLeaveStatus,
  UpdateAttendanceDeviceRequest,
  UpdateDelegationRequest,
  UpdateLeaveTypeRequest,
  UpdateShiftOverrideRequest,
} from './types';
import type {
  ApprovalInboxLeaveRow,
  ApprovalInboxOvertimeRow,
  ApprovalInboxRegularizationRow,
  ApprovalInboxShortLeaveRow,
} from './bff-api';

/* ------------------------------ Shifts -------------------------------- */

export const shiftsApi = {
  list: (signal?: AbortSignal) => http.get<ShiftDto[]>('/shifts', { signal }),
  create: (body: CreateShiftRequest) => http.post<ShiftDto>('/shifts', body),
  assign: (employeeId: number, body: AssignShiftRequest) =>
    http.post<void>(`/shifts/employees/${employeeId}`, body),
  /** Newest-first by `effectiveFrom`. The first row covering today is active. */
  forEmployee: (employeeId: number, signal?: AbortSignal) =>
    http.get<EmployeeShiftDto[]>(`/shifts/employees/${employeeId}`, { signal }),
};

/* ----------------------------- Devices -------------------------------- */

export type ListDevicesParams = {
  /** Pass true to include deactivated rows in the HR-admin catalog UI. */
  includeInactive?: boolean;
};

export const devicesApi = {
  /** Active only by default. Pass `includeInactive: true` for the admin catalog. */
  list: (params: ListDevicesParams = {}, signal?: AbortSignal) =>
    http.get<AttendanceDeviceDto[]>(
      `/devices${qs({ includeInactive: params.includeInactive })}`,
      { signal },
    ),
  get: (id: number, signal?: AbortSignal) =>
    http.get<AttendanceDeviceDto>(`/devices/${id}`, { signal }),
  create: (body: CreateAttendanceDeviceRequest) =>
    http.post<AttendanceDeviceDto>('/devices', body),
  update: (id: number, body: UpdateAttendanceDeviceRequest) =>
    http.patch<AttendanceDeviceDto>(`/devices/${id}`, body),
  /** Soft delete — flips `active=false`. Re-enable via update({ active: true }). */
  deactivate: (id: number) => http.del<void>(`/devices/${id}`),
  /** Set the ZK comm password. Send 0 to clear. */
  setCommPassword: (id: number, body: SetDeviceCommPasswordRequest) =>
    http.post<void>(`/devices/${id}/comm-password`, body),
  /**
   * Null `lastPunchedAt` so the next poller cycle pulls every record
   * currently on the device. Use after a device clock reset or to
   * re-ingest records whose bridge rows landed late.
   */
  resetHwm: (id: number) => http.post<void>(`/devices/${id}/reset-hwm`),
  /** List captured device snapshots (lightweight summary). */
  snapshots: (
    deviceId: number,
    params: { status?: DeviceSnapshotStatus } = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<DeviceSnapshotSummaryDto>>(
      `/devices/${deviceId}/snapshots${qs({ status: params.status })}`,
      { signal },
    ),
  /** Full snapshot row including the captured deviceInfo / users / attendance JSON. */
  snapshot: (deviceId: number, snapshotId: number, signal?: AbortSignal) =>
    http.get<DeviceSnapshotDto>(
      `/devices/${deviceId}/snapshots/${snapshotId}`,
      { signal },
    ),
  /** Queue a SNAPSHOT_DEVICE task. The poller picks it up on its next cycle. */
  requestSnapshot: (deviceId: number) =>
    http.post<DeviceSnapshotSummaryDto>(`/devices/${deviceId}/snapshots`),
  /**
   * Reconcile a COMPLETED snapshot's user dump into the device's enrolled-users
   * bridge — the cutover path for devices that already carry enrollments. Each
   * device user is matched to an employee by code; bridge rows are created or
   * refreshed and unmatched users are returned. Idempotent. Requires
   * `attendance.devices.admin`.
   */
  adoptSnapshotUsers: (deviceId: number, snapshotId: number) =>
    http.post<DeviceUserAdoptionDto>(
      `/devices/${deviceId}/snapshots/${snapshotId}/adopt-users`,
    ),
  /** Per-category counts for a COMPLETED snapshot's device users. */
  reconciliationSummary: (
    deviceId: number,
    snapshotId: number,
    signal?: AbortSignal,
  ) =>
    http.get<DeviceReconciliationSummaryDto>(
      `/devices/${deviceId}/snapshots/${snapshotId}/reconciliation/summary`,
      { signal },
    ),
  /**
   * Paginated reconciliation rows for a COMPLETED snapshot. `category` filters
   * (omit for all). springdoc only annotates `category`, but `page`/`size`
   * (default 100, max 1000) are honored — backend annotation gap to raise.
   */
  reconciliation: (
    deviceId: number,
    snapshotId: number,
    params: {
      category?: ReconciliationCategory;
      page?: number;
      size?: number;
    } = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<DeviceUserReconciliationDto>>(
      `/devices/${deviceId}/snapshots/${snapshotId}/reconciliation${qs({ ...params })}`,
      { signal },
    ),
  /** Render the reconciliation to .xlsx in object storage; returns a short-lived signed download URL. */
  exportReconciliation: (deviceId: number, snapshotId: number) =>
    http.post<ReconciliationExportDto>(
      `/devices/${deviceId}/snapshots/${snapshotId}/reconciliation/export`,
    ),
  /** Sync tasks (UPSERT_USER / DELETE_USER / PUSH_TEMPLATE / SNAPSHOT_DEVICE). */
  syncTasks: (
    deviceId: number,
    params: { status?: DeviceSyncTaskStatus } = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<DeviceSyncTaskDto>>(
      `/devices/${deviceId}/sync-tasks${qs({ status: params.status })}`,
      { signal },
    ),
  /** Reset attempts on a failed task so the poller re-claims it. */
  retrySyncTask: (deviceId: number, taskId: number) =>
    http.post<void>(`/devices/${deviceId}/sync-tasks/${taskId}/retry`),
  /** Employees enrolled on the device. */
  deviceUsers: (deviceId: number, signal?: AbortSignal) =>
    http.get<PageResponse<DeviceUserDto>>(`/devices/${deviceId}/users`, {
      signal,
    }),
  /** Un-enroll an employee — schedules a DELETE_USER sync task. */
  unenrollDeviceUser: (deviceId: number, employeeId: number) =>
    http.del<void>(`/devices/${deviceId}/users/${employeeId}`),
  /**
   * Bulk un-enroll: matched employees (by id, bridge row dropped) + unmatched
   * device user_ids (raw, no bridge row). Queues a DELETE_USER per entry.
   */
  bulkUnenroll: (deviceId: number, body: UnenrollDeviceUsersRequest) =>
    http.post<UnenrollResultDto>(`/devices/${deviceId}/users/unenroll`, body),
  /**
   * Capture an employee's enrolled fingerprints off this device into the
   * central store — store-only, does NOT push to any device. Run on the
   * enrollment device right after the employee scans. Queues a CAPTURE_TEMPLATE
   * task (track it under sync-tasks). Employee must already be on this device.
   * Requires `attendance.devices.admin`.
   */
  captureTemplate: (deviceId: number, employeeId: number) =>
    http.post<DeviceSyncTaskDto>(
      `/devices/${deviceId}/users/${employeeId}/capture-template`,
    ),
  /**
   * Provision an employee's stored fingerprints onto this device — queues one
   * PUSH_TEMPLATE per stored print. Reusable any time (e.g. a new gate) with no
   * re-scan. Target must be active, the employee already enrolled here, and
   * they must have captured prints first (else 422). FACE templates skipped.
   * Returns the queued task(s). Requires `attendance.devices.admin`.
   */
  pushTemplates: (deviceId: number, employeeId: number) =>
    http.post<DeviceSyncTaskDto[]>(
      `/devices/${deviceId}/users/${employeeId}/push-templates`,
    ),
};

/* ----------------------------- Holidays ------------------------------- */

export const holidaysApi = {
  list: (year?: number, signal?: AbortSignal) =>
    http.get<HolidayDto[]>(`/holidays${qs({ year })}`, { signal }),
  create: (body: CreateHolidayRequest) => http.post<HolidayDto>('/holidays', body),
  update: (id: number, body: UpdateHolidayRequest) =>
    http.patch<HolidayDto>(`/holidays/${id}`, body),
  delete: (id: number) => http.del<void>(`/holidays/${id}`),
};

/* ---------------------------- Attendance ------------------------------ */

export type AttendanceRangeParams = { from: string; to: string };

/** Query for the `…/filed-by-me` lists. */
export type FiledByMeParams<S extends string> = {
  status?: S;
  /** Filed on or after (YYYY-MM-DD, inclusive). */
  filedFrom?: string;
  /** Filed on or before (YYYY-MM-DD, inclusive). */
  filedTo?: string;
  page?: number;
  size?: number;
};

/** Query for the `…/department` lists: the filed-by-me filters plus one
 *  colleague (an id outside the caller's department answers empty). */
export type DepartmentRequestParams<S extends string> = FiledByMeParams<S> & {
  employeeId?: number;
};

export const attendanceApi = {
  /** My monthly attendance (YYYY-MM, defaults to current month). */
  me: (month?: string, signal?: AbortSignal) =>
    http.get<AttendanceRecordDto[]>(`/attendance/me${qs({ month })}`, { signal }),
  myPunches: (date: string, signal?: AbortSignal) =>
    http.get<PunchDto[]>(`/attendance/me/punches${qs({ date })}`, { signal }),
  forEmployee: (employeeId: number, params: AttendanceRangeParams, signal?: AbortSignal) =>
    http.get<AttendanceRecordDto[]>(
      `/attendance/employee/${employeeId}${qs({ ...params })}`,
      { signal },
    ),
  /** Several employees' records on one date (cap 200 ids) — no row for
   *  someone with nothing on file that day. `attendance.read`. */
  employeesOnDate: (ids: number[], date: string, signal?: AbortSignal) =>
    http.get<AttendanceRecordDto[]>(`/attendance/employees${qs({ ids, date })}`, { signal }),
  /** Manager spot-check. Always 200; `state === 'NOT_PUNCHED'` + `record === null`
   *  when there's nothing on file yet for today. */
  todayForEmployee: (employeeId: number, signal?: AbortSignal) =>
    http.get<EmployeeTodayDto>(`/attendance/employee/${employeeId}/today`, { signal }),
  punchesForEmployee: (
    employeeId: number,
    params: AttendanceRangeParams,
    signal?: AbortSignal,
  ) =>
    http.get<PunchDto[]>(
      `/attendance/employee/${employeeId}/punches${qs({ ...params })}`,
      { signal },
    ),

  /** ESS self-punch. `at` optional — server stamps now() when null. */
  punch: (body: PunchRequest = {}) => http.post<PunchDto>('/attendance/punch', body),
  /** System / HR batch ingest from biometric devices. */
  ingest: (body: BiometricBatchRequest) =>
    http.post<PunchDto[]>('/attendance/punches/ingest', body),
  /** Operator-added punch off paper proof. */
  operatorPunch: (body: OperatorPunchRequest) =>
    http.post<PunchDto>('/attendance/operator/punch', body),
  /**
   * End-of-shift / paper-form bulk operator-punch insert. Whole batch is one
   * transaction — any failure rolls back the lot. Server cap is 100 rows.
   * Requires `attendance.operator`.
   */
  operatorPunchBulk: (body: BulkOperatorPunchRequest) =>
    http.post<PunchDto[]>('/attendance/operator/punches/bulk', body),
  /**
   * Auto attendance: an IN + OUT punch on every day of a range for one
   * employee (max 62 days, one transaction), with the operator narration on
   * each record. Days that shouldn't be filled come back in `skipped[]`.
   * Requires `attendance.operator`.
   */
  autoAttendance: (body: AutoAttendanceRequest) =>
    http.post<AutoAttendanceResponse>('/attendance/operator/auto-attendance', body),
  /**
   * Operator amend of an existing punch. Recomputes the day; if the new
   * instant moves the work-date the prior day is recomputed too. Requires
   * `attendance.operator`.
   */
  amendPunch: (id: number, body: AmendPunchRequest) =>
    http.patch<PunchDto>(`/attendance/punches/${id}`, body),
  /**
   * Operator hard-delete with mandatory audit reason. Requires
   * `attendance.operator`.
   */
  deletePunch: (id: number, body: DeletePunchRequest) =>
    http.post<void>(`/attendance/punches/${id}/delete`, body),
  /** Submit a regularization for a missed check-in / check-out. */
  regularize: (body: RegularizationRequest) =>
    http.post<RegularizationRequestDto>('/regularizations', body),
  /**
   * Regularizations the caller filed on someone else's behalf (`onBehalfOf`).
   * Their own self-submissions are excluded. Each row pairs the request with
   * the target's employee summary (resolved server-side — the callers hold
   * no `employee.read`). Default sort `attendanceDate,desc`; size 20, max 200.
   */
  regularizationsFiledByMe: (
    params: {
      status?: RegularizationStatus;
      from?: string;
      to?: string;
      /** Filed on or after / before (YYYY-MM-DD, inclusive). */
      filedFrom?: string;
      filedTo?: string;
      page?: number;
      size?: number;
    } = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<ApprovalInboxRegularizationRow>>(
      `/regularizations/filed-by-me${qs({ ...params })}`,
      { signal },
    ),
  /**
   * Leaves / short leaves / overtime the caller filed on someone else's
   * behalf — same contract as `regularizationsFiledByMe`: matched on the
   * submitter, self-submissions excluded, each row paired with the target's
   * employee summary. Default sort `id,desc`; size 20, max 200.
   */
  leavesFiledByMe: (params: FiledByMeParams<LeaveStatus> = {}, signal?: AbortSignal) =>
    http.get<PageResponse<ApprovalInboxLeaveRow>>(
      `/leaves/filed-by-me${qs({ ...params })}`,
      { signal },
    ),
  shortLeavesFiledByMe: (
    params: FiledByMeParams<ShortLeaveStatus> = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<ApprovalInboxShortLeaveRow>>(
      `/short-leaves/filed-by-me${qs({ ...params })}`,
      { signal },
    ),
  overtimeFiledByMe: (params: FiledByMeParams<OvertimeStatus> = {}, signal?: AbortSignal) =>
    http.get<PageResponse<ApprovalInboxOvertimeRow>>(
      `/overtime/filed-by-me${qs({ ...params })}`,
      { signal },
    ),
  /**
   * Every request of that type in the caller's own department, whoever filed
   * it — the team-request generator's counterpart of a manager's team view.
   * Gated on the matching `team.request.generation.<verb>` permission (short
   * leaves also admit a manager — the /time/short-leave department list); the
   * department is resolved server-side. Rows pair the request (with its
   * approval steps) with the target's employee summary. Sort `id,desc`.
   */
  leavesDepartment: (
    params: DepartmentRequestParams<LeaveStatus> = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<ApprovalInboxLeaveRow>>(`/leaves/department${qs({ ...params })}`, {
      signal,
    }),
  shortLeavesDepartment: (
    params: DepartmentRequestParams<ShortLeaveStatus> = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<ApprovalInboxShortLeaveRow>>(
      `/short-leaves/department${qs({ ...params })}`,
      { signal },
    ),
  overtimeDepartment: (
    params: DepartmentRequestParams<OvertimeStatus> = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<ApprovalInboxOvertimeRow>>(
      `/overtime/department${qs({ ...params })}`,
      { signal },
    ),
  regularizationsDepartment: (
    params: DepartmentRequestParams<RegularizationStatus> = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<ApprovalInboxRegularizationRow>>(
      `/regularizations/department${qs({ ...params })}`,
      { signal },
    ),
};

/* -------------------------- Org-wide reads ---------------------------- */

export type NotCheckedInParams = {
  /** Employee code, or part of one (case-insensitive). */
  employeeCode?: string;
  page?: number;
  size?: number;
  sort?: string;
};

export const orgAttendanceApi = {
  /**
   * Active employees whose `shiftStart + grace` has elapsed today with no
   * check-in punch. Live triage list; rows self-clear when a punch arrives.
   * Empty on weekend/holiday. Requires `attendance.read`. Paginated
   * (`PageResponse`); ordered most-overdue first.
   */
  notCheckedIn: (params: NotCheckedInParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<NotCheckedInDto>>(
      `/attendance/org/not-checked-in${qs({ ...params })}`,
      { signal },
    ),
};

/* ----------------------- Attendance period locks ---------------------- */

export const attendancePeriodsApi = {
  /** Locked periods in a year, ordered by month. */
  locks: (year: number, signal?: AbortSignal) =>
    http.get<AttendancePeriodLock[]>(
      `/attendance/periods/locks${qs({ year })}`,
      { signal },
    ),
  /** Lock a payroll period (year + month). Idempotent. */
  lock: (year: number, month: number) =>
    http.post<AttendancePeriodLock>(
      `/attendance/periods/lock${qs({ year, month })}`,
    ),
  /** Unlock a payroll period. Use sparingly — the period was locked for a reason. */
  unlock: (year: number, month: number) =>
    http.post<void>(`/attendance/periods/unlock${qs({ year, month })}`),
};

/* ------------------------- Shift overrides ---------------------------- */

export type ListShiftOverridesParams = {
  /** Single date — returns rows where date ∈ [fromDate, toDate]. */
  date?: string;
  employeeId?: number;
  from?: string;
  to?: string;
};

export const shiftOverridesApi = {
  list: (params: ListShiftOverridesParams = {}, signal?: AbortSignal) =>
    http.get<ShiftOverrideDto[]>(
      `/attendance/shift-overrides${qs({ ...params })}`,
      { signal },
    ),
  create: (body: CreateShiftOverrideRequest) =>
    http.post<ShiftOverrideDto[]>('/attendance/shift-overrides', body),
  update: (id: number, body: UpdateShiftOverrideRequest) =>
    http.patch<ShiftOverrideDto>(`/attendance/shift-overrides/${id}`, body),
  delete: (id: number) => http.del<void>(`/attendance/shift-overrides/${id}`),
};

/* ----------------------------- Leaves --------------------------------- */

/**
 * Filters for `GET /leaves/me`. Date filter is overlap-style (any request
 * whose [fromDate, toDate] intersects [from, to]). Sort defaults to
 * `fromDate,desc`; size defaults 20, max 200.
 */
export type MyLeavesParams = {
  status?: LeaveStatus;
  leaveTypeId?: number;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
};

/** Filters for `GET /leaves/team` — those of `/leaves/me`, plus one report. */
export type TeamLeavesParams = MyLeavesParams & { employeeId?: number };

/**
 * A leave in the team / department lists: the request, plus who it's for
 * (flattened from the `{ request, employee }` rows those endpoints return).
 */
export type TeamLeaveRow = LeaveRequestDto & {
  employee?: {
    id: number;
    fullName?: string | null;
    employeeCode?: string | null;
    profilePictureUrl?: string | null;
  } | null;
};

export type PendingApprovalsParams = {
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
};

export const leavesApi = {
  apply: (body: CreateLeaveRequest) => http.post<LeaveRequestDto>('/leaves', body),
  /**
   * Dry-run for the apply drawer. Same business rules submit enforces;
   * `warnings[]` are soft hints (NEGATIVE_BALANCE, CROSSES_FY, …).
   */
  preview: (body: CreateLeaveRequest, signal?: AbortSignal) =>
    http.post<LeavePreviewDto>('/leaves/preview', body, { signal }),
  get: (id: number, signal?: AbortSignal) =>
    http.get<LeaveRequestDto>(`/leaves/${id}`, { signal }),
  days: (id: number, signal?: AbortSignal) =>
    http.get<LeaveDayDto[]>(`/leaves/${id}/days`, { signal }),
  cancel: (id: number) => http.post<LeaveRequestDto>(`/leaves/${id}/cancel`),
  approve: (id: number, body: ApproveRequest = {}) =>
    http.post<LeaveRequestDto>(`/leaves/${id}/approve`, body),
  reject: (id: number, body: RejectRequest) =>
    http.post<LeaveRequestDto>(`/leaves/${id}/reject`, body),
  /** Per-day decide — only allowed at the FINAL stage. */
  decideDays: (id: number, body: PerDayDecideRequest) =>
    http.post<LeaveRequestDto>(`/leaves/${id}/days/decide`, body),

  // Catalog + balances
  /** ESS view defaults to active-only; pass `includeInactive: true` for the admin catalog. */
  types: (
    options: { includeInactive?: boolean; signal?: AbortSignal } = {},
  ) =>
    http.get<LeaveTypeDto[]>(
      `/leaves/types${qs({ includeInactive: options.includeInactive })}`,
      { signal: options.signal },
    ),
  createType: (body: CreateLeaveTypeRequest) =>
    http.post<LeaveTypeDto>('/leaves/types', body),
  updateType: (id: number, body: UpdateLeaveTypeRequest) =>
    http.patch<LeaveTypeDto>(`/leaves/types/${id}`, body),
  /** Soft-deactivate (sets active=false). Re-enable via updateType({ active: true }). */
  deleteType: (id: number) => http.del<void>(`/leaves/types/${id}`),
  myBalances: (year?: number, signal?: AbortSignal) =>
    http.get<LeaveBalanceDto[]>(`/leaves/me/balances${qs({ year })}`, { signal }),

  // Inboxes
  myLeaves: (params: MyLeavesParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<LeaveRequestDto>>(
      `/leaves/me${qs({ ...params })}`,
      { signal },
    ),
  /**
   * Leaves of the caller's direct reports — whoever submitted them (the
   * report, or the manager on their behalf) — each paired with the target's
   * employee summary. Same filters as `myLeaves` (overlap-style dates), plus
   * `employeeId` (one report; anyone else answers empty). Default sort
   * `fromDate,desc`; `attendance.read`. On a build that predates it the
   * route falls through to `/leaves/{id}` and fails (400).
   */
  team: (params: TeamLeavesParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<ApprovalInboxLeaveRow>>(
      `/leaves/team${qs({ ...params })}`,
      { signal },
    ),
  pendingApprovals: (params: PendingApprovalsParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<LeaveRequestDto>>(
      `/leaves/pending-approvals${qs({ ...params })}`,
      { signal },
    ),
};

/* ------------------------- Short leaves -------------------------------- */

/**
 * Filters for `GET /short-leaves/me`. All optional. Default sort
 * `leaveDate,desc`; size defaults 20, max 200.
 */
export type MyShortLeavesParams = {
  status?: ShortLeaveStatus;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
};

/**
 * Filters for `GET /short-leaves/team` — those of `/short-leaves/me`, plus
 * one direct report (anyone else answers empty).
 */
export type TeamShortLeavesParams = MyShortLeavesParams & { employeeId?: number };

/**
 * A `/short-leaves/team` row: the request, plus who it's for. `employee` is
 * part of the requested contract; callers fall back to resolving the name
 * from `employeeId` when the server leaves it out.
 */
export type TeamShortLeaveRow = ShortLeaveRequestDto & {
  employee?: {
    id: number;
    fullName?: string | null;
    employeeCode?: string | null;
    profilePictureUrl?: string | null;
  } | null;
};

export const shortLeavesApi = {
  apply: (body: CreateShortLeaveRequest) =>
    http.post<ShortLeaveRequestDto>('/short-leaves', body),
  get: (id: number, signal?: AbortSignal) =>
    http.get<ShortLeaveRequestDto>(`/short-leaves/${id}`, { signal }),
  /**
   * Submitter-only. PENDING any time, or APPROVED before the leave date.
   */
  cancel: (id: number) => http.post<ShortLeaveRequestDto>(`/short-leaves/${id}/cancel`),
  approve: (id: number, body: ApproveRequest = {}) =>
    http.post<ShortLeaveRequestDto>(`/short-leaves/${id}/approve`, body),
  reject: (id: number, body: RejectRequest) =>
    http.post<ShortLeaveRequestDto>(`/short-leaves/${id}/reject`, body),
  mine: (params: MyShortLeavesParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<ShortLeaveRequestDto>>(
      `/short-leaves/me${qs({ ...params })}`,
      { signal },
    ),
  /**
   * Short leaves of the caller's direct reports — whoever submitted them
   * (the report themselves, or the manager on their behalf).
   *
   * Mirrors `/short-leaves/me` (status / from / to / page / size / sort;
   * default sort `leaveDate,desc`; `attendance.read`), plus `employeeId`. Rows carry no
   * `employee` summary — names resolve from /employees/me/team. On an
   * attendance-service build that predates it, the route falls through to
   * `/short-leaves/{id}` and fails — see `isEndpointMissing`.
   */
  team: (params: TeamShortLeavesParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<TeamShortLeaveRow>>(
      `/short-leaves/team${qs({ ...params })}`,
      { signal },
    ),
  pendingApprovals: (params: PendingApprovalsParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<ShortLeaveRequestDto>>(
      `/short-leaves/pending-approvals${qs({ ...params })}`,
      { signal },
    ),
};

/* ---------------------------- Overtime -------------------------------- */

export type MyOvertimeParams = {
  status?: OvertimeStatus;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
};

/** Filters for `GET /overtime/team` — those of `/overtime/me`, plus one report. */
export type TeamOvertimeParams = MyOvertimeParams & { employeeId?: number };

/**
 * An overtime request in a list table: the request, plus who it's for
 * (team / department lists — flattened from their `{ request, employee }`
 * rows) or the punch-derived minutes (my own list, from the BFF).
 */
export type OvertimeListRow = OvertimeRequestDto & {
  employee?: {
    id: number;
    fullName?: string | null;
    employeeCode?: string | null;
    profilePictureUrl?: string | null;
  } | null;
  minutes?: {
    otNormalMinutes: number;
    otExtraMinutes: number;
    otSpecialMinutes: number;
    workedMinutes: number;
  };
};

export const overtimeApi = {
  apply: (body: CreateOvertimeRequest) =>
    http.post<OvertimeRequestDto>('/overtime', body),
  bulk: (body: BulkOvertimeRequest) =>
    http.post<BulkOvertimeResponse>('/overtime/bulk', body),
  /** One page of the caller's department roll-call for a date, sorted by
   *  employee code. Requires `team.request.generation.overtime`. */
  departmentAttendance: (params: DepartmentAttendanceParams, signal?: AbortSignal) =>
    http.get<PageResponse<DepartmentAttendanceRow>>(
      `/overtime/department/attendance${qs({ ...params })}`,
      { signal },
    ),
  approve: (id: number, body: ApproveRequest = {}) =>
    http.post<OvertimeRequestDto>(`/overtime/${id}/approve`, body),
  reject: (id: number, body: RejectRequest) =>
    http.post<OvertimeRequestDto>(`/overtime/${id}/reject`, body),
  /**
   * Submitter-only. PENDING any time, or APPROVED before the work date.
   * Re-runs the day's OT minute bucket recompute when the request was
   * previously APPROVED.
   */
  cancel: (id: number) => http.post<OvertimeRequestDto>(`/overtime/${id}/cancel`),

  // Batch operations
  approveBatch: (batchId: string, body: ApproveRequest = {}) =>
    http.post<OvertimeRequestDto[]>(`/overtime/batches/${batchId}/approve`, body),
  rejectBatch: (batchId: string, body: RejectRequest) =>
    http.post<OvertimeRequestDto[]>(`/overtime/batches/${batchId}/reject`, body),

  // Inboxes (paginated)
  mine: (params: MyOvertimeParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<OvertimeRequestDto>>(
      `/overtime/me${qs({ ...params })}`,
      { signal },
    ),
  /**
   * Overtime of the caller's direct reports — whoever submitted it (the
   * report, or the manager on their behalf) — each paired with the target's
   * employee summary. Same filters as `mine` (work date), plus `employeeId`
   * (one report; anyone else answers empty). Default sort
   * `attendanceDate,desc`; `attendance.read`. Missing on older builds (404 /
   * 405).
   */
  team: (params: TeamOvertimeParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<ApprovalInboxOvertimeRow>>(
      `/overtime/team${qs({ ...params })}`,
      { signal },
    ),
  pendingApprovals: (params: PendingApprovalsParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<OvertimeRequestDto>>(
      `/overtime/pending-approvals${qs({ ...params })}`,
      { signal },
    ),
};

/* -------------------------- Regularization ---------------------------- */

export const regularizationsApi = {
  submit: (body: RegularizationRequest) =>
    http.post<RegularizationRequestDto>('/regularizations', body),
  approve: (id: number, body: ApproveRequest = {}) =>
    http.post<RegularizationRequestDto>(`/regularizations/${id}/approve`, body),
  reject: (id: number, body: RejectRequest) =>
    http.post<RegularizationRequestDto>(`/regularizations/${id}/reject`, body),
  /** Submitter-only. Allowed only while the request is PENDING. */
  cancel: (id: number) =>
    http.post<RegularizationRequestDto>(`/regularizations/${id}/cancel`),
  pendingApprovals: (params: PendingApprovalsParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<RegularizationRequestDto>>(
      `/regularizations/pending-approvals${qs({ ...params })}`,
      { signal },
    ),
};

/* ----------------------- Approval history ----------------------------- */

// Removed: `approvalsApi.history`, which typed `/approvals/history` as a
// Spring page and passed type/decision/page/size.
//
// The gateway rewrites that path to the BFF composite
// (/bff/approvals/history), which fans out across attendance, payroll and
// employee. It takes from/to only and answers `{ rows, total,
// erroredSources }`. Reading `.content`/`.totalElements` off that yields
// undefined, which is why the history table and the inbox decision counts
// both silently read as zero.
//
// Use `approvalsBff.history` in `./bff-api.ts` and narrow/page client-side.

/* --------------------------- Delegations ------------------------------ */

export type MyDelegationsParams = {
  status?: DelegationStatus;
  /** YYYY-MM-DD — filter to delegations active on this date. */
  activeOn?: string;
};

export const delegationsApi = {
  mine: (params: MyDelegationsParams = {}, signal?: AbortSignal) =>
    http.get<DelegationDto[]>(
      `/approval-delegations/me${qs({ ...params })}`,
      { signal },
    ),
  create: (body: CreateDelegationRequest) =>
    http.post<DelegationDto>('/approval-delegations', body),
  /** HR (with APPROVAL_OVERRIDE) creates a delegation on a manager's behalf. */
  createOnBehalf: (body: CreateDelegationOnBehalfRequest) =>
    http.post<DelegationDto>('/approval-delegations/on-behalf', body),
  update: (id: number, body: UpdateDelegationRequest) =>
    http.patch<DelegationDto>(`/approval-delegations/${id}`, body),
  /** Soft revoke — sets status=REVOKED, revoked_at=now. */
  revoke: (id: number) => http.del<DelegationDto>(`/approval-delegations/${id}`),
};

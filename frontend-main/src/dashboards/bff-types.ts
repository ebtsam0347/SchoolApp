/**
 * BFF DTO types — mirrored from `docs/services/bff.openapi.json` schemas
 * `EmployeeDashboard`, `ManagerDashboard`, `HrDashboard`, `PayrollDashboard`,
 * `AdminDashboard` and their nested types.
 *
 * Keep this file in lockstep with the OpenAPI mirror. The persona
 * adapter modules (`<persona>/adapter.ts`) take these types as input
 * and produce the view-model DTOs in `./types.ts` that the layouts
 * actually render.
 */

import type { PayrollRunStatus } from '@/payroll';

export interface BffDashboardMoney {
  amount: number;
  currency: string;
}

export interface BffEmployeeSummary {
  id: number;
  fullName: string;
  employeeCode: string | null;
  departmentName: string | null;
  profilePictureUrl: string | null;
}

/* ——————————— Employee ——————————— */

export interface BffEmployeeDashboard {
  nextPayday: BffEmployeeNextPayday;
  today: BffEmployeeToday;
  needsYou: BffEmployeeNeedsYou;
  leaveBalance: BffEmployeeLeaveBalance;
  attendanceHeatmap: BffEmployeeAttendanceHeatmap;
  payslips: BffEmployeePayslips;
  upcomingHolidays: BffEmployeeUpcomingHoliday[];
}

export interface BffEmployeeNextPayday {
  date: string; // YYYY-MM-DD
  projectedNet: BffDashboardMoney;
  runId: number;
  runStatus: string;
}

export interface BffEmployeeToday {
  status: string;
  checkInAt: string | null; // ISO date-time
  shift: { start: string; end: string; name: string };
  elapsedMinutes: number;
}

export interface BffEmployeeNeedsYou {
  pendingRequests: { count: number; items: BffEmployeeRequestRow[] };
  missingPunches: { count: number; items: BffEmployeePunchRow[] };
  documentsToAck: { count: number; items: BffEmployeeDocRow[] };
}

export interface BffEmployeeRequestRow {
  kind: string;
  id: number;
  summary: string;
  status: string;
  createdAt: string;
}

export interface BffEmployeePunchRow {
  date: string;
  reason: string;
}

export interface BffEmployeeDocRow {
  id: number;
  name: string;
  kind: string;
}

export interface BffEmployeeLeaveBalance {
  asOf: string;
  types: BffEmployeeLeaveTypeBalance[];
}

export interface BffEmployeeLeaveTypeBalance {
  code: string;
  label: string;
  used: number;
  pending: number;
  remaining: number;
  total: number;
}

export interface BffEmployeeAttendanceHeatmap {
  month: string;
  days: BffEmployeeHeatmapDay[];
}

export interface BffEmployeeHeatmapDay {
  date: string;
  status: string;
  varianceMin: number;
}

export interface BffEmployeePayslips {
  recent: BffEmployeePayslipRow[];
  netTrend12m: BffEmployeeNetTrendPoint[];
}

export interface BffEmployeePayslipRow {
  id: number;
  periodYear: number;
  periodMonth: number;
  gross: BffDashboardMoney;
  net: BffDashboardMoney;
  status: string;
  generatedAt: string | null;
}

export interface BffEmployeeNetTrendPoint {
  period: string;
  amount: number;
}

export interface BffEmployeeUpcomingHoliday {
  date: string;
  name: string;
  dayOfWeek: string;
}

/* ——————————— Manager ——————————— */

export interface BffManagerDashboard {
  team: BffManagerTeam;
  attendance14d: BffManagerAttendance14d;
  approvalQueue: BffManagerApprovalQueue;
  leaveCalendar30d: BffManagerLeaveCalendar30d;
  context: BffManagerContext;
}

export interface BffManagerTeam {
  size: number;
  directReports: number;
  /** Flat roster used for the avatar stack. Per-person status comes
   * from `/bff/attendance/team/today` — the composite no longer carries
   * a `today` sub-block. */
  members: BffEmployeeSummary[];
}

/** Response of `/bff/attendance/team/today`. The manager dashboard
 *  fans out to this for per-person status + counts. */
export interface BffTeamAttendanceToday {
  asOf: string;
  rows: BffTeamAttendanceTodayRow[];
  truncated: boolean;
}

export interface BffTeamAttendanceTodayRow {
  employee: BffEmployeeSummary;
  /** PRESENT | ABSENT | LEAVE | HOLIDAY | WEEKEND | HALF_DAY | PENDING_PUNCH */
  status: string;
  checkIn: string | null;
  checkOut: string | null;
  lateMinutes: number | null;
  activeLeave: { leaveTypeCode: string; halfDay: boolean } | null;
}

export interface BffManagerAttendance14d {
  from: string;
  to: string;
  days: BffManagerDayBucket[];
}

export interface BffManagerDayBucket {
  date: string;
  present: number;
  late: number;
  absent: number;
  leave: number;
}

export interface BffManagerApprovalQueue {
  totalPending: number;
  items: BffManagerApprovalRow[];
  velocity: BffManagerVelocity;
}

export interface BffManagerApprovalRow {
  kind: string;
  id: number;
  employee: BffEmployeeSummary;
  summary: string;
  createdAt: string;
}

export interface BffManagerVelocity {
  avgDecisionMinutes7d: number;
  trend7d: number[];
}

export interface BffManagerLeaveCalendar30d {
  from: string;
  to: string;
  items: BffManagerLeaveItem[];
}

export interface BffManagerLeaveItem {
  personId: number;
  personName: string;
  leaveType: string;
  start: string;
  end: string;
}

export interface BffManagerContext {
  punctualityWeek: { avgVarianceMin: number; trend7d: number[] };
  exceptionsWeek: { count: number };
}

/* ——————————— HR ——————————— */

export interface BffHrDashboard {
  orgPulse: BffHrOrgPulse;
  attendance30d: BffHrAttendance30d;
  headcount12m: BffHrHeadcount12m;
  queues: BffHrQueues;
  departmentLeave30d: BffHrDepartmentLeave[];
  recentAuditAnomalies: BffTimelineEntryView[];
}

export interface BffHrOrgPulse {
  asOf: string;
  headcount: number;
  presentToday: number;
  lateToday: number;
  onLeaveToday: number;
  absentToday: number;
}

export interface BffHrAttendance30d {
  from: string;
  to: string;
  days: BffHrRateDay[];
}

export interface BffHrRateDay {
  date: string;
  rate: number;
  anomaly: boolean;
}

export interface BffHrHeadcount12m {
  months: BffHrHeadcountMonth[];
}

export interface BffHrHeadcountMonth {
  month: string;
  joiners: number;
  leavers: number;
  endHeadcount: number;
}

export interface BffHrQueues {
  onboardingStuck: BffHrQueueSection;
  contractsExpiring: BffHrQueueSection;
  disciplinaryOpen: BffHrQueueSection;
  leaveHotspots: BffHrLeaveHotspots;
}

export interface BffHrQueueSection {
  count: number;
  items: BffHrQueueItem[];
}

export interface BffHrQueueItem {
  kind: string;
  id: number;
  employee: BffEmployeeSummary;
  summary: string;
  due: string | null;
}

export interface BffHrLeaveHotspots {
  count: number;
  items: BffHrLeaveHotspotRow[];
}

export interface BffHrLeaveHotspotRow {
  personId: number;
  name: string;
  departmentName: string | null;
  annualUsedPct: number;
  remainingDays: number;
}

export interface BffHrDepartmentLeave {
  departmentId: number;
  name: string;
  leaveDays: number;
}

export interface BffTimelineEntryView {
  id: number;
  action: string;
  actorUsername: string | null;
  actorLabel: string | null;
  message: string | null;
  occurredAt: string;
}

/* ——————————— Payroll ——————————— */

export interface BffPayrollDashboard {
  currentRun: BffPayrollCurrentRun;
  runs6m: BffPayrollRuns6m;
  needsYou: BffPayrollNeedsYou;
  /** Null when the backend has not yet computed scatter for this run. */
  anomalyScatter: BffPayrollAnomalyScatter | null;
  bonusByDepartment90d: BffPayrollDepartmentBonus[];
  netYoYTrend12m: BffPayrollNetPoint[];
}

export interface BffPayrollCurrentRun {
  id: number;
  fiscalYear: string;
  period: string;
  status: PayrollRunStatus;
  cutoffDate: string;
  daysToCutoff: number;
  rollup: BffPayrollRollup;
}

export interface BffPayrollRollup {
  gross: BffDashboardMoney;
  deductions: BffDashboardMoney;
  net: BffDashboardMoney;
  employeeCount: number;
}

export interface BffPayrollRuns6m {
  runs: BffPayrollRunRollup[];
}

export interface BffPayrollRunRollup {
  id: number;
  period: string;
  gross: BffDashboardMoney;
  deductions: BffDashboardMoney;
  net: BffDashboardMoney;
}

export interface BffPayrollNeedsYou {
  anomaliesOnRun: BffPayrollQueueSection;
  offCyclePending: BffPayrollQueueSection;
  salaryChangesPending: BffPayrollQueueSection;
  payslipsHeld: BffPayrollQueueSection;
}

export interface BffPayrollQueueSection {
  count: number;
  items: BffPayrollQueueItem[];
}

export interface BffPayrollQueueItem {
  id: number;
  employee: BffEmployeeSummary;
  summary: string;
  reason: string | null;
  occurredAt: string;
}

export interface BffPayrollAnomalyScatter {
  runId: number;
  comparedTo: number;
  points: BffPayrollScatterPoint[];
}

export interface BffPayrollScatterPoint {
  employeeId: number;
  name: string;
  departmentName: string | null;
  priorNet: number;
  currentNet: number;
  variancePct: number;
}

export interface BffPayrollDepartmentBonus {
  departmentId: number;
  name: string;
  total: BffDashboardMoney;
}

export interface BffPayrollNetPoint {
  month: string;
  amount: number;
}

/* ——————————— Admin ——————————— */

export interface BffAdminDashboard {
  systemHealth: BffAdminSystemHealth;
  auditVolume7d: BffAdminAuditDay[];
  needsYou: BffAdminNeedsYou;
  authEventsByHour: BffAdminAuthHourBucket[];
  recentRoleChanges: BffAuditEventView[];
}

export interface BffAdminSystemHealth {
  overall: string;
  sagas: BffAdminSagaSummary;
  auditReplayQueue: BffAdminReplayQueue;
}

export interface BffAdminSagaSummary {
  running: number;
  failed24h: number;
  list: BffAdminSagaHealth[];
}

export interface BffAdminSagaHealth {
  id: string;
  name: string;
  status: string;
  failureRate24h: number[];
}

export interface BffAdminReplayQueue {
  depth: number;
  oldestSeconds: number;
}

export interface BffAdminAuditDay {
  date: string;
  count: number;
}

export interface BffAdminNeedsYou {
  failedSagas: BffAdminQueueSection;
  permissionChanges24h: BffAdminQueueSection;
  lockedAccounts: BffAdminQueueSection;
}

export interface BffAdminQueueSection {
  count: number;
  items: BffAdminQueueItem[];
}

export interface BffAdminQueueItem {
  id: number;
  label: string;
  summary: string;
  retryHref: string | null;
}

export interface BffAdminAuthHourBucket {
  hour: number;
  logins: number;
  failures: number;
}

export interface BffAuditEventView {
  id: number;
  eventId: string | null;
  sourceService: string | null;
  entityType: string | null;
  entityId: string | null;
  action: string;
  actorUserId: number | null;
  actorUsername: string | null;
  actorLabel: string | null;
  correlationId: string | null;
  ipAddress: string | null;
  userVisible: boolean;
  /** Often null for system-generated events (CRUD on entities). */
  message: string | null;
  occurredAt: string;
  ingestedAt: string;
}

/**
 * Persona dashboard DTOs — one per BFF endpoint. Mirrors the response
 * shapes catalogued in `BFF_DASHBOARD_WISHLIST.md`. None of these
 * endpoints exist on the live BFF yet; the per-persona API modules
 * return mock data shaped to this contract until they ship, so the FE
 * layout work is unblocked.
 */

import type { ChartTone, SagaHealth } from '@/components';

export interface MoneyDto {
  amount: number;
  currency: 'PKR' | string;
}

export interface PersonRef {
  id: number;
  name: string;
  employeeCode?: string | null;
  departmentName?: string | null;
  profilePictureUrl?: string | null;
}

/* —————————————————————————————————————————————————————————————————————
 * M-D1 — Employee dashboard
 * Default for everyone. Today-shaped, not trend-shaped.
 */

export type EmployeeTodayStatus =
  | 'CHECKED_IN'
  | 'ON_LEAVE'
  | 'OFF'
  | 'PENDING_PUNCH';

export type RunStatus = 'DRAFT' | 'LOCKED' | 'POSTED' | 'RUNNING';

export interface PayslipRow {
  id: number;
  period: string; // YYYY-MM
  net: MoneyDto;
  pdfUrl?: string | null;
}

export interface RequestRow {
  id: string;
  kind: 'LEAVE' | 'OVERTIME' | 'REGULARIZATION';
  kindLabel: string;
  /** Range start (ISO date), inclusive. */
  start: string;
  end: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  /** Human-readable summary, e.g. "5 d · Annual" or "4 h · OT". */
  summary: string;
  /** Deep link into the module page. */
  href?: string;
}

export interface MissingPunchRow {
  date: string;
  shiftName?: string | null;
  hint?: string;
  href?: string;
}

export interface DocumentAckRow {
  id: string;
  title: string;
  effectiveOn?: string | null;
  href?: string;
}

export interface LeaveBalanceTypeDto {
  code: string;
  label: string;
  used: number;
  pending: number;
  remaining: number;
  total: number;
}

export interface AttendanceHeatmapDayDto {
  date: string;
  status: 'PRESENT' | 'LATE' | 'ABSENT' | 'ON_LEAVE' | 'OFF' | 'HOLIDAY';
  varianceMin: number | null;
  checkInAt?: string | null;
}

export interface UpcomingHolidayDto {
  date: string;
  name: string;
  dayOfWeek: 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN';
}

export interface EmployeeDashboardDto {
  nextPayday: {
    date: string | null;
    projectedNet: MoneyDto | null;
    runId: number | null;
    runStatus: RunStatus | null;
  };
  today: {
    status: EmployeeTodayStatus;
    checkInAt?: string | null;
    shift: { start: string; end: string; name: string } | null;
    elapsedMinutes: number | null;
  };
  needsYou: {
    pendingRequests: { count: number; items: RequestRow[] };
    missingPunches: { count: number; items: MissingPunchRow[] };
    documentsToAck: { count: number; items: DocumentAckRow[] };
  };
  leaveBalance: {
    asOf: string;
    types: LeaveBalanceTypeDto[];
  };
  attendanceHeatmap: {
    month: string;
    days: AttendanceHeatmapDayDto[];
  };
  payslips: {
    recent: PayslipRow[];
    netTrend12m: Array<{ period: string; amount: number }>;
  };
  upcomingHolidays: UpcomingHolidayDto[];
}

/* —————————————————————————————————————————————————————————————————————
 * M-D2 — Manager dashboard
 * Caller must have direct reports.
 */

export type TeamMemberStatus =
  | 'PRESENT'
  | 'LATE'
  | 'ABSENT'
  | 'ON_LEAVE'
  | 'PENDING_PUNCH';

export interface TeamMemberRow extends PersonRef {
  status: TeamMemberStatus;
  /** Check-in time when present/late. */
  checkInAt?: string | null;
  /** Variance from shift start (minutes). Positive = late. */
  varianceMin?: number | null;
}

export interface ManagerTodayRollup {
  present: number;
  late: number;
  absent: number;
  onLeave: number;
  pendingPunch: number;
  /** Up to 12 rows for the avatar stack + roll-call list. */
  people: TeamMemberRow[];
}

export interface ManagerAttendanceDay {
  date: string; // YYYY-MM-DD
  present: number;
  late: number;
  absent: number;
  leave: number;
}

export type ApprovalKind = 'LEAVE' | 'SHORT_LEAVE' | 'OVERTIME' | 'REGULARIZATION' | 'SHIFT_OVERRIDE';

export interface ApprovalRow {
  id: string;
  kind: ApprovalKind;
  /** Human-readable kind ("Annual leave", "OT", …). */
  kindLabel: string;
  person: PersonRef;
  /** Range start (ISO date). */
  start: string;
  /** Range end (ISO date). Same as start for single-day items. */
  end: string;
  /** Submitted timestamp (ISO). */
  submittedAt: string;
  /** Free-form reason / note. */
  reason?: string | null;
  /** Tone hint — drives the row marker dot. */
  tone?: ChartTone;
  /** Deep link into the module page where the actual approval happens. */
  href?: string;
}

export interface LeaveCalendarItemDto {
  id: string;
  personId: number;
  personName: string;
  /** ISO YYYY-MM-DD, inclusive. */
  start: string;
  /** ISO YYYY-MM-DD, inclusive. */
  end: string;
  leaveType: string;
  tone?: ChartTone;
}

export interface ManagerDashboardDto {
  team: {
    size: number;
    directReports: number;
    today: ManagerTodayRollup;
  };
  attendance14d: {
    from: string;
    to: string;
    days: ManagerAttendanceDay[];
  };
  approvalQueue: {
    totalPending: number;
    items: ApprovalRow[];
    velocity: {
      avgDecisionMinutes7d: number;
      trend7d: number[];
    };
  };
  leaveCalendar30d: {
    from: string;
    to: string;
    items: LeaveCalendarItemDto[];
  };
  context: {
    punctualityWeek: { avgVarianceMin: number; trend7d: number[] };
    exceptionsWeek: { count: number };
  };
}

/* —————————————————————————————————————————————————————————————————————
 * M-D3 — HR dashboard
 * Caller needs employee.*.read + attendance.*.read at org scope.
 */

export interface HrAttendanceDay {
  date: string;
  /** 0..1 — fraction of org headcount present that day. */
  rate: number;
  /** True if outside 1σ of trailing 14-day mean (server-computed). */
  anomaly: boolean;
}

export interface HrHeadcountMonth {
  month: string; // YYYY-MM
  joiners: number;
  leavers: number;
  endHeadcount: number;
}

export interface OnboardingStuckRow {
  id: string;
  personName: string;
  /** Stage they're stuck in (e.g. "Background check"). */
  stage: string;
  /** Days in current stage. */
  ageDays: number;
  href?: string;
}

export interface ContractExpiryRow {
  id: string;
  personName: string;
  contractType: string;
  /** ISO date. */
  expiresOn: string;
  /** Days remaining (negative if already expired). */
  daysLeft: number;
  href?: string;
}

export interface DisciplinaryRow {
  id: string;
  personName: string;
  level: string;
  status: string;
  openedOn: string;
  href?: string;
}

export interface LeaveHotspotRow {
  personId: number;
  personName: string;
  departmentName: string;
  /** 0..1 — fraction of annual leave consumed. */
  annualUsedPct: number;
  remainingDays: number;
  href?: string;
}

export interface DepartmentLeaveRow {
  departmentId: number;
  name: string;
  leaveDays: number;
  /** Optional headcount for caption / per-capita derivation. */
  headcount?: number;
}

export interface AuditAnomalyRow {
  id: string;
  occurredAt: string;
  summary: string;
  href?: string;
}

/* —————————————————————————————————————————————————————————————————————
 * M-D4 — Payroll dashboard
 * Caller needs `payroll.*.read`. Some sections only populate if the
 * caller can also see the active run.
 */

// Canonical run lifecycle from the payroll module. Was a stale 4-value copy
// here (incl. a non-existent RUNNING) that collapsed real states to "Draft".
import type { PayrollRunStatus } from '@/payroll';

export type { PayrollRunStatus };

export interface PayrollRunRollupDto {
  gross: MoneyDto;
  deductions: MoneyDto;
  net: MoneyDto;
  employeeCount: number;
}

export interface PayrollRunSummaryDto {
  id: number;
  period: string; // YYYY-MM
  gross: MoneyDto;
  deductions: MoneyDto;
  net: MoneyDto;
}

export interface PayrollAnomalyRow {
  id: string;
  personName: string;
  departmentName: string;
  /** "+39%" or "Rs 56,000" — server picks the most surprising form. */
  summary: string;
  /**
   * Fractional variance vs prior period (e.g. 0.39 = +39%). Null when
   * the BFF hasn't computed the scatter yet — the layout falls back to
   * a generic "Anomaly" pill in that case.
   */
  variancePct: number | null;
  href?: string;
}

export interface OffCycleApprovalRow {
  id: string;
  personName: string;
  category: string;
  amount: MoneyDto | null;
  submittedAt: string;
  href?: string;
}

export interface SalaryChangeRow {
  id: string;
  personName: string;
  /** Effective date (ISO). */
  effectiveOn: string;
  /** Free-form summary, e.g. "+12% · L4 → L5". */
  summary: string;
  href?: string;
}

export interface PayslipHoldRow {
  id: string;
  personName: string;
  runPeriod: string | null;
  reason: string;
  amount: MoneyDto | null;
  href?: string;
}

export interface AnomalyScatterPointDto {
  employeeId: number;
  name: string;
  departmentName: string;
  priorNet: number;
  currentNet: number;
  /** Fractional variance (e.g. 0.39 = +39%). */
  variancePct: number;
}

export interface PayrollBonusDeptRow {
  departmentId: number;
  name: string;
  total: MoneyDto;
}

export interface PayrollNetYoyPoint {
  month: string; // YYYY-MM
  amount: number;
}

export interface PayrollDashboardDto {
  currentRun: {
    id: number;
    fiscalYear: string;
    period: string;
    status: PayrollRunStatus;
    cutoffDate: string;
    daysToCutoff: number;
    rollup: PayrollRunRollupDto;
  };
  runs6m: {
    runs: PayrollRunSummaryDto[];
  };
  needsYou: {
    anomaliesOnRun: { count: number; items: PayrollAnomalyRow[] };
    offCyclePending: { count: number; items: OffCycleApprovalRow[] };
    salaryChangesPending: { count: number; items: SalaryChangeRow[] };
    payslipsHeld: { count: number; items: PayslipHoldRow[] };
  };
  anomalyScatter: {
    runId: number;
    comparedTo: number;
    points: AnomalyScatterPointDto[];
  };
  bonusByDepartment90d: PayrollBonusDeptRow[];
  netYoYTrend12m: PayrollNetYoyPoint[];
}

/* —————————————————————————————————————————————————————————————————————
 * M-D5 — Admin / Auditor dashboard
 * Caller needs admin-tier scope (`*.admin` or operator role).
 */

export type SystemHealthOverall = 'OK' | 'DEGRADED' | 'INCIDENT';

// Saga health lives canonically in the SagaStatusGrid chart (@/components).
// Alias it so the dashboards view-models share that single source of truth.
export type SagaHealthStatus = SagaHealth;

export interface SagaHealthRow {
  id: string;
  name: string;
  status: SagaHealthStatus;
  /** 24 hourly buckets — count of failures in each hour. */
  failureRate24h: number[];
}

/**
 * Unified shape for all three admin queues — failed sagas, permission
 * changes in last 24h, locked accounts. The BFF returns the same
 * `{label, summary, retryHref}` envelope for each, with category
 * supplied by which queue the row landed in.
 */
export interface AdminQueueRow {
  id: string;
  /** Headline (saga name / subject username / locked username). */
  label: string;
  /** Action summary (failure reason / change description / lockout reason). */
  summary: string;
  /** When non-null, the row exposes a Retry button wired to this URL. */
  retryHref: string | null;
}

export interface AuditVolumePoint {
  date: string;
  count: number;
}

export interface AuthEventsHourPoint {
  hour: number; // 0..23
  logins: number;
  failures: number;
}

export interface RoleChangeRow {
  id: string;
  actorName: string;
  /** May be missing — BFF audit events don't always carry a target name. */
  subjectName?: string | null;
  summary: string;
  occurredAt: string;
  href?: string;
}

export interface AdminDashboardDto {
  systemHealth: {
    overall: SystemHealthOverall;
    sagas: {
      running: number;
      failed24h: number;
      list: SagaHealthRow[];
    };
    auditReplayQueue: {
      depth: number;
      oldestSeconds: number;
    };
  };
  auditVolume7d: AuditVolumePoint[];
  needsYou: {
    failedSagas: { count: number; items: AdminQueueRow[] };
    permissionChanges24h: { count: number; items: AdminQueueRow[] };
    lockedAccounts: { count: number; items: AdminQueueRow[] };
  };
  authEventsByHour: AuthEventsHourPoint[];
  recentRoleChanges: RoleChangeRow[];
}

export interface HrDashboardDto {
  orgPulse: {
    asOf: string;
    headcount: number;
    presentToday: number;
    lateToday: number;
    onLeaveToday: number;
    absentToday: number;
  };
  attendance30d: {
    from: string;
    to: string;
    days: HrAttendanceDay[];
  };
  headcount12m: {
    months: HrHeadcountMonth[];
  };
  queues: {
    onboardingStuck: { count: number; items: OnboardingStuckRow[] };
    contractsExpiring: { count: number; items: ContractExpiryRow[] };
    disciplinaryOpen: { count: number; items: DisciplinaryRow[] };
    leaveHotspots: { count: number; items: LeaveHotspotRow[] };
  };
  departmentLeave30d: DepartmentLeaveRow[];
  recentAuditAnomalies: AuditAnomalyRow[];
}

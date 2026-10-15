// Display helpers for the payroll module. Period formatting + status
// labels + tones for the various enums payroll exposes.

import type { StatusPillTone } from '@/components';
import type {
  AdvanceApprovalStage,
  AdvanceCurrentStageDto,
  AdvanceStatus,
  BonusNo,
  BonusPaymentMode,
  BonusPercentage,
  BonusRunStatus,
  IncrementBatchStatus,
  IncrementLineStatus,
  PaymentMode,
  EmployeePaymentMode,
  PayrollRunStatus,
  PayslipStatus,
  ComponentType,
  ComponentCalcType,
  FiscalYearStatus,
  PermanentAdvanceDeductionSource,
  SagaStatus,
  ShortTermAdvanceLineStatus,
} from './types';

/** "Apr 2026" — period header. */
export function formatPeriod(year: number, month: number): string {
  const d = new Date(year, month - 1, 1);
  return d.toLocaleDateString('en-GB', { month: 'short', year: 'numeric' });
}

/** "April 2026" — long form. */
export function formatPeriodLong(year: number, month: number): string {
  const d = new Date(year, month - 1, 1);
  return d.toLocaleDateString('en-GB', { month: 'long', year: 'numeric' });
}

/* ------------------------------ Pretty maps --------------------------- */

export const PAYROLL_RUN_STATUS_LABEL: Record<PayrollRunStatus, string> = {
  DRAFT: 'Draft',
  LOCKED: 'Attendance locked',
  COMPUTING: 'Computing',
  COMPUTED: 'Computed',
  APPROVED: 'Approved',
  POSTING: 'Posting',
  POSTED: 'Posted',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};

export const PAYROLL_RUN_STATUS_TONE: Record<PayrollRunStatus, StatusPillTone> = {
  DRAFT: 'neutral',
  LOCKED: 'info',
  COMPUTING: 'info',
  COMPUTED: 'info',
  APPROVED: 'warning',
  POSTING: 'info',
  POSTED: 'success',
  FAILED: 'error',
  CANCELLED: 'neutral',
};

export const PAYSLIP_STATUS_LABEL: Record<PayslipStatus, string> = {
  DRAFT: 'Draft',
  PAID: 'Paid',
  HELD: 'Held',
  RELEASE_PENDING: 'Release pending',
  RELEASED: 'Released',
};

export const PAYSLIP_STATUS_TONE: Record<PayslipStatus, StatusPillTone> = {
  DRAFT: 'neutral',
  PAID: 'success',
  HELD: 'warning',
  RELEASE_PENDING: 'info',
  RELEASED: 'neutral',
};

export const PAYMENT_MODE_LABEL: Record<PaymentMode, string> = {
  CASH: 'Cash',
  BANK: 'Bank transfer',
  OTHER: 'Other',
};

export const EMPLOYEE_PAYMENT_MODE_LABEL: Record<EmployeePaymentMode, string> = {
  CASH: 'Cash',
  BANK_TRANSFER: 'Bank transfer',
};

/** Bonus slots offered per fiscal year. */
export const BONUS_NO_OPTIONS: readonly BonusNo[] = [1, 2];

/** Shares of salary a bonus run may pay out. */
export const BONUS_PERCENTAGE_OPTIONS: readonly BonusPercentage[] = [25, 50, 75, 100];

/** "Bonus 1" — display name for a bonus-no settings row. */
export function formatBonusRun(bonusNo: number): string {
  return `Bonus ${bonusNo}`;
}

export const BONUS_RUN_STATUS_LABEL: Record<BonusRunStatus, string> = {
  ...PAYROLL_RUN_STATUS_LABEL,
  LOCKED: 'Locked',
};

/** Approval is terminal for a bonus run (no post step), so it reads as done. */
export const BONUS_RUN_STATUS_TONE: Record<BonusRunStatus, StatusPillTone> = {
  ...PAYROLL_RUN_STATUS_TONE,
  APPROVED: 'success',
};

export const BONUS_PAYMENT_MODE_LABEL: Record<BonusPaymentMode, string> = {
  CASH: 'Cash',
  BANK_TRANSFER: 'Bank transfer',
};

export const INCREMENT_BATCH_STATUS_LABEL: Record<IncrementBatchStatus, string> = {
  DRAFT: 'Draft',
  APPLIED: 'Applied',
  CANCELLED: 'Cancelled',
};

export const INCREMENT_BATCH_STATUS_TONE: Record<IncrementBatchStatus, StatusPillTone> = {
  DRAFT: 'neutral',
  APPLIED: 'success',
  CANCELLED: 'error',
};

export const INCREMENT_LINE_STATUS_LABEL: Record<IncrementLineStatus, string> = {
  VALID: 'Valid',
  ERROR: 'Error',
  APPLIED: 'Applied',
};

export const INCREMENT_LINE_STATUS_TONE: Record<IncrementLineStatus, StatusPillTone> = {
  VALID: 'info',
  ERROR: 'error',
  APPLIED: 'success',
};

export const FY_STATUS_LABEL: Record<FiscalYearStatus, string> = {
  CLOSED: 'Closed',
  CURRENT: 'Current',
  FUTURE: 'Future',
};

export const FY_STATUS_TONE: Record<FiscalYearStatus, StatusPillTone> = {
  CLOSED: 'neutral',
  CURRENT: 'success',
  FUTURE: 'info',
};

export const SAGA_STATUS_TONE: Record<SagaStatus, StatusPillTone> = {
  RUNNING: 'info',
  COMPLETED: 'success',
  FAILED: 'error',
};

export const COMPONENT_TYPE_LABEL: Record<ComponentType, string> = {
  EARNING: 'Earning',
  DEDUCTION: 'Deduction',
};

export const COMPONENT_TYPE_TONE: Record<ComponentType, StatusPillTone> = {
  EARNING: 'success',
  DEDUCTION: 'warning',
};

export const COMPONENT_CALC_LABEL: Record<ComponentCalcType, string> = {
  FLAT: 'Flat (monthly)',
  PER_DAY: 'Per day × days present',
};

export const ADVANCE_STATUS_LABEL: Record<AdvanceStatus, string> = {
  PENDING: 'Pending Manager',
  MANAGER_APPROVED: 'Pending HR',
  HR_APPROVED: 'Pending Accounts',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

export const ADVANCE_STATUS_TONE: Record<AdvanceStatus, StatusPillTone> = {
  PENDING: 'warning',
  MANAGER_APPROVED: 'warning',
  HR_APPROVED: 'info',
  APPROVED: 'success',
  REJECTED: 'error',
  CANCELLED: 'neutral',
};

export const ADVANCE_APPROVAL_STAGE_LABEL: Record<AdvanceApprovalStage, string> = {
  MANAGER_APPROVAL: 'Manager',
  HR_APPROVAL: 'HR',
  ACCOUNTS_APPROVAL: 'Accounts',
};

/**
 * Resolves the authoritative approval-stage enum for a row — who it's
 * currently sitting with. Prefers `approvalStage` (the field itself),
 * falls back to matching `currentStage.stageRole` against the three known
 * stage names ("Manager Approval" / "HR Approval" / "Accounts Approval"),
 * and finally falls back to `status` — which also maps 1:1 onto the three
 * stages (PENDING -> Manager, MANAGER_APPROVED -> HR, HR_APPROVED ->
 * Accounts) — for rows a response didn't include either field on. Prefer
 * `approvalStage`/`currentStage` when present since the backend documents
 * those as authoritative, but `status` is a reliable fallback, not a guess.
 *
 * This is the single source of truth for both the Stage badge label
 * ({@link advanceStageLabel}) and for picking which approve endpoint
 * (manager-approve / hr-approve / accounts-approve) a row's Approve button
 * should call — the two must never be derived independently, or they can
 * drift apart the way the badge and the button did before.
 */
export function resolveAdvanceApprovalStage(row: {
  status: AdvanceStatus;
  approvalStage?: AdvanceApprovalStage | null;
  currentStage?: AdvanceCurrentStageDto | null;
}): AdvanceApprovalStage | null {
  if (row.approvalStage) return row.approvalStage;
  const stageRole = row.currentStage?.stageRole?.trim().toUpperCase();
  if (stageRole) {
    if (stageRole.startsWith('MANAGER')) return 'MANAGER_APPROVAL';
    if (stageRole.startsWith('HR')) return 'HR_APPROVAL';
    if (stageRole.startsWith('ACCOUNTS')) return 'ACCOUNTS_APPROVAL';
    return null;
  }
  if (row.status === 'PENDING') return 'MANAGER_APPROVAL';
  if (row.status === 'MANAGER_APPROVED') return 'HR_APPROVAL';
  if (row.status === 'HR_APPROVED') return 'ACCOUNTS_APPROVAL';
  return null;
}

/**
 * Display label for the "Stage" badge. Falls back to the raw (suffix-
 * stripped) `currentStage.stageRole` text for a stage name
 * {@link resolveAdvanceApprovalStage} doesn't recognize, so an unexpected
 * future stage still shows *something* meaningful instead of going blank.
 */
export function advanceStageLabel(row: {
  status: AdvanceStatus;
  approvalStage?: AdvanceApprovalStage | null;
  currentStage?: AdvanceCurrentStageDto | null;
}): string | null {
  const stage = resolveAdvanceApprovalStage(row);
  if (stage) return ADVANCE_APPROVAL_STAGE_LABEL[stage];
  const stageRole = row.currentStage?.stageRole;
  return stageRole ? stageRole.replace(/\s*Approval$/i, '').trim() || stageRole : null;
}

export const SHORT_TERM_ADVANCE_LINE_LABEL: Record<
  ShortTermAdvanceLineStatus,
  string
> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

export const SHORT_TERM_ADVANCE_LINE_TONE: Record<
  ShortTermAdvanceLineStatus,
  StatusPillTone
> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
  CANCELLED: 'neutral',
};

export const PERMANENT_ADVANCE_DEDUCTION_SOURCE_LABEL: Record<
  PermanentAdvanceDeductionSource,
  string
> = {
  SALARY: 'Salary',
  BONUS: 'Bonus',
  ANNUAL_LEAVE: 'Annual leave',
  MANUAL: 'Manual',
  SETTLEMENT: 'Final settlement',
};

/* -------------------------- ISO date helpers -------------------------- */
//
// Canonical date formatters live in @/lib/dates. Re-exported here so
// payroll callers can `import { formatDateShort } from '@/payroll'` like
// any other format helper.

export {
  formatDateLong,
  formatDateMonthDay,
  formatDateShort,
  formatDateTime,
} from '@/lib/dates';

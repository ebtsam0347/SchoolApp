// Payroll composites — one round-trip per page surface
// (self-serve payslips / run detail+list / increment-batch / off-cycle /
// per-employee salary tab / employee picker / disbursement queue).
//
// Hosted by the payroll service (the BFF used to host these); see
// `docs/services/payroll.openapi.json` under "Payroll — Composites" for
// response shapes. The service-direct wrappers in `./api.ts` remain
// authoritative for writes; the composites here are read-only and aggregate
// or enrich.

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';
import type { EmployeeSummary } from '@/attendance';
import type {
  ComponentCalcType,
  ComponentType,
  EmployeeBankDetailsDto,
  EmployeePayrollSettingsDto,
  EmployeeSalaryComponentDto,
  EmployeeSalaryDto,
  GratuityDto,
  IncrementBatchStatus,
  IncrementLineStatus,
  OffCycleCategory,
  OffCyclePaymentDto,
  PageResponse,
  PayrollRunDto,
  PayrollRunStatus,
  PayslipStatus,
  SalaryComponentDto,
} from './types';

/* --------------------- P1: GET /payslips/me --------------------------- */

/**
 * Self-serve payslip row: period info denormalized, idempotent signed
 * `pdfUrl` (null when no PDF generated yet), and the upstream payslip
 * totals carried through unchanged.
 */
export type MyPayslipRow = {
  id: number;
  payrollRunId: number;
  periodYear: number;
  periodMonth: number;
  fiscalYearCode: string;
  basic: number;
  allowances: number;
  gross: number;
  deductions: number;
  tax: number;
  eobi: number;
  pessiEmployer: number;
  net: number;
  lopDays: number;
  otHours: number;
  otAmount: number;
  generatedAt: string | null;
  status: PayslipStatus;
  heldReason: string | null;
  pdfUrl: string | null;
};

export type MyPayslipsResponse = {
  employeeId: number;
  year: number;
  payslips: PageResponse<MyPayslipRow>;
};

/* --------------- GET /payroll/runs/{id}/summary ----------------------- */

/**
 * Lightweight run snapshot — status + progress counts + totals — designed
 * for the run detail page to poll cheaply while compute is running.
 * Once status leaves COMPUTING, the page fires the heavier payslip page
 * (`payrollComposites.run`) and the skips list once.
 */
export type PayrollRunSummaryDto = {
  id: number;
  fiscalYearCode: string;
  periodYear: number;
  periodMonth: number;
  status: PayrollRunStatus;
  totalCount: number;
  progressCount: number;
  skippedCount: number;
  successCount: number;
  computedAt: string | null;
  postedAt: string | null;
  totals: {
    employeeCount: number;
    basic: number;
    allowances: number;
    gross: number;
    deductions: number;
    tax: number;
    eobi: number;
    pessiEmployer: number;
    net: number;
    heldCount: number;
    releasePendingCount: number;
  };
};

/* --------------- P2: GET /payroll/runs/{id}/detail -------------------- */

export type PayrollRunSummary = {
  employees: number;
  basic: number;
  allowances: number;
  gross: number;
  deductions: number;
  tax: number;
  eobi: number;
  pessiEmployer: number;
  net: number;
  held: number;
  releasePending: number;
};

/**
 * Run-detail payslip row. The backend widened this from a flat
 * payslip-plus-employee-summary into a full salary slip: identity + bank,
 * the earnings and deductions breakdowns with their own totals, the
 * attendance basis for the period, and the leave position. Everything the
 * payslip drawer renders comes from here — there is no per-payslip detail
 * endpoint carrying the leave/bank blocks (`GET /payslips/{id}` still
 * returns the older flat `PayslipDto`).
 *
 * Note the money now lives under `salary` — `row.gross` / `row.net` and
 * friends no longer exist at the top level.
 */
export type PayslipWithEmployee = {
  id: number;
  periodYear: number;
  periodMonth: number;
  fiscalYearCode: string;
  status: PayslipStatus;
  heldReason: string | null;
  generatedAt: string | null;
  employee: PayslipEmployeeDetail;
  /** Null when the employee has no salary account on file. */
  bank: PayslipBankDetail | null;
  salary: PayslipSalarySummary;
  earnings: PayslipEarningsBlock;
  deductions: PayslipDeductionsBlock;
  attendance: PayslipAttendanceBlock;
  leaves: PayslipLeavesBlock | null;
};

/** Employee identity denormalized onto the slip (not `EmployeeSummary`). */
export type PayslipEmployeeDetail = {
  employeeId: number;
  employeeCode: string | null;
  fullName: string;
  fatherName: string | null;
  designation: string | null;
  department: string | null;
  grade: string | null;
  dateOfJoining: string | null;
  profilePictureUrl: string | null;
};

/** Salary account the net is disbursed to. */
export type PayslipBankDetail = {
  bankName: string | null;
  branch: string | null;
  accountNumber: string | null;
};

/** Authoritative totals — the breakdown blocks below reconcile to these. */
export type PayslipSalarySummary = {
  basic: number;
  grossSalary: number;
  totalDeduction: number;
  netSalary: number;
  tax: number;
  eobi: number;
  /** Employer-side PESSI — a cost, not deducted from net. */
  pessiEmployer: number;
  /** Outstanding long-term advance; null when the employee has none. */
  permanentAdvanceBalance: number | null;
};

/** One earning / deduction row. Flat `{code,name,amount}` — the rate+days
 *  breakdown the old `PayslipLineDto` carried is not on this payload. */
export type PayslipAmountLine = {
  code: string;
  name: string;
  amount: number;
};

export type PayslipEarningsBlock = {
  lines: PayslipAmountLine[];
  grossSalary: number;
};

export type PayslipDeductionsBlock = {
  lines: PayslipAmountLine[];
  totalDeduction: number;
};

/** Attendance basis the slip was computed from. */
export type PayslipAttendanceBlock = {
  workingDays: number;
  daysWorked: number;
  absentDays: number;
  holidaysInPeriod: number;
  weekendsInPeriod: number;
  lopDays: number;
  otHours: number;
  otAmount: number;
};

/** Per-type leave usage: this payroll month and the running leave cycle. */
export type PayslipLeaveLine = {
  code: string;
  name: string;
  availedThisMonth: number;
  availedThisCycle: number;
};

/** Annual leave is entitlement-based, so it reports a balance. */
export type PayslipAnnualLeave = {
  total: number;
  availed: number;
  /** Days already encashed out of `availed`. */
  paid: number;
  balance: number;
};

export type PayslipLeavesBlock = {
  leaves: PayslipLeaveLine[];
  annualLeave: PayslipAnnualLeave | null;
};

export type PayrollRunDetail = {
  run: PayrollRunDto;
  summary: PayrollRunSummary;
  payslips: PageResponse<PayslipWithEmployee>;
};

/* --------------- P3: GET /payroll/runs/with-summary ------------------- */

export type PayrollRunsListItem = {
  run: PayrollRunDto;
  summary: PayrollRunSummary;
};

export type PayrollRunsListResponse = {
  fiscalYearCode: string;
  runs: PayrollRunsListItem[];
};

/* --------- P4: GET /salary/increment-batches/{id}/detail -------------- */

export type IncrementBatchLineWithEmployee = {
  id: number;
  employee: EmployeeSummary;
  oldBasic: number;
  newBasic: number;
  oldIncomeTax: number | null;
  newIncomeTax: number | null;
  effectiveFrom: string | null;
  status: IncrementLineStatus;
  errorMessage: string | null;
};

export type IncrementBatchDetail = {
  id: number;
  name: string;
  status: IncrementBatchStatus;
  effectiveFrom: string;
  notes: string | null;
  appliedAt: string | null;
  appliedBy: number | null;
  lines: IncrementBatchLineWithEmployee[];
};

/* ----------- P6: GET /off-cycle-payments/with-employee ---------------- */

export type OffCyclePaymentRowWithEmployee = {
  id: number;
  employee: EmployeeSummary;
  paidOn: string;
  amount: number;
  reason: string;
  paymentMode: 'CASH' | 'BANK' | 'OTHER' | null;
  referenceNo: string | null;
  offCyclePaymentTypeId: number | null;
  offCyclePaymentTypeName: string | null;
  sourceEventId: number | null;
  sourceInstallmentId: number | null;
};

export type ListOffCycleWithEmployeeParams = {
  employeeId?: number;
  from?: string;
  to?: string;
  category?: OffCycleCategory;
  page?: number;
  size?: number;
};

/* --------------- P7: GET /payroll/people/{id}/salary ------------------ */

/**
 * One-shot bundle for the per-employee salary tab. Replaces 8 fan-out
 * calls — current basic, history, components, catalog, payroll-settings,
 * bank details, gratuity, and 12-month off-cycle payments.
 *
 * Fields type-equivalent to their service-direct counterparts so callers
 * can keep using `EmployeeSalaryDto`-shaped helpers.
 */
export type EmployeeSalaryTab = {
  employeeId: number;
  currentBasic: EmployeeSalaryDto | null;
  basicHistory: EmployeeSalaryDto[];
  components: EmployeeSalaryComponentDto[];
  catalog: SalaryComponentDto[];
  payrollSettings: EmployeePayrollSettingsDto | null;
  bankDetails: EmployeeBankDetailsDto | null;
  gratuity: GratuityDto;
  offCycle12Months: OffCyclePaymentDto[];
};

// Re-export for completeness — the composite shape mirrors
// EmployeeSalaryComponentDto. If they ever diverge this is the seam.
export type { ComponentCalcType, ComponentType };

/* -------------------- P15: GET /payslips/queue ------------------------ */

export type PayslipQueueRow = {
  id: number;
  employee: EmployeeSummary;
  periodYear: number;
  periodMonth: number;
  fiscalYearCode: string;
  net: number;
  status: PayslipStatus;
  heldReason: string | null;
  generatedAt: string | null;
};

export type PayslipQueueResponse = {
  status: PayslipStatus;
  page: PageResponse<PayslipQueueRow>;
};

/* ----------------------------- Wrappers ------------------------------- */

export const payrollComposites = {
  payslipsMine: (year: number, signal?: AbortSignal) =>
    http.get<MyPayslipsResponse>(`/payslips/me${qs({ year })}`, { signal }),

  runSummary: (id: number, signal?: AbortSignal) =>
    http.get<PayrollRunSummaryDto>(`/payroll/runs/${id}/summary`, { signal }),

  run: (
    id: number,
    params: {
      status?: PayslipStatus;
      q?: string;
      /** 0-based Spring page index. */
      page?: number;
      /** Page size (Spring default is 20). */
      size?: number;
    } = {},
    signal?: AbortSignal,
  ) =>
    http.get<PayrollRunDetail>(
      `/payroll/runs/${id}/detail${qs({
        status: params.status,
        q: params.q,
        page: params.page,
        size: params.size,
      })}`,
      { signal },
    ),

  runs: (fiscalYear: string, signal?: AbortSignal) =>
    http.get<PayrollRunsListResponse>(
      `/payroll/runs/with-summary${qs({ fiscalYear })}`,
      { signal },
    ),

  incrementBatch: (id: number, signal?: AbortSignal) =>
    http.get<IncrementBatchDetail>(`/salary/increment-batches/${id}/detail`, { signal }),

  offCyclePayments: (params: ListOffCycleWithEmployeeParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<OffCyclePaymentRowWithEmployee>>(
      `/off-cycle-payments/with-employee${qs({
        employeeId: params.employeeId,
        from: params.from,
        to: params.to,
        category: params.category,
        page: params.page,
        size: params.size,
      })}`,
      { signal },
    ),

  salaryTab: (employeeId: number, signal?: AbortSignal) =>
    http.get<EmployeeSalaryTab>(`/payroll/people/${employeeId}/salary`, { signal }),

  payslipQueue: (status: PayslipStatus, signal?: AbortSignal) =>
    http.get<PayslipQueueResponse>(`/payslips/queue${qs({ status })}`, { signal }),
};

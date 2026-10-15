// Payroll-service contract types. Mirrors
// `docs/services/payroll.openapi.json` — keep in sync if the spec drifts.
// Domain rules in `docs/services/payroll.md`.
//
// Money is rupees on the wire (BigDecimal scale 2 server side; JSON number
// at the boundary). Pass values straight to MoneyCell — no conversion.

import type { EmployeeSummary, PageResponse } from '@/attendance';
import type { components } from '@/generated/payroll';

export type { PageResponse };

/* ------------------------------ Enums --------------------------------- */

export type ComponentType = 'EARNING' | 'DEDUCTION';
export type ComponentCalcType = 'FLAT' | 'PER_DAY';

/**
 * Run lifecycle, derived from the generated payroll spec. A new backend
 * state widens this union and fails typecheck at every exhaustive consumer
 * (PAYROLL_RUN_STATUS_LABEL / _TONE, the run state machine) until handled —
 * so it can't silently drift out of sync.
 */
export type PayrollRunStatus = NonNullable<
  components['schemas']['PayrollRunDto']['status']
>;

export type PayslipStatus =
  | 'DRAFT'
  | 'PAID'
  | 'HELD'
  | 'RELEASE_PENDING'
  | 'RELEASED';

export type OffCycleCategory = string;
export type PaymentMode = 'CASH' | 'BANK' | 'OTHER';
export type EmployeePaymentMode = 'CASH' | 'BANK_TRANSFER';

/**
 * Bonus runs share the payroll-run status enum on the wire, but only
 * DRAFT → COMPUTING → COMPUTED → APPROVED (plus FAILED / CANCELLED) are
 * reachable — there is no lock-attendance or post step.
 */
export type BonusRunStatus = PayrollRunStatus;
/** Bonus slot within a fiscal year. The API accepts 1–4; HR pays two. */
export type BonusNo = 1 | 2;
/** Share of salary a bonus run pays out. */
export type BonusPercentage = 25 | 50 | 75 | 100;
export type BonusPaymentMode = 'CASH' | 'BANK_TRANSFER';

export type IncrementBatchStatus = 'DRAFT' | 'APPLIED' | 'CANCELLED';
export type IncrementLineStatus = 'VALID' | 'ERROR' | 'APPLIED';

export type FiscalYearStatus = 'CLOSED' | 'CURRENT' | 'FUTURE';

export type SagaStatus = 'RUNNING' | 'COMPLETED' | 'FAILED';

/* ------------------------------ Banks --------------------------------- */

/**
 * Bank catalog row. The catalog also carries the company's *own* account at
 * each bank (used for disbursement source); employees pick a bank from this
 * controlled list when supplying their salary account.
 */
export type BankDto = {
  id: number;
  code: string;
  name: string;
  branch: string | null;
  address: string | null;
  /** Title of the company's account at this bank. */
  accountTitle: string | null;
  iban: string | null;
  accountNumber: string | null;
  active: boolean;
};

/* ------------------------- Salary components -------------------------- */

export type SalaryComponentDto = {
  id: number;
  code: string;
  name: string;
  type: ComponentType;
  calcType: ComponentCalcType;
  defaultAmount: number | null;
  displayOrder: number;
  calculateWithSalary: boolean;
  active: boolean;
};

export type CreateSalaryComponentRequest = {
  code: string;
  name: string;
  type: ComponentType;
  calcType?: ComponentCalcType;
  defaultAmount?: number;
  displayOrder?: number;
  calculateWithSalary?: boolean;
};

export type UpdateSalaryComponentRequest = {
  name?: string;
  defaultAmount?: number;
  displayOrder?: number;
  calculateWithSalary?: boolean;
  active?: boolean;
};

/* --------------------------- Employee salary -------------------------- */

export type EmployeeSalaryDto = {
  id: number;
  employeeId: number;
  basic: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  revisionReason: string | null;
};

export type ReviseBasicSalaryRequest = {
  basic: number;
  effectiveFrom: string;
  revisionReason?: string;
};

export type EmployeeSalaryComponentDto = {
  id: number;
  employeeId: number;
  componentId: number;
  componentCode: string;
  componentName: string;
  type: ComponentType;
  calcType: ComponentCalcType;
  amount: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  notes: string | null;
  calculateWithSalary: boolean;
};

export type AddSalaryLineRequest = {
  componentId: number;
  amount?: number;
  effectiveFrom: string;
  notes?: string;
};

export type UpdateSalaryLineRequest = {
  amount: number;
  effectiveFrom: string;
  notes?: string;
};

export type RemoveSalaryLineRequest = {
  effectiveTo: string;
};

/* ----------------------------- Payroll runs --------------------------- */

export type PayrollRunDto = {
  id: number;
  fiscalYearCode: string;
  periodYear: number;
  periodMonth: number;
  status: PayrollRunStatus;
  attendanceLocked: boolean;
  computedAt: string | null;
  approvedAt: string | null;
  postedAt: string | null;
  approvedBy: number | null;
  notes: string | null;
  /** Employees compute could not produce a payslip for. Drill-in via the
   *  skips endpoint; reset clears it. 0 (or absent on legacy rows) when
   *  compute hasn't run yet. */
  skippedCount: number;
};

/* --------------------------- Run skip rows ---------------------------- */

/**
 * One employee compute could not produce a payslip for. Persisted by the
 * payroll service so HR can fix the underlying data and retry without a
 * full reset of the run.
 */
export type PayrollRunSkipDto = {
  id: number;
  employeeId: number;
  employeeCode: string;
  employeeFullName: string;
  /** Stable error code (e.g. NO_BASIC_SALARY) — used for fix deeplinks. */
  errorCode: string;
  /** Human-readable reason from the compute step. */
  message: string;
  createdAt: string;
};

export type RetrySkipsRequest = {
  /** Subset to retry. Omit / empty array retries every skip on the run. */
  employeeIds?: number[];
};

/** Skip-row `errorCode` for a payslip HR undid by hand. */
export const MANUALLY_UNDONE = 'MANUALLY_UNDONE';

/**
 * A skipped employee's running permanent advance — the latest APPROVED one,
 * which compute deducts from. `advanceId` is null when nothing is outstanding.
 */
export type RunSkipAdvanceDto = {
  employeeId: number;
  advanceId: number | null;
  requestCode: string | null;
  /** Sum of every APPROVED permanent advance. */
  taken: number;
  /** Sum of every deduction already in the ledger. */
  deducted: number;
  outstanding: number;
  /** The advance's monthly installment. Not deducted this month when `waivedThisMonth`. */
  monthlyDeduction: number | null;
  /** HR waived the installment for the run's month: the retry deducts 0 and
   *  writes a 0 ledger row; the advance's installment is unchanged. */
  waivedThisMonth: boolean;
};

export type SetAdvanceWaiverRequest = {
  /** true: deduct 0 this month. false: deduct the usual installment. */
  waived: boolean;
};

export type CreatePayrollRunRequest = {
  year: number;
  month: number;
};

/* ------------------------------ Payslips ------------------------------ */

export type PayslipLineDto = {
  id: number;
  componentId: number | null;
  code: string;
  name: string;
  type: ComponentType;
  calcType: ComponentCalcType;
  rate: number | null;
  days: number | null;
  amount: number;
  displayOrder: number;
};

export type PayslipDto = {
  id: number;
  payrollRunId: number;
  employeeId: number;
  employeeCode: string;
  /** Period denormalized onto the payslip (V0004 migration). */
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
  /** Working days in the period (excludes weekends + holidays). */
  workingDays: number;
  /** Days the employee was paid for. */
  paidDays: number;
  /** Days marked absent. */
  absentDays: number;
  /** Holidays falling in the period. */
  holidaysInPeriod: number;
  /** Weekend (rest-day) count in the period. */
  weekendsInPeriod: number;
  otHours: number;
  otAmount: number;
  pdfObjectKey: string | null;
  generatedAt: string | null;
  status: PayslipStatus;
  heldReason: string | null;
  releasedInRunId: number | null;
  /** Present only on the detail read. */
  lines?: PayslipLineDto[];
};

export type HoldPayslipRequest = { reason: string };

export type PdfUrlResponse = { url: string };

/* ---------------------------- Off-cycle ------------------------------- */

export type OffCyclePaymentDto = {
  id: number;
  employeeId: number;
  paidOn: string;
  amount: number;
  reason: string;
  paymentMode: PaymentMode | null;
  referenceNo: string | null;
  /** Employee-service off-cycle payment type. Null on system rows. */
  offCyclePaymentTypeId: number | null;
  /** Type name snapshot taken when the payment was recorded. */
  offCyclePaymentTypeName: string | null;
  /** Non-null = sourced from a bonus installment apply. Immutable. */
  sourceEventId: number | null;
  sourceInstallmentId: number | null;
};

export type CreateOffCyclePaymentRequest = {
  employeeId: number;
  paidOn: string;
  amount: number;
  offCyclePaymentTypeId: number;
  reason: string;
  paymentMode: PaymentMode;
  referenceNo?: string;
};

export type UpdateOffCyclePaymentRequest = {
  amount?: number;
  offCyclePaymentTypeId?: number;
  reason?: string;
  paymentMode?: PaymentMode;
  referenceNo?: string;
  paidOn?: string;
};

/* ------------------- Bonus runs (bonus no settings) ------------------- */
//
// One settings row per (fiscal year, bonus no) fixes the share of salary
// paid. Compute writes one BonusTransaction per eligible employee.

export type BonusNoSettingsDto = {
  id: number;
  fiscalYearCode: string;
  bonusNo: number;
  percentageOfSalary: number;
  status: BonusRunStatus;
};

export type CreateBonusNoSettingsRequest = {
  /** "2026-27" */
  fiscalYearCode: string;
  bonusNo: BonusNo;
  percentageOfSalary: BonusPercentage;
};

export type UpdateBonusNoSettingsRequest = {
  percentageOfSalary?: BonusPercentage;
};

export type BonusTransactionDto = {
  id: number;
  fiscalYearCode: string;
  bonusNo: number;
  employeeId: number;
  employeeCode: string;
  /** Server-side employee enrichment; null when the lookup missed. */
  myEmployee: EmployeeSummary | null;
  dateOfJoining: string | null;
  basicSalary: number;
  percentageOfSalary: number;
  /**
   * Days the bonus is calculated for: the full fiscal year, or — for
   * employees who joined after the FY start — date of joining → FY end
   * (inclusive).
   */
  noOfDays: number;
  /** basicSalary × percentageOfSalary, prorated over noOfDays. */
  percentageSalaryAmount: number;
  deductionAsLongTermAdvance: number;
  rfdAdvanceAdded: number;
  netAmount: number;
  paymentMode: BonusPaymentMode | null;
  status: PayslipStatus;
  paidAt: string | null;
};

/* -------------------------- Increment batches ------------------------- */

export type IncrementBatchDto = {
  id: number;
  name: string;
  status: IncrementBatchStatus;
  effectiveFrom: string;
  sourceFilename: string | null;
  notes: string | null;
  appliedAt: string | null;
  appliedBy: number | null;
  lines?: IncrementBatchLineDto[];
};

export type IncrementBatchLineDto = {
  id: number;
  batchId: number;
  employeeId: number;
  oldBasic: number;
  newBasic: number;
  oldIncomeTax: number | null;
  newIncomeTax: number | null;
  effectiveFrom: string | null;
  status: IncrementLineStatus;
  errorMessage: string | null;
};

export type CreateIncrementBatchRequestLine = {
  employeeId: number;
  newBasic: number;
  newIncomeTax?: number;
  effectiveFrom?: string;
  notes?: string;
};

/** Body for POST /salary/increment-batches/{id}/lines. 409 if the employee
 *  is already in the batch. */
export type CreateIncrementBatchLineRequest = {
  employeeId: number;
  newBasic: number;
  newIncomeTax?: number;
  effectiveFrom?: string;
  notes?: string;
};

export type CreateIncrementBatchRequest = {
  name: string;
  effectiveFrom: string;
  notes?: string;
  lines: CreateIncrementBatchRequestLine[];
};

export type UpdateIncrementBatchRequest = {
  name?: string;
  effectiveFrom?: string;
  notes?: string;
};

export type UpdateIncrementBatchLineRequest = {
  newBasic?: number;
  newIncomeTax?: number;
  effectiveFrom?: string;
};

/* ---------------------- Bank + payroll settings ----------------------- */

export type EmployeeBankDetailsDto = {
  id: number;
  employeeId: number;
  /** FK into the banks catalog. */
  bankId: number;
  /** Catalog code (denormalized). */
  bankCode: string;
  /** Catalog display name (denormalized). */
  bankName: string;
  branch: string | null;
  accountTitle: string;
  iban: string;
  /** Returned only to HR / payroll roles. */
  accountNumber: string | null;
};

export type UpsertBankDetailsRequest = {
  /** FK into the banks catalog. */
  bankId: number;
  branch?: string;
  accountTitle: string;
  iban: string;
  accountNumber?: string;
};

export type EmployeePayrollSettingsDto = {
  id: number;
  employeeId: number;
  hireDate: string;
  eobiEnrolled: boolean;
  paymentMode: EmployeePaymentMode;
  /** When gratuity accrual began. Falls back to hireDate when null. */
  gratuityStartDate: string | null;
  /** When senior-allowance eligibility began. Null = ineligible. */
  seniorAllowanceStartDate: string | null;
};

export type UpdateEmployeePayrollSettingsRequest = {
  eobiEnrolled?: boolean;
  paymentMode?: EmployeePaymentMode;
  /** When gratuity accrual began. Set explicitly to override the hireDate fallback. */
  gratuityStartDate?: string | null;
  /** When senior-allowance eligibility began. Null = ineligible. */
  seniorAllowanceStartDate?: string | null;
};

/* --------------------------- Gratuity / settlement -------------------- */

export type GratuityDto = {
  employeeId: number;
  hireDate: string;
  /** Gratuity start date — falls back to hireDate when no override is set. */
  gratuityStartDate: string;
  asOf: string;
  totalServiceMonths: number;
  eligible: boolean;
  completedYears: number;
  /** Number of gratuity units the payout is computed on. */
  noOfGratuities: number;
  lastBasic: number;
  gratuity: number;
  configId: number | null;
};

export type SettlementEmployeeSummary = {
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  department: string;
  section: string;
  designation: string;
  grade: string;
  gradeType: string;
  hireDate: string;
  gratuityStartDate: string;
};

export type SettlementBonusEvent = {
  bonusEventId: number;
  eventName: string;
  eventType: string;
  referenceDate: string;
  eligibleAmount: number;
  paidAmount: number;
  outstandingAmount: number;
};

export type SettlementLeaveSummary = {
  leaveTypeId: number;
  leaveCode: string;
  leaveName: string;
  cycleStart: string;
  cycleEnd: string;
  allocated: number;
  accrued: number;
  used: number;
  pending: number;
  payableDays: number;
  recoverableDays: number;
  encashmentAmount: number;
  recoveryAmount: number;
};

export type SettlementLeaveTypeSummary = {
  leaveTypeId: number;
  leaveCode: string;
  leaveName: string;
  cycleStart: string;
  cycleEnd: string;
  encashable: boolean;
  allocated: number;
  earned: number;
  used: number;
  pending: number;
  payableDays: number;
  recoverableDays: number;
  encashmentAmount: number;
  recoveryAmount: number;
};

export type SettlementExcessiveLeaveSummary = {
  excessiveDays: number;
  excessiveAmount: number;
};

export type SettlementPermanentAdvanceDetail = {
  advanceId: number;
  requestCode: string;
  receivingDate: string;
  originalAmount: number;
  recoveredAmount: number;
  outstandingAmount: number;
  monthlyDeduction: number;
  bonusOffset: number;
  annualLeaveOffset: number;
};

export type SettlementOvertimeBucket = {
  minutes: number;
  hours: string;
};

export type SettlementApprovedOvertime = {
  normal: SettlementOvertimeBucket;
  extra: SettlementOvertimeBucket;
  special: SettlementOvertimeBucket;
  total: SettlementOvertimeBucket;
};

export type SettlementPendingOvertime = SettlementApprovedOvertime & {
  pendingRequests: number;
};

export type SettlementUnappliedOvertime = SettlementApprovedOvertime & {
  attendanceDays: number;
};

export type SettlementAttendanceSummary = {
  normalOvertime:{
    approved:{
      minutes: number;
      hours: string;
      amount: number;
    },
    pending:{
      minutes: number;
      hours: string;
      amount: number;
    },
    total:{
      minutes: number;
      hours: string;
      amount: number;
    },
    unapplied:{
      minutes: number;
      hours: string;
      amount: number;
    }
  };
  employeeId: number;
  from: string;
  to: string;
  attendance: {
    presentDays: number;
    halfDays: number;
    leaveDays: number;
    absentDays: number;
    weekendDays: number;
    holidayDays: number;
  };
  approvedOvertime: SettlementApprovedOvertime;
  pendingOvertime: SettlementPendingOvertime;
  unappliedOvertime: SettlementUnappliedOvertime;
};

export type SettlementAdjustmentType = 'NONE' | 'NOTICE_PAY' | 'FORCED_DEDUCTION';

/** Body for POST /settlement/employees — submits the final settlement. */
export type SubmitSettlementRequest = {
  employeeId: number;
  /** YYYY-MM-DD */
  lastWorkingDay: string;
  settlementAdjustmentType: SettlementAdjustmentType;
  /** `NONE` or `PERCENT_<n>`. */
  noticePayPercentage: string;
  /** `PERCENT_<n>` (`PERCENT_0` when not applied). */
  forcedDeductionPercentage: string;
  bonusRate: number;
  remarks?: string;
};

export type SettlementDto = {
  id: number;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  lastWorkingDay: string;
  grossAmount: number;
  totalDeductions: number;
  netAmount: number;
  status: string;
  paymentDate: string | null;
  remarks: string | null;
  createdAt: string;
};

export type FinalSettlementStatus = 'DRAFT' | 'HR_APPROVED' | 'PAID' | 'REJECTED' | 'CANCELLED';

/** Row of GET /settlement/employees. */
export type FinalSettlementListDto = {
  settlementId: number;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  department: string | null;
  section: string | null;
  designation: string | null;
  grade: string | null;
  gradeType: 'MANAGEMENT' | 'SUPERVISOR' | 'LABOUR' | null;
  hireDate: string | null;
  lastWorkingDay: string;
  status: FinalSettlementStatus;
  grossSettlement: number;
  totalDeductions: number;
  netSettlement: number;
  remarks: string | null;
  createdAt: string;
};

/** GET /settlement/employees/submitted-settlements/{settlementId}. */
export type FinalSettlementDetailsDto = {
  settlementId: number;
  status: FinalSettlementStatus;
  lastWorkingDay: string;
  remarks: string | null;
  employee: {
    employeeId: number;
    employeeCode: string;
    employeeName: string;
    department: string | null;
    section: string | null;
    designation: string | null;
    grade: string | null;
    gradeType: string | null;
    hireDate: string | null;
    gratuityStartDate: string | null;
  };
  earnedSalary: {
    monthlyBasicSalary: number;
    earnedBasicSalary: number;
    allowances: number;
    grossSalary: number;
  };
  gratuity: { eligible: boolean; completedYears: number; amount: number };
  advances: { permanent: number; shortTerm: number; rfd: number; total: number };
  permanentAdvances: {
    advances: {
      advanceId: number;
      requestCode: string;
      receivingDate: string;
      advanceStatus: string;
      description: string | null;
      originalAmount: number;
      recoveredAmount: number;
      outstandingAmount: number;
      monthlyDeduction: number;
      bonusOffset: number;
      annualLeaveOffset: number;
      bonusRecovered: number;
      leaveRecovered: number;
      settlementRecovered: number;
    }[];
    totalOutstanding: number;
  };
  bonus: {
    outstanding: number;
    events: {
      bonusEventId: number;
      eventName: string;
      eventType: string;
      referenceDate: string;
      eligibleAmount: number;
      paidAmount: number;
      outstandingAmount: number;
    }[];
  };
  annualLeave: {
    leaveTypeId: number;
    leaveCode: string;
    leaveName: string;
    cycleStart: string;
    cycleEnd: string;
    allocated: number;
    accrued: number;
    used: number;
    pending: number;
    payableDays: number;
    excessiveDays: number;
    encashmentAmount: number;
    excessiveAmount: number;
  } | null;
  medicalAndCasualLeave: {
    allocated: number;
    earned: number;
    used: number;
    pending: number;
    payableDays: number;
    excessiveDays: number;
    encashmentAmount: number;
    excessiveAmount: number;
  } | null;
  annualExcessiveLeave: { excessiveDays: number; excessiveAmount: number } | null;
  medicalAndCasualExcessiveLeave: { excessiveDays: number; excessiveAmount: number } | null;
  fairPriceShop: { outstandingAmount: number } | null;
  canteen: { amount: number } | null;
  outstandingDeductions: { deductionCode: string; deductionName: string; amount: number }[];
  attendanceDetail: {
    periodFrom: string;
    periodTo: string;
    presentDays: number;
    halfDays: number;
    leaveDays: number;
    paidLeaveDays: number;
    unpaidLeaveDays: number;
    absentDays: number;
    weekendDays: number;
    holidayDays: number;
  } | null;
  normalOvertimeDetail: {
    approvedMinutes: number;
    approvedAmount: number;
    pendingMinutes: number;
    pendingAmount: number;
    unappliedMinutes: number;
    unappliedAmount: number;
    totalMinutes: number;
    totalAmount: number;
  } | null;
  totals: {
    grossSettlement: number;
    totalDeductions: number;
    tax: number;
    netSettlement: number;
  };
  audit: {
    createdBy: number | null;
    createdAt: string | null;
    approvedBy: number | null;
    approvedAt: string | null;
    paidBy: number | null;
    paidAt: string | null;
    updatedAt: string | null;
  };
};

export type ListFinalSettlementsParams = {
  employeeId?: number;
  employeeCode?: string;
  employeeName?: string;
  status?: FinalSettlementStatus;
  page?: number;
  size?: number;
  sort?: string;
};

export type SettlementPreviewDto = {
  employeeId: number;
  lastWorkingDay: string;
  terminationPolicy?: {
    adjustmentType: string;
    noticePayPercentage: number | string;
    noticePayAmount: number | string;
    forcedDeductionPercentage: number | string;
    forcedDeductionAmount: number | string;
  };
  employee: SettlementEmployeeSummary;
  earnedSalary: {
    monthlyBasicSalary: number;
    earnedBasic: number;
    allowances: number;
    gross: number;
  };
  gratuity: {
    eligible: boolean;
    completedYears: number;
    amount: number;
  };
  advances: {
    permanent: number;
    shortTerm: number;
    total: number;
  };
  permanentAdvances: {
    advances: SettlementPermanentAdvanceDetail[];
    totalOutstanding: number;
  };
  bonus: {
    name: string;
    outstanding: number;
    events: SettlementBonusEvent[];
  };
  leaveSummary: SettlementLeaveSummary | null;
  attendanceSummary: SettlementAttendanceSummary | null;
  annualLeave?: SettlementLeaveTypeSummary | null;
  annualExcessiveLeave?: SettlementExcessiveLeaveSummary | null;
  medicalAndCasualLeave?: SettlementLeaveTypeSummary | null;
  medicalAndCasualExcessiveLeave?: SettlementExcessiveLeaveSummary | null;
  fairPriceShop: {
    outstandingAmount: number;
  };
  outstandingDeductions: { code: string; amount: number }[];
  grossSettlement: number;
  totalDeductions: number;
  tax: number;
  net: number;
};

/* ------------------------- Payroll config + FY ------------------------ */

export type PayrollConfigDto = {
  id: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  eobiAmount: number;
  pessiRatePercent: number;
  pessiSalaryCap: number;
  gratuityMinServiceMonths: number;
  gratuityPartialYearThresholdMonths: number;
  canteenPerDayRateManagement: number | null;
  canteenPerDayRateOther: number | null;
  notes: string | null;
};

export type CreatePayrollConfigRequest = {
  effectiveFrom: string;
  eobiAmount: number;
  pessiRatePercent: number;
  pessiSalaryCap: number;
  gratuityMinServiceMonths?: number;
  gratuityPartialYearThresholdMonths?: number;
  canteenPerDayRateManagement?: number;
  canteenPerDayRateOther?: number;
  notes?: string;
};

export type FiscalYearDto = {
  code: string;
  startDate: string;
  endDate: string;
  status: FiscalYearStatus;
};

export type CreateFiscalYearRequest = {
  code: string;
  startDate: string;
  endDate: string;
};

export type UpdateFiscalYearRequest = {
  status: FiscalYearStatus;
};

/* ------------------------------- Sagas -------------------------------- */

export type SagaResponse = {
  id: number;
  sagaName: string;
  correlationKey: string;
  status: SagaStatus;
  currentStep: string | null;
  stepIndex: number;
  attemptCount: number;
  nextAttemptAt: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy: string | null;
};

/** Per-saga-name rollup row from GET /payroll/sagas/health. */
export type SagaHealthPerName = {
  name: string;
  status: string;
  failureRate24h: number[];
};

/** Response from GET /payroll/sagas/health. */
export type SagaHealthSnapshot = {
  running: number;
  failed24h: number;
  perName: SagaHealthPerName[];
};

/* ------------------------------ Advances ------------------------------ */
//
// Three-stage approval (Manager → HR → Accounts) → APPROVED. APPROVED is the
// live state: the payroll run deducts against it directly (short-term in full
// in its target month, permanent in monthly installments). There is no
// disbursement step and no CLOSED / DEDUCTED marker.
// Money is rupees on the wire (BigDecimal scale 2 server side; JSON number
// at the boundary).

/**
 * Three-stage chain: PENDING (with Manager) -> MANAGER_APPROVED (with HR) ->
 * HR_APPROVED (with Accounts) -> APPROVED. Confirmed
 * against a live 409 from POST .../hr-approve: "batch must be in
 * MANAGER_APPROVED (was PENDING)" — `MANAGER_APPROVED` is a real status the
 * backend returns, not a derived value; earlier FE code treated `status` as
 * only ever PENDING/HR_APPROVED/terminal (a stale two-stage HR → Accounts
 * assumption) and silently dropped this value.
 */
export type AdvanceStatus =
  | 'PENDING'
  | 'MANAGER_APPROVED'
  | 'HR_APPROVED'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED';

/**
 * Which approver the request is currently sitting with. Present while
 * in-flight; null once decided/terminal. Read this (or
 * `currentStage.stageRole`) for display/routing instead of `status` where
 * possible — it's the field the backend documents as authoritative — but
 * note `status` *does* also distinguish the three stages 1:1 (PENDING /
 * MANAGER_APPROVED / HR_APPROVED), so either works as a source of truth.
 */
export type AdvanceApprovalStage = 'MANAGER_APPROVAL' | 'HR_APPROVAL' | 'ACCOUNTS_APPROVAL';

export type AdvanceCurrentStageDto = {
  stageNo: number;
  /** e.g. "Manager Approval" / "HR Approval" / "Accounts Approval". */
  stageRole: string;
  assignedTo: number | null;
  assignedToName: string | null;
};

/** A line stays APPROVED once recovered; CANCELLED when its batch is cancelled. */
export type ShortTermAdvanceLineStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED';

/** Long-term advance against future salary. */
export type PermanentAdvanceDto = {
  id: number;
  requestCode: string | null;
  employeeId: number;
  amount: number;
  monthlyDeduction: number;
  /** Recovered from bonus #1 of `fiscalYearCode` when that bonus is paid. */
  bonusOffset: number;
  /** Recovered from bonus #2 of the same fiscal year. Zero when the request
   *  only targets bonus #1. */
  bonusOffset2: number | null;
  /** Fiscal year the two bonus offsets belong to. Null when the request has
   *  no bonus offset at all. Note the create request spells this
   *  `financialYearCode` — the response uses `fiscalYearCode`. */
  fiscalYearCode: string | null;
  annualLeaveOffset: number;
  /** Month the "other deduction" (AL encashment) offset lands in. First of
   *  the month; null when there's no AL offset. */
  annualLeaveMonth: string | null;
  gratuityAtRequest: number | null;
  receivingDate: string | null;
  description: string | null;
  status: AdvanceStatus;
  /** Which approver holds this now. See {@link AdvanceApprovalStage}. */
  approvalStage: AdvanceApprovalStage | null;
  currentStage: AdvanceCurrentStageDto | null;
  deductedTotal: number | null;
  remainingBalance: number | null;
  hrDecidedBy: number | null;
  hrDecidedAt: string | null;
  hrNote: string | null;
  accountsDecidedBy: number | null;
  accountsDecidedAt: string | null;
  accountsNote: string | null;
  rejectionReason: string | null;
  disbursedAt: string | null;
  disbursedBy: number | null;
  closedAt: string | null;
  createdAt: string;
};

export type CreatePermanentAdvanceRequest = {
  employeeId: number;
  amount: number;
  monthlyDeduction: number;
  /** Deduction against bonus #1. Requires `financialYearCode`. */
  bonusOffset?: number;
  /** Deduction against bonus #2 of the same fiscal year. */
  bonusOffset2?: number;
  /** Fiscal year the bonus offsets are taken from, e.g. "2026-27". Spelled
   *  `financialYearCode` on the request but `fiscalYearCode` on the DTO. */
  financialYearCode?: string;
  /** "Other deduction" — taken from AL encashment in `annualLeaveMonth`. */
  annualLeaveOffset?: number;
  /** YYYY-MM-DD, first of the month the AL offset applies to. */
  annualLeaveMonth?: string;
  gratuityAtRequest?: number;
  receivingDate?: string;
  description?: string;
};

/** Optional approver note on HR / Accounts approve. */
export type AdvanceDecisionRequest = {
  note?: string;
};

/**
 * One row of `GET /permanent-advances/hr-approved-receiving-list` — advances
 * HR has approved that Accounts hasn't yet (status HR_APPROVED), which HR
 * prints as the receiving list / advance slip.
 *
 * The backend enriches each advance with the `employee` summary (code, name,
 * department, section, designation) and the employee's date of joining.
 */
export type PermanentAdvanceReceivingRow = PermanentAdvanceDto & {
  /** Employee card number; falls back to `employee.employeeCode`. */
  cardNo?: string | null;
  employee?: EmployeeSummary | null;
  /** Employee's date of joining, "YYYY-MM-DD". */
  dateOfJoining?: string | null;
};

export type HrApprovedReceivingListParams = {
  employeeId?: number;
  page?: number;
  size?: number;
};

/** `POST /permanent-advances/{id}/hr-adjust-before-approval` — HR revises the
 *  figures while the advance waits on HR (MANAGER_APPROVED). Both amounts
 *  must be positive and `monthlyDeduction` can't exceed `amount` (409). */
export type AdjustPermanentAdvanceRequest = {
  amount: number;
  monthlyDeduction: number;
  note?: string;
};

/** Reject body — reason required. */
export type AdvanceRejectRequest = {
  reason: string;
};

/**
 * Manual / off-cycle deduction against the employee's permanent advances.
 * Capped at their remaining balance. Blank description defaults server-side
 * to "Manual deduction for {employeeCode}".
 */
export type RecordPermanentAdvanceDeductionRequest = {
  deductedAmount: number;
  transactionDate: string;
  description?: string;
};

/** Where a permanent-advance deduction was recovered from. */
export type PermanentAdvanceDeductionSource =
  | 'SALARY'
  | 'BONUS'
  | 'ANNUAL_LEAVE'
  | 'MANUAL'
  | 'SETTLEMENT';

/**
 * One recovery in an employee's permanent-advance ledger. Deductions belong
 * to the employee (balance = APPROVED advances − all deductions), not to one
 * advance. Only MANUAL rows can be deleted; the others are removed by undoing
 * their source (payroll run reset, settlement reject/cancel).
 */
export type PermanentAdvanceDeductionDto = {
  id: number;
  employeeId: number;
  deductedAmount: number;
  source: PermanentAdvanceDeductionSource;
  /** Payroll run / bonus transaction / annual leave transaction / settlement id, by source. Null for MANUAL. */
  sourceRefId: number | null;
  remainingBalance: number;
  transactionDate: string;
  description: string | null;
  createdAt: string;
};

/** One line inside a short-term advance batch — single-month deduction. */
export type ShortTermAdvanceLineDto = {
  id: number;
  batchId: number;
  employeeId: number;
  /** Code / name / department / section / designation, resolved server-side
   *  from the shared employee cache. Null if the employee can't be resolved. */
  employee?: EmployeeSummary | null;
  amount: number;
  targetYear: number;
  targetMonth: number;
  status: ShortTermAdvanceLineStatus;
  rejectionReason: string | null;
  deductedInRunId: number | null;
};

export type ShortTermAdvanceLineInput = {
  employeeId: number;
  amount: number;
};

export type CreateShortTermAdvanceBatchRequest = {
  departmentId?: number;
  sectionId?: number;
  targetYear: number;
  /** 1..12 — the payroll month to deduct in. */
  targetMonth: number;
  notes?: string;
  lines: ShortTermAdvanceLineInput[];
};

export type ShortTermAdvanceBatchDto = {
  id: number;
  requestCode: string | null;
  departmentId: number | null;
  sectionId: number | null;
  targetYear: number;
  targetMonth: number;
  totalAmount: number;
  totalEmployees: number;
  status: AdvanceStatus;
  /** Which approver holds this now. See {@link AdvanceApprovalStage}. */
  approvalStage: AdvanceApprovalStage | null;
  currentStage: AdvanceCurrentStageDto | null;
  hrDecidedBy: number | null;
  hrDecidedAt: string | null;
  hrNote: string | null;
  accountsDecidedBy: number | null;
  accountsDecidedAt: string | null;
  accountsNote: string | null;
  rejectionReason: string | null;
  notes: string | null;
  /** Per-employee detail. Included on detail reads, omitted on list pages. */
  lines: ShortTermAdvanceLineDto[] | null;
  createdAt: string;
};

/* -------------------------- Annual Leave ------------------------------ */

/** Denormalized employee snapshot embedded on an AnnualLeaveDto row. */
export type AnnualLeaveEmployeeSnapshot = {
  id: number;
  fullName: string;
  departmentId: number | null;
  departmentName: string | null;
  sectionId: number | null;
  sectionName: string | null;
  designationId: number | null;
  designationName: string | null;
  profilePictureUrl: string | null;
};

/** One calculated annual-leave / LTA row — GET /annual-leave. */
export type AnnualLeaveDto = {
  id: number;
  employeeId: number;
  employee: AnnualLeaveEmployeeSnapshot | null;
  employeeCode: string;
  /** ISO date of the calculation month (YYYY-MM-DD). */
  calculationMonth: string;
  basicSalary: number;
  perDaySalary: number;
  /** Leave-cycle window this calculation falls in. */
  cycleStart: string;
  cycleEnd: string;
  eligibleLeaveDays: number;
  availedLeaveDays: number;
  payableLeaveDays: number;
  leaveAmount: number;
  grossAnnualLeaveAmount: number;
  /** Long-term advance deduction. */
  deductionAsLongTermAdvance: number;
  netAmount: number;
  /** null = no decision yet ("Pending"); false = marked unpaid (leave days carried forward). */
  paymentStatus: boolean | null;
  /** null = never locked. */
  lockStatus: boolean | null;
  joiningDate: string;
};

/** Per-employee result inside AnnualLeaveCalculationResultDto. */
export type AnnualLeaveEmployeeResult = {
  employeeId: number;
  cardNo: string;
  status: string;
  message: string;
};

/** Response from POST /annual-leave — bulk calculation result. */
export type AnnualLeaveCalculationResultDto = {
  year: number;
  month: number;
  totalEmployeesProcessed: number;
  successCount: number;
  failedCount: number;
  successfulEmployees: AnnualLeaveEmployeeResult[];
  failedEmployees: AnnualLeaveEmployeeResult[];
};

export type CalculateAnnualLeaveRequest = {
  year: number;
  month: number;
};

export type LockAnnualLeaveMonthRequest = {
  year: number;
  month: number;
};

/** Response from POST /annual-leave/lock. */
export type AnnualLeaveLockMonthResultDto = {
  year: number;
  month: number;
  lockedCount: number;
};

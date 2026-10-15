// Typed wrappers for payroll-service. Mirrors
// `docs/services/payroll.openapi.json`. Routed through the gateway via
// `src/lib/http.ts` (bearer + idempotency + 401-refresh-retry).

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';
import { downloadReportBlob, fetchReportFile, type ReportFile } from '@/reports/download';
import type { SlipLayout } from '@/reports/slip-layout';
import type { SlipLanguage } from '@/reports/accounts/types';
import type {
  AddSalaryLineRequest,
  AdvanceDecisionRequest,
  AdjustPermanentAdvanceRequest,
  HrApprovedReceivingListParams,
  PermanentAdvanceReceivingRow,
  AdvanceRejectRequest,
  AdvanceStatus,
  BankDto,
  BonusNoSettingsDto,
  BonusPaymentMode,
  BonusRunStatus,
  BonusTransactionDto,
  ComponentType,
  CreateBonusNoSettingsRequest,
  CreateFiscalYearRequest,
  CreateIncrementBatchLineRequest,
  CreateIncrementBatchRequest,
  CreateOffCyclePaymentRequest,
  CreatePayrollRunRequest,
  CreatePermanentAdvanceRequest,
  CreateSalaryComponentRequest,
  CreateShortTermAdvanceBatchRequest,
  CreatePayrollConfigRequest,
  EmployeeBankDetailsDto,
  EmployeePayrollSettingsDto,
  EmployeeSalaryComponentDto,
  EmployeeSalaryDto,
  FiscalYearDto,
  GratuityDto,
  HoldPayslipRequest,
  IncrementBatchDto,
  IncrementBatchLineDto,
  IncrementBatchStatus,
  OffCycleCategory,
  OffCyclePaymentDto,
  PageResponse,
  PayrollRunDto,
  PayrollRunSkipDto,
  PayslipDto,
  PayslipStatus,
  PdfUrlResponse,
  PermanentAdvanceDeductionDto,
  PermanentAdvanceDto,
  RecordPermanentAdvanceDeductionRequest,
  RetrySkipsRequest,
  RunSkipAdvanceDto,
  SetAdvanceWaiverRequest,
  RemoveSalaryLineRequest,
  ReviseBasicSalaryRequest,
  SagaHealthSnapshot,
  SagaResponse,
  SagaStatus,
  SalaryComponentDto,
  FinalSettlementDetailsDto,
  FinalSettlementListDto,
  ListFinalSettlementsParams,
  SettlementDto,
  SettlementPreviewDto,
  SubmitSettlementRequest,
  ShortTermAdvanceBatchDto,
  ShortTermAdvanceLineDto,
  PayrollConfigDto,
  UpdateBonusNoSettingsRequest,
  UpdateEmployeePayrollSettingsRequest,
  UpdateFiscalYearRequest,
  UpdateIncrementBatchLineRequest,
  UpdateIncrementBatchRequest,
  UpdateOffCyclePaymentRequest,
  UpdateSalaryComponentRequest,
  UpdateSalaryLineRequest,
  UpsertBankDetailsRequest,
  AnnualLeaveDto,
  AnnualLeaveCalculationResultDto,
  CalculateAnnualLeaveRequest,
  LockAnnualLeaveMonthRequest,
  AnnualLeaveLockMonthResultDto,
} from './types';

/* ------------------------------- Banks -------------------------------- */

export const banksApi = {
  list: (params: { includeInactive?: boolean } = {}, signal?: AbortSignal) =>
    http.get<BankDto[]>(`/banks${qs({ ...params })}`, { signal }),
  get: (id: number, signal?: AbortSignal) =>
    http.get<BankDto>(`/banks/${id}`, { signal }),
};

/* ----------------------- Salary catalog ------------------------------- */

export const salaryComponentsApi = {
  list: (params: { type?: ComponentType; active?: boolean } = {}, signal?: AbortSignal) =>
    http.get<SalaryComponentDto[]>(`/salary/components${qs({ ...params })}`, { signal }),
  create: (body: CreateSalaryComponentRequest) =>
    http.post<SalaryComponentDto>('/salary/components', body),
  update: (id: number, body: UpdateSalaryComponentRequest) =>
    http.patch<SalaryComponentDto>(`/salary/components/${id}`, body),
};

/* --------------------------- Employee salary -------------------------- */

export const employeeSalaryApi = {
  current: (employeeId: number, signal?: AbortSignal) =>
    http.get<EmployeeSalaryDto>(`/salary/employees/${employeeId}`, { signal }),
  history: (employeeId: number, signal?: AbortSignal) =>
    http.get<EmployeeSalaryDto[]>(`/salary/employees/${employeeId}/history`, { signal }),
  revise: (employeeId: number, body: ReviseBasicSalaryRequest) =>
    http.post<EmployeeSalaryDto>(`/salary/employees/${employeeId}`, body),
};

export const employeeSalaryLinesApi = {
  list: (employeeId: number, type?: ComponentType, signal?: AbortSignal) =>
    http.get<EmployeeSalaryComponentDto[]>(
      `/employees/${employeeId}/salary-components${qs({ type })}`,
      { signal },
    ),
  history: (employeeId: number, signal?: AbortSignal) =>
    http.get<EmployeeSalaryComponentDto[]>(
      `/employees/${employeeId}/salary-components/history`,
      { signal },
    ),
  add: (employeeId: number, body: AddSalaryLineRequest) =>
    http.post<EmployeeSalaryComponentDto>(`/employees/${employeeId}/salary-components`, body),
  update: (employeeId: number, lineId: number, body: UpdateSalaryLineRequest) =>
    http.patch<EmployeeSalaryComponentDto>(
      `/employees/${employeeId}/salary-components/${lineId}`,
      body,
    ),
  remove: (employeeId: number, lineId: number, body: RemoveSalaryLineRequest) =>
    http.post<void>(`/employees/${employeeId}/salary-components/${lineId}/stop`, body),
};

/* ----------------------------- Payroll runs --------------------------- */

export const payrollRunsApi = {
  list: (fiscalYear?: string, signal?: AbortSignal) =>
    http.get<PayrollRunDto[]>(`/payroll/runs${qs({ fiscalYear })}`, { signal }),
  get: (id: number, signal?: AbortSignal) =>
    http.get<PayrollRunDto>(`/payroll/runs/${id}`, { signal }),
  create: (body: CreatePayrollRunRequest) =>
    http.post<PayrollRunDto>('/payroll/runs', body),
  lockAttendance: (id: number) =>
    http.post<PayrollRunDto>(`/payroll/runs/${id}/lock-attendance`),
  compute: (id: number) => http.post<PayrollRunDto>(`/payroll/runs/${id}/compute`),
  approve: (id: number) => http.post<PayrollRunDto>(`/payroll/runs/${id}/approve`),
  post: (id: number) => http.post<PayrollRunDto>(`/payroll/runs/${id}/post`),
  cancel: (id: number) => http.post<PayrollRunDto>(`/payroll/runs/${id}/cancel`),
  /**
   * Roll a COMPUTED run back to LOCKED so the operator can fix employee
   * data and re-run /compute. Drops every payslip on the run; the
   * attendance period stays locked. Only valid in COMPUTED.
   */
  reset: (id: number) => http.post<PayrollRunDto>(`/payroll/runs/${id}/reset`),
  /**
   * Run-scoped payslip list. Spec accepts `status` and `q` only —
   * server-side pagination is not exposed as query params (Spring's
   * Pageable is bound but unsurfaced). For UI use, prefer
   * `payrollComposites.run()` which resolves employee names server-side.
   */
  payslips: (
    id: number,
    params: { status?: PayslipStatus; q?: string } = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<PayslipDto>>(
      `/payroll/runs/${id}/payslips${qs({ ...params })}`,
      { signal },
    ),
  /**
   * Employees compute could not produce a payslip for. Server-paginated;
   * `size` defaults to ~20 server side.
   */
  skips: (
    id: number,
    params: { page?: number; size?: number } = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<PayrollRunSkipDto>>(
      `/payroll/runs/${id}/skips${qs({ ...params })}`,
      { signal },
    ),
  /**
   * Re-run compute for previously-skipped employees on a COMPUTED run.
   * Empty / omitted body retries every skip; ids that aren't currently
   * skipped are quietly ignored. Run stays COMPUTED.
   */
  retrySkips: (id: number, body: RetrySkipsRequest = {}) =>
    http.post<PayrollRunDto>(`/payroll/runs/${id}/retry-skips`, body),
  /**
   * Undo one DRAFT payslip on a COMPUTED run: drops it with its RFD row and
   * permanent-advance deduction, and parks the employee on the skip list
   * (`MANUALLY_UNDONE`) to be brought back with `retrySkips`.
   */
  undoPayslip: (id: number, payslipId: number) =>
    http.post<PayrollRunDto>(`/payroll/runs/${id}/payslips/${payslipId}/undo`),
  /** Running permanent advance of an employee skipped on the run. */
  skipAdvance: (id: number, employeeId: number, signal?: AbortSignal) =>
    http.get<RunSkipAdvanceDto>(
      `/payroll/runs/${id}/skips/${employeeId}/permanent-advance`,
      { signal },
    ),
  /**
   * Waive (or restore) the skipped employee's permanent-advance installment
   * for the run's month. The advance's own installment is not changed.
   */
  setSkipAdvanceWaiver: (
    id: number,
    employeeId: number,
    body: SetAdvanceWaiverRequest,
  ) =>
    http.put<RunSkipAdvanceDto>(
      `/payroll/runs/${id}/skips/${employeeId}/permanent-advance/waiver`,
      body,
    ),
  /**
   * The payslip a retry would compute for this skipped employee right now
   * (waiver included). Computed and rolled back — nothing is saved.
   */
  previewSkip: (id: number, employeeId: number, signal?: AbortSignal) =>
    http.get<PayslipDto>(`/payroll/runs/${id}/skips/${employeeId}/preview`, {
      signal,
    }),
};

/* ------------------------------ Payslips ------------------------------ */

export const payslipsApi = {
  /**
   * Self-serve: list payslips for an employee + year. Spec accepts `year`
   * only; default sort is `periodMonth desc`. ESS surfaces should prefer
   * `payrollComposites.payslipsMine()` which adds period labels and signed PDF
   * URLs in one shot.
   */
  forEmployee: (
    employeeId: number,
    params: { year?: number } = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<PayslipDto>>(
      `/payslips/employees/${employeeId}${qs({ ...params })}`,
      { signal },
    ),
  /**
   * Manager scope: the caller's direct reports' payslips for `year`
   * (optionally narrowed to a single `month`). Server resolves the team
   * via employee-service `/me/team`; DRAFT rows are hidden from managers
   * (only HR / ACCOUNTS with `payroll.read` see DRAFT). Empty page if the
   * caller has no reports. Requires `payroll.read.team` or `payroll.read`.
   */
  forTeam: (
    params: { year: number; month?: number; page?: number; size?: number } = {
      year: new Date().getFullYear(),
    },
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<PayslipDto>>(
      `/payslips/team${qs({ ...params })}`,
      { signal },
    ),
  get: (id: number, signal?: AbortSignal) =>
    http.get<PayslipDto>(`/payslips/${id}`, { signal }),
  pdfUrl: (id: number, signal?: AbortSignal) =>
    http.get<PdfUrlResponse>(`/payslips/${id}/pdf-url`, { signal }),
  /** Reporting / queue list. */
  byStatus: (status: PayslipStatus, signal?: AbortSignal) =>
    http.get<PageResponse<PayslipDto>>(`/payslips${qs({ status })}`, { signal }),
  hold: (id: number, body: HoldPayslipRequest) =>
    http.post<PayslipDto>(`/payslips/${id}/hold`, body),
  unhold: (id: number, body: HoldPayslipRequest) =>
    http.post<PayslipDto>(`/payslips/${id}/unhold`, body),
  release: (id: number, body: HoldPayslipRequest) =>
    http.post<PayslipDto>(`/payslips/${id}/release`, body),
};

/* --------------------------- Payroll reports -------------------------- */

export const payrollReportsApi = {
  /**
   * Every payslip of a run as one PDF, rendered on demand so it works in
   * any run status. `layout`: `full` = one slip per page, `sheet` = three
   * per page; `language`: `en` | `ur` (both default server-side). Needs the
   * bearer token, so it's fetched as a blob and saved under the server's
   * `Content-Disposition` name (fallback `payslips-run-{runId}.pdf`).
   * 400 `REPORT_PARAM_INVALID` on a bad layout / language; 404
   * `PAYROLL_RUN_NOT_FOUND` / `PAYSLIP_NOT_FOUND` when the run is missing
   * or has no payslips. Requires `payroll.reports.run`.
   */
  downloadRunPayslipsPdf: (
    runId: number,
    opts: { layout?: SlipLayout; language?: SlipLanguage } = {},
    signal?: AbortSignal,
  ) =>
    downloadReportBlob(
      `/reports/payroll/runs/${runId}/payslips-pdf${qs({ ...opts })}`,
      `payslips-run-${runId}.pdf`,
      { signal },
    ),

  /**
   * Final settlement / dues statement — one A4 page of the saved figures.
   * Any status prints; DRAFT / REJECTED / CANCELLED carry a red chip in the
   * PDF itself. Returned as a blob so the caller can open it in a new tab.
   * 404 `FINAL_SETTLEMENT_NOT_FOUND`; 502 when the employee-service lookup
   * (father name, CNIC, photo) fails. Requires `payroll.reports.run`.
   */
  settlementStatement: (settlementId: number, signal?: AbortSignal): Promise<ReportFile> =>
    fetchReportFile(
      `/reports/payroll/settlements/${settlementId}/statement`,
      `settlement-statement-${settlementId}.pdf`,
      { accept: 'application/pdf', signal },
    ),
};

/* --------------------------- Off-cycle payments ----------------------- */

export type ListOffCycleParams = {
  employeeId?: number;
  from?: string;
  to?: string;
  category?: OffCycleCategory;
  size?:number;
};

export const offCycleApi = {
  list: (params: ListOffCycleParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<OffCyclePaymentDto>>(`/off-cycle-payments${qs({ ...params })}`, {
      signal,
    }),
  get: (id: number, signal?: AbortSignal) =>
    http.get<OffCyclePaymentDto>(`/off-cycle-payments/${id}`, { signal }),
  create: (body: CreateOffCyclePaymentRequest) =>
    http.post<OffCyclePaymentDto>('/off-cycle-payments', body),
  update: (id: number, body: UpdateOffCyclePaymentRequest) =>
    http.patch<OffCyclePaymentDto>(`/off-cycle-payments/${id}`, body),
  remove: (id: number) => http.del<void>(`/off-cycle-payments/${id}`),
  /** Per-employee list for the profile timeline. */
  forEmployee: (
    employeeId: number,
    params: { from?: string; to?: string } = {},
    signal?: AbortSignal,
  ) =>
    http.get<OffCyclePaymentDto[]>(
      `/employees/${employeeId}/off-cycle-payments${qs({ ...params })}`,
      { signal },
    ),
};

/* --------------------- Bonus runs (bonus no settings) ----------------- */
//
// Tag "Bonus no settings (Bonus Calculation)". Reads need `payroll.read`;
// every write / transition needs `payroll.admin`.

export type ListBonusTransactionsParams = {
  status?: PayslipStatus;
  /** CASH or BANK_TRANSFER. */
  paymentMode?: BonusPaymentMode;
  employeeCode?: string;
  /** 0-based Spring page index. */
  page?: number;
  size?: number;
};

export const bonusRunsApi = {
  list: (params: { fiscalYear?: string; status?: BonusRunStatus } = {}, signal?: AbortSignal) =>
    http.get<BonusNoSettingsDto[]>(`/bonuses/no-settings${qs({ ...params })}`, { signal }),
  get: (id: number, signal?: AbortSignal) =>
    http.get<BonusNoSettingsDto>(`/bonuses/no-settings/${id}`, { signal }),
  create: (body: CreateBonusNoSettingsRequest) =>
    http.post<BonusNoSettingsDto>('/bonuses/no-settings', body),
  update: (id: number, body: UpdateBonusNoSettingsRequest) =>
    http.patch<BonusNoSettingsDto>(`/bonuses/no-settings/${id}`, body),
  remove: (id: number) => http.del<void>(`/bonuses/no-settings/${id}`),
  /** Starts the async compute saga for every employee → COMPUTING. */
  compute: (id: number) =>
    http.post<BonusNoSettingsDto>(`/bonuses/no-settings/${id}/compute`),
  /** Approve a COMPUTED run. */
  approve: (id: number) =>
    http.post<BonusNoSettingsDto>(`/bonuses/no-settings/${id}/approve`),
  /** Drop the computed transactions so the run can be recomputed. */
  reset: (id: number) =>
    http.post<BonusNoSettingsDto>(`/bonuses/no-settings/${id}/reset`),
  /** Per-employee computed bonus rows. Server-paginated. */
  transactions: (
    id: number,
    params: ListBonusTransactionsParams = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<BonusTransactionDto>>(
      `/bonuses/no-settings/${id}/transactions${qs({ ...params })}`,
      { signal },
    ),
};

/* -------------------------- Increment batches ------------------------- */

export const incrementBatchesApi = {
  list: (
    params: { status?: IncrementBatchStatus; fiscalYear?: string } = {},
    signal?: AbortSignal,
  ) =>
    http.get<IncrementBatchDto[]>(`/salary/increment-batches${qs({ ...params })}`, { signal }),
  get: (id: number, signal?: AbortSignal) =>
    http.get<IncrementBatchDto>(`/salary/increment-batches/${id}`, { signal }),
  create: (body: CreateIncrementBatchRequest) =>
    http.post<IncrementBatchDto>('/salary/increment-batches', body),
  update: (id: number, body: UpdateIncrementBatchRequest) =>
    http.patch<IncrementBatchDto>(`/salary/increment-batches/${id}`, body),
  apply: (id: number) =>
    http.post<IncrementBatchDto>(`/salary/increment-batches/${id}/apply`),
  cancel: (id: number) =>
    http.post<IncrementBatchDto>(`/salary/increment-batches/${id}/cancel`),
  /**
   * Add a single line to an existing DRAFT batch (P11).
   * 409 if the employee already has a line in this batch.
   */
  addLine: (id: number, body: CreateIncrementBatchLineRequest) =>
    http.post<IncrementBatchLineDto>(
      `/salary/increment-batches/${id}/lines`,
      body,
    ),
  updateLine: (id: number, lineId: number, body: UpdateIncrementBatchLineRequest) =>
    http.patch<IncrementBatchLineDto>(
      `/salary/increment-batches/${id}/lines/${lineId}`,
      body,
    ),
  removeLine: (id: number, lineId: number) =>
    http.del<void>(`/salary/increment-batches/${id}/lines/${lineId}`),
};

/* ------------------- Bank + employee payroll settings ----------------- */

export const employeeBankDetailsApi = {
  /** 404 when none exists. Callers must catch ApiError with status 404. */
  get: (employeeId: number, signal?: AbortSignal) =>
    http.get<EmployeeBankDetailsDto>(`/employees/${employeeId}/bank-details`, { signal }),
  upsert: (employeeId: number, body: UpsertBankDetailsRequest) =>
    http.put<EmployeeBankDetailsDto>(`/employees/${employeeId}/bank-details`, body),
  remove: (employeeId: number) =>
    http.del<void>(`/employees/${employeeId}/bank-details`),
};

export const employeePayrollSettingsApi = {
  get: (employeeId: number, signal?: AbortSignal) =>
    http.get<EmployeePayrollSettingsDto>(`/employees/${employeeId}/payroll-settings`, {
      signal,
    }),
  update: (employeeId: number, body: UpdateEmployeePayrollSettingsRequest) =>
    http.patch<EmployeePayrollSettingsDto>(
      `/employees/${employeeId}/payroll-settings`,
      body,
    ),
};

/* --------------------------- Gratuity / settlement -------------------- */

export const gratuityApi = {
  forEmployee: (employeeId: number, asOf?: string, signal?: AbortSignal) =>
    http.get<GratuityDto>(`/gratuity/employees/${employeeId}${qs({ asOf })}`, { signal }),
};

export const settlementApi = {
  preview: (employeeId: number, lastWorkingDay?: string, signal?: AbortSignal) =>
    http.get<SettlementPreviewDto>(
      `/settlement/employees/preview/${employeeId}${qs({ lastWorkingDay })}`,
      { signal },
    ),
  submit: (body: SubmitSettlementRequest) =>
    http.post<SettlementDto>('/settlement/employees', body),
  /** GET /settlement/employees — paginated submitted settlements. */
  list: (params: ListFinalSettlementsParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<FinalSettlementListDto>>(
      `/settlement/employees${qs({ ...params })}`,
      { signal },
    ),
  /** Full breakdown of one submitted settlement. */
  details: (settlementId: number, signal?: AbortSignal) =>
    http.get<FinalSettlementDetailsDto>(
      `/settlement/employees/submitted-settlements/${settlementId}`,
      { signal },
    ),
  /** DRAFT → HR_APPROVED. */
  approve: (settlementId: number, note?: string) =>
    http.post<SettlementDto>(`/settlement/employees/${settlementId}/hr-approval-confirmation`, {
      note,
    }),
  /** DRAFT → REJECTED (final). */
  reject: (settlementId: number, note?: string) =>
    http.post<SettlementDto>(`/settlement/employees/${settlementId}/reject`, { note }),
  /** Cancels the settlement (DRAFT or HR_APPROVED). */
  cancel: (settlementId: number, reason: string) =>
    http.post<SettlementDto>(`/settlement/employees/${settlementId}/cancel`, { reason }),
};

/* -------------------- Payroll config + fiscal years ------------------- */

export const payrollConfigApi = {
  current: (signal?: AbortSignal) =>
    http.get<PayrollConfigDto>('/payroll/config', { signal }),
  history: (signal?: AbortSignal) =>
    http.get<PayrollConfigDto[]>('/payroll/config/history', { signal }),
  create: (body: CreatePayrollConfigRequest) =>
    http.post<PayrollConfigDto>('/payroll/config', body),
};

export const fiscalYearsApi = {
  list: (signal?: AbortSignal) => http.get<FiscalYearDto[]>('/fiscal-years', { signal }),
  current: (signal?: AbortSignal) =>
    http.get<FiscalYearDto>('/fiscal-years/current', { signal }),
  create: (body: CreateFiscalYearRequest) =>
    http.post<FiscalYearDto>('/fiscal-years', body),
  update: (code: string, body: UpdateFiscalYearRequest) =>
    http.patch<FiscalYearDto>(`/fiscal-years/${code}`, body),
};

/* ------------------------------ Sagas --------------------------------- */

export type ListPayrollSagasParams = {
  status?: SagaStatus;
  name?: string;
  page?: number;
  size?: number;
  sort?: string;
};

export const payrollSagasApi = {
  get: (id: number, signal?: AbortSignal) =>
    http.get<SagaResponse>(`/payroll/sagas/${id}`, { signal }),
  byKey: (name: string, correlationKey: string, signal?: AbortSignal) =>
    http.get<SagaResponse>(`/payroll/sagas/by-key${qs({ name, correlationKey })}`, { signal }),
  list: (params: ListPayrollSagasParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<SagaResponse>>(`/payroll/sagas${qs(params)}`, { signal }),
  retry: (id: number) => http.post<SagaResponse>(`/payroll/sagas/${id}/retry`),
  health: (signal?: AbortSignal) =>
    http.get<SagaHealthSnapshot>('/payroll/sagas/health', { signal }),
};

/* ---------------------------- Advances -------------------------------- */
//
// Two flavours that share the HR → Accounts approval shape:
//   - Permanent: single-employee long-term advance, monthly deductions
//   - Short-term: bulk batch, single-month deduction across N employees

export type ListPermanentAdvancesParams = {
  employeeId?: number;
  status?: AdvanceStatus;
  page?: number;
  size?: number;
  sort?: string;
};

/** Query for the advance `…/filed-by-me` lists. Dates are YYYY-MM-DD,
 *  inclusive, on when the request was filed. */
export type FiledByMeAdvanceParams = {
  status?: AdvanceStatus;
  filedFrom?: string;
  filedTo?: string;
  page?: number;
  size?: number;
};

/** Query for the advance `…/department` lists — the filed-by-me filters
 *  plus one colleague. */
export type DepartmentAdvanceParams = FiledByMeAdvanceParams & { employeeId?: number };

export const permanentAdvancesApi = {
  list: (params: ListPermanentAdvancesParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<PermanentAdvanceDto>>(
      `/permanent-advances${qs(params)}`,
      { signal },
    ),
  /** Self-serve view — caller may read their own employee id without
   *  `payroll.read`; HR / payroll-officer callers can read any. */
  listForEmployee: (
    employeeId: number,
    params: { status?: AdvanceStatus; size?: number } = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<PermanentAdvanceDto>>(
      `/permanent-advances/employees/${employeeId}${qs(params)}`,
      { signal },
    ),
  get: (id: number, signal?: AbortSignal) =>
    http.get<PermanentAdvanceDto>(`/permanent-advances/${id}`, { signal }),
  /** Advances the caller filed for someone else (self-applications
   *  excluded), with approval steps. Scoped to the caller server-side. */
  filedByMe: (
    params: FiledByMeAdvanceParams = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<PermanentAdvanceDto>>(
      `/permanent-advances/filed-by-me${qs(params)}`,
      { signal },
    ),
  /** Every advance in the caller's department, whoever filed it. Gated on
   *  `team.request.generation.longTermAdvance`. */
  department: (params: DepartmentAdvanceParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<PermanentAdvanceDto>>(
      `/permanent-advances/department${qs(params)}`,
      { signal },
    ),
  create: (body: CreatePermanentAdvanceRequest) =>
    http.post<PermanentAdvanceDto>('/permanent-advances', body),
  managerApprove: (id: number, body: AdvanceDecisionRequest = {}) =>
    http.post<PermanentAdvanceDto>(`/permanent-advances/${id}/manager-approve`, body),
  /** Advances HR has approved that Accounts hasn't (HR_APPROVED) — the list
   *  HR prints advance slips from. Optional `employeeId` narrows it to one
   *  employee. Default sort id desc, page size 20 (max 200). Requires
   *  `payroll.advance.hr`. */
  hrApprovedReceivingList: (params: HrApprovedReceivingListParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<PermanentAdvanceReceivingRow>>(
      `/permanent-advances/hr-approved-receiving-list${qs(params)}`,
      { signal },
    ),
  /** HR revises amount / monthly deduction before approving. Status and
   *  chain are unchanged — HR still calls `hrApprove` afterwards. Requires
   *  `payroll.advance.hr`. */
  hrAdjustBeforeApproval: (id: number, body: AdjustPermanentAdvanceRequest) =>
    http.post<PermanentAdvanceDto>(`/permanent-advances/${id}/hr-adjust-before-approval`, body),
  hrApprove: (id: number, body: AdvanceDecisionRequest = {}) =>
    http.post<PermanentAdvanceDto>(`/permanent-advances/${id}/hr-approve`, body),
  accountsApprove: (id: number, body: AdvanceDecisionRequest = {}) =>
    http.post<PermanentAdvanceDto>(
      `/permanent-advances/${id}/accounts-approve`,
      body,
    ),
  reject: (id: number, body: AdvanceRejectRequest) =>
    http.post<PermanentAdvanceDto>(`/permanent-advances/${id}/reject`, body),
  cancel: (id: number) =>
    http.post<PermanentAdvanceDto>(`/permanent-advances/${id}/cancel`),
  deductions: (id: number, signal?: AbortSignal) =>
    http.get<PermanentAdvanceDeductionDto[]>(
      `/permanent-advances/${id}/deductions`,
      { signal },
    ),
  recordDeduction: (id: number, body: RecordPermanentAdvanceDeductionRequest) =>
    http.post<PermanentAdvanceDeductionDto>(
      `/permanent-advances/${id}/deductions`,
      body,
    ),
  /** MANUAL deductions only — any other source answers 409. */
  deleteDeduction: (id: number, deductionId: number) =>
    http.del<void>(`/permanent-advances/${id}/deductions/${deductionId}`),
};

export type ListShortTermAdvanceBatchesParams = {
  status?: AdvanceStatus;
  year?: number;
  month?: number;
  departmentId?: number;
  page?: number;
  size?: number;
  sort?: string;
};

export const shortTermAdvancesApi = {
  list: (params: ListShortTermAdvanceBatchesParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<ShortTermAdvanceBatchDto>>(
      `/short-term-advance-batches${qs(params)}`,
      { signal },
    ),
  /** Self-serve view — caller may read their own employee id without
   *  `payroll.read`; HR / payroll-officer callers can read any. `lines`,
   *  `totalAmount`, and `totalEmployees` are rescoped server-side to just
   *  the caller's own line so batch-mates don't leak through. */
  listForEmployee: (
    employeeId: number,
    params: { status?: AdvanceStatus; size?: number } = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<ShortTermAdvanceBatchDto>>(
      `/short-term-advance-batches/employees/${employeeId}${qs(params)}`,
      { signal },
    ),
  /** Batches the caller raised, with lines and approval steps. Scoped to
   *  the caller server-side, so no `payroll.read` needed. */
  filedByMe: (
    params: FiledByMeAdvanceParams = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<ShortTermAdvanceBatchDto>>(
      `/short-term-advance-batches/filed-by-me${qs(params)}`,
      { signal },
    ),
  /** Every batch covering someone in the caller's department, whoever raised
   *  it. Gated on `team.request.generation.shortTermAdvance`. */
  department: (params: DepartmentAdvanceParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<ShortTermAdvanceBatchDto>>(
      `/short-term-advance-batches/department${qs(params)}`,
      { signal },
    ),
  get: (id: number, signal?: AbortSignal) =>
    http.get<ShortTermAdvanceBatchDto>(
      `/short-term-advance-batches/${id}`,
      { signal },
    ),
  /** Per-employee lines of one batch, each with its `employee` summary
   *  inlined server-side. Authentication only — no `payroll.read` and no
   *  per-line `GET /employees/{id}` (`employee.read`) needed. */
  lines: (id: number, signal?: AbortSignal) =>
    http.get<ShortTermAdvanceLineDto[]>(
      `/short-term-advance-batches/${id}/lines`,
      { signal },
    ),
  create: (body: CreateShortTermAdvanceBatchRequest) =>
    http.post<ShortTermAdvanceBatchDto>('/short-term-advance-batches', body),
  managerApprove: (id: number, body: AdvanceDecisionRequest = {}) =>
    http.post<ShortTermAdvanceBatchDto>(
      `/short-term-advance-batches/${id}/manager-approve`,
      body,
    ),
  hrApprove: (id: number, body: AdvanceDecisionRequest = {}) =>
    http.post<ShortTermAdvanceBatchDto>(
      `/short-term-advance-batches/${id}/hr-approve`,
      body,
    ),
  accountsApprove: (id: number, body: AdvanceDecisionRequest = {}) =>
    http.post<ShortTermAdvanceBatchDto>(
      `/short-term-advance-batches/${id}/accounts-approve`,
      body,
    ),
  reject: (id: number, body: AdvanceRejectRequest) =>
    http.post<ShortTermAdvanceBatchDto>(
      `/short-term-advance-batches/${id}/reject`,
      body,
    ),
  cancel: (id: number) =>
    http.post<ShortTermAdvanceBatchDto>(
      `/short-term-advance-batches/${id}/cancel`,
    ),
};

/* -------------------------- Annual Leave ------------------------------ */

export type ListAnnualLeaveParams = {
  year: number;
  month: number;
  employeeCode?: string;
  /** true = paid, false = marked unpaid. Ignored when paymentPending is set. */
  paymentStatus?: boolean;
  /** true = only rows with no payment decision yet (null paymentStatus). */
  paymentPending?: boolean;
  /** false also matches never-locked (null) rows. */
  lockStatus?: boolean;
  page?: number;
  size?: number;
};

export const annualLeaveApi = {
  /** GET /annual-leave — paginated list of calculated rows. */
  list: (params: ListAnnualLeaveParams, signal?: AbortSignal) =>
    http.get<PageResponse<AnnualLeaveDto>>(`/annual-leave${qs({ ...params })}`, { signal }),
  /** POST /annual-leave — run bulk calculation for year+month. */
  calculate: (body: CalculateAnnualLeaveRequest) =>
    http.post<AnnualLeaveCalculationResultDto>('/annual-leave', body),
  /**
   * POST /annual-leave/transaction/{id}/payment-status — mark one calculated
   * row paid/unpaid. Per-transaction, not month-level — each employee's row
   * is settled independently.
   */
  setPaymentStatus: (id: number, paymentStatus: boolean) =>
    http.post<AnnualLeaveDto>(`/annual-leave/transaction/${id}/payment-status`, {
      paymentStatus,
    }),
  /**
   * POST /annual-leave/lock — permanently lock the whole month. The backend
   * refuses (409, `pendingCount` in the error) while any row still has no
   * payment status. There is no unlock.
   */
  lockMonth: (body: LockAnnualLeaveMonthRequest) =>
    http.post<AnnualLeaveLockMonthResultDto>('/annual-leave/lock', body),
};

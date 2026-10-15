// Payroll "Account Reports" contract — `/reports/payroll/accounts/*`.
//
// The registry is the single source of truth: every report's filters
// (`params[]`) and columns come from it, so the screens here are generic
// and a report the backend adds later shows up without a frontend change.

export type ReportParam =
  | 'FISCAL_YEAR'
  | 'MONTH'
  | 'DATE_RANGE'
  | 'AS_ON_DATE'
  | 'PAYMENT_MODE'
  | 'EMPLOYEE_TYPE'
  | 'CARD_RANGE'
  | 'CARD_LIST'
  | 'BANK'
  | 'DEPARTMENT'
  | 'SECTION'
  | 'DESIGNATION'
  | 'EMP_STATUS'
  | 'BALANCE'
  | 'BONUS_NO'
  | 'SLIP_LANGUAGE'
  | 'SHIFT'
  | 'GRADE_TYPE';

export type AccountsReportOutput =
  | 'LIST'
  | 'GROUPED_LIST'
  | 'SUMMARY'
  | 'SLIP'
  | 'LETTER'
  | 'LEDGER';

export type ColumnType = 'TEXT' | 'INT' | 'DECIMAL' | 'AMOUNT' | 'DATE' | 'MONTH';

export interface AccountsReportColumn {
  key: string;
  label: string;
  type: ColumnType;
  totalable: boolean;
}

export interface AccountsReportParamDef {
  param: ReportParam;
  legacyCode: string;
  required: boolean;
}

export interface AccountsReportDefinition {
  /** "A2", "C5", "F-1" … — case-insensitive on the server. */
  id: string;
  title: string;
  group: string;
  groupLabel: string;
  output: AccountsReportOutput;
  /** True when `POST /{id}/document` prints a PDF for this report. */
  printable?: boolean;
  params: AccountsReportParamDef[];
  columns: AccountsReportColumn[];
}

export interface AccountsReportGroup {
  group: string | null;
  employees: number;
  /** Rows of this group in the full filtered result. */
  rows: number;
  totals: Record<string, number>;
}

export type ReportRow = Record<string, unknown>;

export interface AccountsReportResult {
  reportId: string;
  title: string;
  /** "Status: Active | Month: August 2026 | Bank: HBL" */
  appliedFilters: string;
  columns: AccountsReportColumn[];
  /** LEDGER only — the one employee the ledger is for. */
  header: Record<string, unknown> | null;
  rows: ReportRow[];
  /** Grand totals over the FULL filtered result, not this page. */
  totals: Record<string, number>;
  totalEmployees: number;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  /** GROUPED_LIST only, in print order. */
  groups: AccountsReportGroup[] | null;
}

export type PaymentMode = 'ALL' | 'CASH' | 'BANK_TRANSFER';
export type EmployeeType = 'ALL' | 'PRODUCTION' | 'NON_PRODUCTION';
export type EmpStatus = 'ACTIVE' | 'NON_ACTIVE' | 'ALL';
export type BalanceFilter = 'GREATER_THAN_ZERO' | 'EQUAL_ZERO';
export type SlipLanguage = 'en' | 'ur';
export type GradeType = 'MANAGEMENT' | 'SUPERVISOR' | 'LABOUR';

/** JSON body shared by `/data`, `/export` and `/document`. */
export interface AccountsReportFilterBody {
  fiscalYearCode?: string | null;
  /** "YYYY-MM" */
  month?: string | null;
  dateFrom?: string | null;
  dateTo?: string | null;
  asOnDate?: string | null;
  paymentMode?: PaymentMode | null;
  employeeType?: EmployeeType | null;
  cardFrom?: number | null;
  cardTo?: number | null;
  cardList?: string[] | null;
  bankId?: number | null;
  departmentId?: number | null;
  sectionId?: number | null;
  designationId?: number | null;
  empStatus?: EmpStatus | null;
  balance?: BalanceFilter | null;
  bonusNo?: number | null;
  gradeType?: GradeType | null;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
  search?: string;
}

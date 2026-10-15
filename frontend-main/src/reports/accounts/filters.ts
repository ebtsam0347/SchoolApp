// Filter plumbing for Account Reports: which of a report's registry params
// the run drawer shows, their defaults, and form values → request body.
//
// Form field names match the body field names on purpose, so a 400's
// `violations[].field` lands under the right input without a field map.
// Only the params the report declares (minus the hidden ones below) are
// ever rendered or sent.

import dayjs, { type Dayjs } from 'dayjs';

import { loadSlipLayout, type SlipLayout } from '../slip-layout';

import type {
  AccountsReportDefinition,
  AccountsReportFilterBody,
  BalanceFilter,
  EmpStatus,
  EmployeeType,
  GradeType,
  PaymentMode,
  ReportParam,
  SlipLanguage,
} from './types';

/** Render order, whatever order the registry lists them in. */
export const PARAM_ORDER: ReportParam[] = [
  'FISCAL_YEAR',
  'MONTH',
  'DATE_RANGE',
  'AS_ON_DATE',
  'BONUS_NO',
  'EMP_STATUS',
  'PAYMENT_MODE',
  'EMPLOYEE_TYPE',
  'GRADE_TYPE',
  'BANK',
  'BALANCE',
  'CARD_RANGE',
  'CARD_LIST',
  'DEPARTMENT',
  'SECTION',
  'DESIGNATION',
  'SLIP_LANGUAGE',
];

/*
 * Per-report filter cleanup, checked against payroll-service's report
 * sources (keys are upper-case report ids):
 *
 *   A7   payment mode: the source forces bank transfer, so it never
 *        changes the result.
 *   C5   card range / department / section / designation: the ledger is
 *        for exactly one card, so these can only hide that one employee.
 */
const HIDDEN: Record<string, ReportParam[]> = {
  A7: ['PAYMENT_MODE'],
  C5: ['CARD_RANGE', 'DEPARTMENT', 'SECTION', 'DESIGNATION'],
};

/**
 * How a DATE_RANGE is asked for. The server takes `dateFrom` + `dateTo`,
 * but some sources read less than that:
 *   B1, B4  only the month of each date (advances are keyed by month)
 *   C3      only `dateTo` — a balance as on that date
 */
export type DateRangeMode = 'DATES' | 'MONTHS' | 'AS_ON';

export function dateRangeMode(def: AccountsReportDefinition): DateRangeMode {
  const id = def.id.toUpperCase();
  if (id === 'B1' || id === 'B4') return 'MONTHS';
  if (id === 'C3') return 'AS_ON';
  return 'DATES';
}

/** Output file the drawer produces. */
export type OutputFormat = 'pdf' | 'xlsx' | 'csv';

/**
 * Whether the report prints a PDF — the registry's `printable` flag.
 * A backend from before the flag only printed slips, letters and ledgers.
 */
export const isDocument = (def: AccountsReportDefinition) =>
  def.printable ?? (def.output === 'SLIP' || def.output === 'LETTER' || def.output === 'LEDGER');

export type FilterFormValues = {
  format?: OutputFormat;
  fiscalYearCode?: string;
  month?: Dayjs | null;
  /** DATES → [from, to]; MONTHS → [from month, to month]. */
  dateRange?: [Dayjs, Dayjs] | null;
  /** C3's single "balance as on" date. */
  dateTo?: Dayjs | null;
  asOnDate?: Dayjs | null;
  paymentMode?: PaymentMode;
  employeeType?: EmployeeType;
  cardFrom?: string;
  cardTo?: string;
  cardList?: string[];
  bankId?: number;
  departmentId?: number;
  sectionId?: number;
  designationId?: number;
  empStatus?: EmpStatus;
  balance?: BalanceFilter;
  bonusNo?: number;
  /** Empty = all grades. */
  gradeType?: GradeType | '';
  language?: SlipLanguage;
  /** A1's PDF page layout — a query param on `/document`, like `language`. */
  layout?: SlipLayout;
};

const KNOWN = new Set<ReportParam>(PARAM_ORDER);

/** Params the drawer renders, in PARAM_ORDER. SHIFT isn't wired yet. */
export function visibleParams(def: AccountsReportDefinition) {
  const hidden = HIDDEN[def.id.toUpperCase()] ?? [];
  return def.params
    .filter((p) => KNOWN.has(p.param) && !hidden.includes(p.param))
    .sort((a, b) => PARAM_ORDER.indexOf(a.param) - PARAM_ORDER.indexOf(b.param));
}

export const hasParam = (def: AccountsReportDefinition, param: ReportParam) =>
  visibleParams(def).some((p) => p.param === param);

/** Only A1 (Salary Slips) prints in the 3-per-page `sheet` layout. */
export const hasSlipLayout = (def: AccountsReportDefinition) => def.id.toUpperCase() === 'A1';

/** LEDGER takes exactly one card. */
export const isSingleCard = (def: AccountsReportDefinition) => def.output === 'LEDGER';

/* ------------------------------ Defaults ------------------------------ */

/**
 * Static defaults. The fiscal year (CURRENT) and bonus no (highest of the
 * year) depend on lookups and are filled in by their fields once those load.
 */
export function defaultFormValues(def: AccountsReportDefinition): FilterFormValues {
  const today = dayjs();
  const out: FilterFormValues = { format: isDocument(def) ? 'pdf' : 'xlsx' };
  if (hasSlipLayout(def)) out.layout = loadSlipLayout();
  for (const { param } of visibleParams(def)) {
    switch (param) {
      case 'MONTH':
        // Salary is posted after month-end, so its reports look back one month.
        out.month =
          def.group === 'SALARY' ? today.subtract(1, 'month').startOf('month') : today.startOf('month');
        break;
      case 'DATE_RANGE':
        if (dateRangeMode(def) === 'AS_ON') out.dateTo = today;
        else out.dateRange = [today.startOf('month'), today.endOf('month')];
        break;
      case 'AS_ON_DATE':
        out.asOnDate = today;
        break;
      case 'PAYMENT_MODE':
        out.paymentMode = 'ALL';
        break;
      case 'EMPLOYEE_TYPE':
        out.employeeType = 'ALL';
        break;
      case 'EMP_STATUS':
        // A ledger is looked up by card — show it whether or not they've left.
        out.empStatus = isSingleCard(def) ? 'ALL' : 'ACTIVE';
        break;
      case 'BALANCE':
        out.balance = 'GREATER_THAN_ZERO';
        break;
      case 'SLIP_LANGUAGE':
        out.language = 'en';
        break;
      case 'GRADE_TYPE':
        out.gradeType = '';
        break;
      default:
        break;
    }
  }
  return out;
}

/* ------------------------------ Encoding ------------------------------ */

const iso = (d: Dayjs | null | undefined) => (d ? d.format('YYYY-MM-DD') : null);

const cardNumber = (s: string | undefined): number | null => {
  const t = s?.trim();
  return t && /^\d+$/.test(t) ? Number(t) : null;
};

/**
 * Form values → request body. Only the report's own params are sent;
 * unset ones go as `null`. The slip language and layout are query
 * parameters on `/document`, never part of the body.
 */
export function formToBody(
  def: AccountsReportDefinition,
  v: FilterFormValues,
): AccountsReportFilterBody {
  const body: AccountsReportFilterBody = {};
  const params = new Set(visibleParams(def).map((p) => p.param));
  const on = (p: ReportParam) => params.has(p);

  if (on('FISCAL_YEAR')) body.fiscalYearCode = v.fiscalYearCode || null;
  if (on('MONTH')) body.month = v.month ? v.month.format('YYYY-MM') : null;
  if (on('DATE_RANGE')) {
    const mode = dateRangeMode(def);
    if (mode === 'AS_ON') {
      body.dateFrom = iso(v.dateTo);
      body.dateTo = iso(v.dateTo);
    } else {
      const [from, to] = v.dateRange ?? [];
      body.dateFrom = iso(mode === 'MONTHS' ? from?.startOf('month') : from);
      body.dateTo = iso(mode === 'MONTHS' ? to?.endOf('month') : to);
    }
  }
  if (on('AS_ON_DATE')) body.asOnDate = iso(v.asOnDate);
  if (on('PAYMENT_MODE')) body.paymentMode = v.paymentMode ?? null;
  if (on('EMPLOYEE_TYPE')) body.employeeType = v.employeeType ?? null;
  if (on('CARD_RANGE')) {
    body.cardFrom = cardNumber(v.cardFrom);
    body.cardTo = cardNumber(v.cardTo);
  }
  if (on('CARD_LIST')) {
    const cards = (v.cardList ?? []).map((c) => String(c).trim()).filter(Boolean);
    body.cardList = cards.length ? cards : null;
  }
  if (on('BANK')) body.bankId = v.bankId ?? null;
  if (on('DEPARTMENT')) body.departmentId = v.departmentId ?? null;
  if (on('SECTION')) body.sectionId = v.sectionId ?? null;
  if (on('DESIGNATION')) body.designationId = v.designationId ?? null;
  if (on('EMP_STATUS')) body.empStatus = v.empStatus ?? null;
  if (on('BALANCE')) body.balance = v.balance ?? null;
  if (on('BONUS_NO')) body.bonusNo = v.bonusNo ?? null;
  if (on('GRADE_TYPE')) body.gradeType = v.gradeType || null;
  return body;
}

/**
 * Where a server violation's `field` lives in the form, when the drawer
 * renders it under a different name (the date range is one control).
 */
export function violationFieldMap(def: AccountsReportDefinition): Record<string, string> {
  return dateRangeMode(def) === 'AS_ON'
    ? { dateFrom: 'dateTo' }
    : { dateFrom: 'dateRange', dateTo: 'dateRange' };
}

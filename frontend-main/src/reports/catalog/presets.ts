// Presentation-only tweaks for catalog entries: readable enum labels,
// checkbox wording, defaults, hints and the kind a report is grouped under
// on /reports. None of this decides *whether* a report shows — the
// catalog does. Unknown ids / params simply fall back to the generic
// rendering.

import dayjs, { type Dayjs } from 'dayjs';

export interface ParamPreset {
  label?: string;
  /** enum value → label. */
  enumLabels?: Record<string, string>;
  /** Text next to the checkbox for `true`/`false` enums. */
  checkboxLabel?: string;
  /** Placeholder for an optional Select; also the empty option's label. */
  emptyLabel?: string;
  initial?: unknown;
}

/** Keyed by parameter name — the same name means the same thing across reports. */
export const PARAM_PRESETS: Record<string, ParamPreset> = {
  columns: {
    label: 'Columns',
    enumLabels: { BASIC: 'Basic', FULL: 'All information' },
    initial: 'BASIC',
  },
  missingPhoto: {
    label: 'Photo',
    checkboxLabel: 'Only employees without a photo',
    initial: false,
  },
  groupBy: {
    label: 'Group by',
    enumLabels: {
      GRADE: 'Grade',
      EMPLOYMENT_TYPE: 'Employment Type',
      CATEGORY: 'Employee Category',
      GENDER: 'Gender',
      MARITAL_STATUS: 'Marital Status',
      RELIGION: 'Religion',
      BLOOD_GROUP: 'Blood Group',
      QUALIFICATION: 'Qualification',
      AGE_BAND: 'Age',
      SERVICE_BAND: 'Length of Service',
    },
  },
  field: {
    label: 'Changed field',
    emptyLabel: 'All',
  },
  onlyDifferent: {
    label: 'Gratuity date',
    checkboxLabel: 'Only where gratuity date differs from joining',
    initial: true,
  },
};

export interface ReportPreset {
  /** Shown above the Download button. */
  hint?: string;
  /** Initial value of a `from`/`to` date range. */
  defaultRange?: () => [Dayjs, Dayjs];
}

export const REPORT_PRESETS: Record<string, ReportPreset> = {
  'employee.id-cards': { hint: 'At most 500 cards per run.' },
  'employee.confirmation-letters': { hint: 'At most 300 letters per run.' },
  'payroll.increment-list': {
    defaultRange: () => [dayjs().startOf('month'), dayjs().endOf('month')],
  },
};

/** Sub-groups inside the People section of /reports, in display order. */
export type EmployeeReportKind = 'lists' | 'bulk-letters' | 'per-employee' | 'other';

export const EMPLOYEE_KIND_ORDER: EmployeeReportKind[] = [
  'lists',
  'bulk-letters',
  'per-employee',
  'other',
];

export const EMPLOYEE_KIND_LABEL: Record<EmployeeReportKind, string> = {
  lists: 'Lists',
  'bulk-letters': 'Letters in bulk',
  'per-employee': 'Per employee',
  other: 'Other',
};

const EMPLOYEE_KIND: Record<string, EmployeeReportKind> = {
  'employee.list': 'lists',
  'employee.department-wise': 'lists',
  'employee.disability-list': 'lists',
  'employee.rejoining-list': 'lists',
  'employee.change-log': 'lists',
  'employee.confirmation-letters': 'bulk-letters',
  'employee.id-cards': 'bulk-letters',
};

export function employeeReportKind(entry: { id: string; path: string }): EmployeeReportKind {
  return (
    EMPLOYEE_KIND[entry.id] ?? (entry.path.includes('{') ? 'per-employee' : 'other')
  );
}

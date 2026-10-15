// Explanatory copy for individual reports, shown as the run drawer's hint.
// Filters, columns and outputs all come from the registry. Keys are
// upper-case report ids.

const RFD_BONUS_HINT =
  'RFD on a bonus is an addition (cash rounded up to a 500 note). Permanent-advance deduction on bonus is 0 until payroll applies it.';

export const FILTER_HINTS: Record<string, string> = {
  A2: "Advance columns are shown as on the payslip; Total Deductions and Net Salary are the payslip's own figures.",
  C10: 'Balance outstanding but no salary deduction in this month.',
  E2: RFD_BONUS_HINT,
  E3: RFD_BONUS_HINT,
  E7: RFD_BONUS_HINT,
  'F-7': 'One column per active allowance. Grade type Management gives the old Management Allowances sheet.',
};

/** Shown under Format while PDF is chosen. */
export const PRINT_HINTS: Record<string, string> = {
  'F-7': 'Print on Legal paper, landscape.',
};

export const reportKey = (id: string) => id.toUpperCase();

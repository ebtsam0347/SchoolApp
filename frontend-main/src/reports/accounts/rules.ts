// Dependency rules between Account Report filters — the combinations the
// run drawer must not let through. Each rule mirrors how payroll-service
// applies the filters, so an allowed combination can actually match rows:
//
//   Fiscal year → Month      A month outside the fiscal year matches no
//                            payslip (the source keeps only payslips of
//                            that year).
//   No future periods        Salary, permanent-advance and allowance data
//                            only exist for months that have happened, and
//                            a balance "as on" a future date is today's.
//                            Temporary advances are keyed by their target
//                            month, which may be ahead, so they stay open.
//   Card range ⟂ card list   Both are applied (AND): a listed card outside
//                            the range silently drops out. One or the other.
//   Cash ⟂ Bank              The bank filter only matches employees paid by
//                            bank transfer, so Cash + a bank is always empty.
//   Employee type → Dept     Production / non-production is the department's
//                            flag: a production department under
//                            "Non-production" can never match.
//   Department → Section     (LookupSelect) sections follow the department.
//   Fiscal year → Bonus no   (BonusNoField) bonus numbers follow the year.

import dayjs, { type Dayjs } from 'dayjs';

import type { AccountsReportDefinition, EmployeeType } from './types';

export type FiscalYearRange = { code: string; start: Dayjs; end: Dayjs };

export function toFiscalYearRange(fy: {
  code?: string;
  startDate?: string;
  endDate?: string;
}): FiscalYearRange | null {
  if (!fy.code || !fy.startDate || !fy.endDate) return null;
  return { code: fy.code, start: dayjs(fy.startDate), end: dayjs(fy.endDate) };
}

/** Groups whose periods can't be in the future (see header). */
export const blocksFuture = (def: AccountsReportDefinition) => def.group !== 'TEMPORARY_ADVANCE';

/** Months that can be picked: inside the fiscal year, and not after this month when blocked. */
export function monthDisabled(month: Dayjs, fy: FiscalYearRange | null, noFuture: boolean): boolean {
  if (fy && (month.isBefore(fy.start, 'month') || month.isAfter(fy.end, 'month'))) return true;
  return noFuture && month.isAfter(dayjs(), 'month');
}

/** Days that can be picked for an "as on" date. */
export const dayDisabled = (day: Dayjs, noFuture: boolean) => noFuture && day.isAfter(dayjs(), 'day');

/**
 * The month to use once the fiscal year changes: the current month when it
 * is still valid, otherwise the latest allowed month of the year.
 */
export function clampMonth(
  month: Dayjs | null | undefined,
  fy: FiscalYearRange,
  noFuture: boolean,
): Dayjs {
  if (month && !monthDisabled(month, fy, noFuture)) return month;
  const latest = noFuture && fy.end.isAfter(dayjs(), 'month') ? dayjs() : fy.end;
  return (latest.isBefore(fy.start, 'month') ? fy.start : latest).startOf('month');
}

/** Whether a department fits the chosen employee type. Unknown flags always fit. */
export function departmentFitsType(isProduction: boolean | undefined, type: EmployeeType | undefined) {
  if (!type || type === 'ALL' || isProduction === undefined) return true;
  return type === 'PRODUCTION' ? isProduction : !isProduction;
}

// Analytics-service contract types — modelled from the response shapes
// catalogued in `docs/reports-frontend-guide.md` §8 and the `/analytics/*`
// endpoints exposed via the gateway.
//
// Money on the wire is rupees (BigDecimal scale 2 server-side, JSON
// number at the boundary). Pass values straight to MoneyCell — no
// conversion. See CLAUDE.md / the auto-memory note on rupees-on-wire.

/* —————————————————————— People analytics ——————————————————————— */

/** Joiners / leavers / end-of-month headcount over a trailing window. */
export interface HeadcountTrendDto {
  months: number;
  series: HeadcountTrendMonth[];
}

export interface HeadcountTrendMonth {
  month: string; // YYYY-MM
  joiners: number;
  leavers: number;
  endHeadcount: number;
}

/**
 * Per-month leavers + start/end headcount + ratePct, plus the
 * rolling-12m headline summary. The "rate" is the leaver fraction of
 * the period's average headcount, expressed as percent.
 */
export interface AttritionDto {
  months: number;
  series: AttritionMonth[];
  rollingTwelveMonthPct: number;
}

export interface AttritionMonth {
  month: string; // YYYY-MM
  leavers: number;
  startHeadcount: number;
  endHeadcount: number;
  ratePct: number;
}

/** Active employees bucketed into tenure ranges, optionally split by
 *  department or grade. When `splitBy` is omitted the response carries
 *  one bucket per range with a single `total` value. */
export interface TenureBandsDto {
  splitBy: TenureBandsSplit;
  bands: TenureBand[];
}

export type TenureBandsSplit = 'none' | 'dept' | 'grade';

/** A single tenure range — `<1y`, `1-3y`, `3-5y`, `5-10y`, `10y+`. */
export interface TenureBand {
  label: string;
  /** Total employees in this band. When `splitBy === 'none'` this is the
   *  whole answer; otherwise it equals the sum of `splits[].count`. */
  total: number;
  splits: TenureBandSplit[];
}

export interface TenureBandSplit {
  /** Department name or grade code, depending on `splitBy`. */
  key: string;
  count: number;
}

/* —————————————————————— Attendance analytics ——————————————————— */

/** Monthly on-time%. denominator = PRESENT records, numerator = on-time
 *  PRESENT records. `pct` is `numerator / denominator * 100` when
 *  `denominator > 0`, else `null` (avoids 0/0 noise on the chart). */
export interface OnTimePctDto {
  months: number;
  series: OnTimePctMonth[];
}

export interface OnTimePctMonth {
  month: string;
  denominator: number;
  numerator: number;
  pct: number | null;
}

/** Monthly absenteeism%. denominator = scheduled work-days, numerator =
 *  ABSENT records. */
export interface AbsenteeismPctDto {
  months: number;
  series: AbsenteeismPctMonth[];
}

export interface AbsenteeismPctMonth {
  month: string;
  denominator: number;
  numerator: number;
  pct: number | null;
}

/** Monthly OT minutes + derived hours. */
export interface OtTrendDto {
  months: number;
  series: OtTrendMonth[];
}

export interface OtTrendMonth {
  month: string;
  minutes: number;
  hours: number;
}

/** Rolling N-day scorecard for one employee. */
export interface EmployeeScorecardDto {
  employeeId: number;
  days: number;
  presentCount: number;
  lateCount: number;
  absentCount: number;
  /** On-time PRESENT records / PRESENT records, percent. Null when
   *  presentCount is 0. */
  onTimePct: number | null;
  otHours: number;
}

/* ———————————————————————— Leave analytics ———————————————————————— */

/** Per leave-type allocated / used / pending + used%. Plus a single
 *  company-wide `overallPct` summary. */
export interface LeaveUtilizationDto {
  year: number;
  byType: LeaveUtilizationType[];
  /** Used days / allocated days across every type, percent. */
  overallPct: number;
}

export interface LeaveUtilizationType {
  code: string;
  label: string;
  allocated: number;
  used: number;
  pending: number;
  /** `used / allocated * 100`. Null when `allocated === 0`. */
  usedPct: number | null;
}

/** Monthly leave days taken with per-leave-type breakdown. The
 *  `byType[]` keys are stable across months so a stacked-bar chart can
 *  use them directly as series ids. */
export interface LeaveSeasonalityDto {
  months: number;
  series: LeaveSeasonalityMonth[];
}

export interface LeaveSeasonalityMonth {
  month: string;
  total: number;
  byType: LeaveSeasonalityType[];
}

export interface LeaveSeasonalityType {
  code: string;
  label: string;
  days: number;
}

/* ——————————————————————— Payroll analytics ——————————————————————— */

/**
 * Per-month payroll cost. `gross / eobi / pessi / tax / net` are the
 * employee-side totals; `totalCost` is the employer-borne sum (gross +
 * employer contributions). `costPerHead` is `totalCost / employeeCount`.
 */
export interface PayrollCostTrendDto {
  months: number;
  series: PayrollCostTrendMonth[];
}

export interface PayrollCostTrendMonth {
  month: string; // YYYY-MM
  gross: number;
  eobi: number;
  pessi: number;
  tax: number;
  net: number;
  totalCost: number;
  employeeCount: number;
  costPerHead: number;
}

/**
 * Min / p25 / median / p75 / max of current basic across all employees.
 * `basics[]` is the raw sorted basics for boxplot rendering — only
 * present when employee count ≤ 5000 (server caps payload size).
 */
export interface SalaryDistributionDto {
  count: number;
  min: number;
  p25: number;
  median: number;
  p75: number;
  max: number;
  basics: number[] | null;
}

/* ——————————————————— Cross-service composite ——————————————————— */

/**
 * Single payload combining the latest values from all four service-side
 * analytics endpoints. Each field falls back to `null` when its
 * downstream is unreachable, so the executive-overview tiles render a
 * "—" without the whole page going red.
 */
export interface ExecutiveOverviewDto {
  headcount: number | null;
  attritionRolling12mPct: number | null;
  latestMonthOnTimePct: number | null;
  latestMonthAbsenteeismPct: number | null;
  latestMonthOtHours: number | null;
  latestMonthPayrollCost: number | null;
}

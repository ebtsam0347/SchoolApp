// Analytics-service + BFF wrappers. All read-only, all JSON, all routed
// via the gateway at `${VITE_API_BASE}` (handled by the shared `http`
// client). Endpoint shapes documented in `docs/reports-frontend-guide.md`
// §8 and modelled in `./types.ts`.
//
// Service-direct endpoints (`/analytics/*`) carry per-service `*.analytics.read`
// permissions. The cross-service composite (`/bff/analytics/executive-overview`)
// is open to any authenticated caller — every field falls back to `null`
// when its downstream is unreachable, so the overview page stays useful
// even with partial permissions.

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';

import type {
  AbsenteeismPctDto,
  AttritionDto,
  EmployeeScorecardDto,
  ExecutiveOverviewDto,
  HeadcountTrendDto,
  LeaveSeasonalityDto,
  LeaveUtilizationDto,
  OnTimePctDto,
  OtTrendDto,
  PayrollCostTrendDto,
  SalaryDistributionDto,
  TenureBandsDto,
  TenureBandsSplit,
} from './types';

interface SignalOpts {
  signal?: AbortSignal;
}

/* —————————————————————— People analytics ——————————————————————— */

export function fetchHeadcountTrend(
  months: number,
  opts?: SignalOpts,
): Promise<HeadcountTrendDto> {
  return http.get<HeadcountTrendDto>(
    `/analytics/employees/headcount-trend${qs({ months })}`,
    opts,
  );
}

export function fetchAttrition(
  months: number,
  opts?: SignalOpts,
): Promise<AttritionDto> {
  return http.get<AttritionDto>(
    `/analytics/employees/attrition${qs({ months })}`,
    opts,
  );
}

export function fetchTenureBands(
  splitBy: TenureBandsSplit,
  opts?: SignalOpts,
): Promise<TenureBandsDto> {
  // The server treats the absence of `splitBy` as "no split" — pass
  // through only when the caller explicitly wants `dept` / `grade`.
  const query = splitBy === 'none' ? {} : { splitBy };
  return http.get<TenureBandsDto>(
    `/analytics/employees/tenure-bands${qs(query)}`,
    opts,
  );
}

/* —————————————————————— Attendance analytics ——————————————————— */

export function fetchOnTimePct(
  months: number,
  opts?: SignalOpts,
): Promise<OnTimePctDto> {
  return http.get<OnTimePctDto>(
    `/analytics/attendance/on-time-pct${qs({ months })}`,
    opts,
  );
}

export function fetchAbsenteeismPct(
  months: number,
  opts?: SignalOpts,
): Promise<AbsenteeismPctDto> {
  return http.get<AbsenteeismPctDto>(
    `/analytics/attendance/absenteeism-pct${qs({ months })}`,
    opts,
  );
}

export function fetchOtTrend(
  months: number,
  opts?: SignalOpts,
): Promise<OtTrendDto> {
  return http.get<OtTrendDto>(
    `/analytics/attendance/ot-trend${qs({ months })}`,
    opts,
  );
}

export function fetchEmployeeScorecard(
  employeeId: number,
  days: number,
  opts?: SignalOpts,
): Promise<EmployeeScorecardDto> {
  return http.get<EmployeeScorecardDto>(
    `/analytics/attendance/scorecard/${employeeId}${qs({ days })}`,
    opts,
  );
}

/* ———————————————————————— Leave analytics ———————————————————————— */

export function fetchLeaveUtilization(
  year: number,
  opts?: SignalOpts,
): Promise<LeaveUtilizationDto> {
  return http.get<LeaveUtilizationDto>(
    `/analytics/leaves/utilization${qs({ year })}`,
    opts,
  );
}

export function fetchLeaveSeasonality(
  months: number,
  opts?: SignalOpts,
): Promise<LeaveSeasonalityDto> {
  return http.get<LeaveSeasonalityDto>(
    `/analytics/leaves/seasonality${qs({ months })}`,
    opts,
  );
}

/* ——————————————————————— Payroll analytics ——————————————————————— */

export function fetchPayrollCostTrend(
  months: number,
  opts?: SignalOpts,
): Promise<PayrollCostTrendDto> {
  return http.get<PayrollCostTrendDto>(
    `/analytics/payroll/cost-trend${qs({ months })}`,
    opts,
  );
}

export function fetchSalaryDistribution(
  opts?: SignalOpts,
): Promise<SalaryDistributionDto> {
  return http.get<SalaryDistributionDto>(
    '/analytics/payroll/salary-distribution',
    opts,
  );
}

/* ——————————————————— Cross-service composite ——————————————————— */

export function fetchExecutiveOverview(
  opts?: SignalOpts,
): Promise<ExecutiveOverviewDto> {
  return http.get<ExecutiveOverviewDto>(
    '/bff/analytics/executive-overview',
    opts,
  );
}

// Employee allowances — the flat monthly allowances paid apart from salary
// (salary_components with calculate_with_salary = false: travel, education,
// mobile, special, accommodation, tax relief). Each employee's amounts are
// effective-dated employee_salary_components lines; the monthly allowance
// run (Employee Allowances) pays whatever is effective.

import type { Page } from '@/lib/page';

/** One allowance column — a catalog component with calculateWithSalary = false. */
export type AllowanceColumn = {
  componentId: number;
  code: string;
  name: string;
};

export type AllowanceEmployee = {
  id: number;
  fullName: string;
  employeeCode: string | null;
  departmentName: string | null;
  sectionName: string | null;
  designationName: string | null;
  profilePictureUrl: string | null;
};

/** Amounts keyed by component code. A missing code means no line. */
export type AllowanceAmounts = Record<string, number>;

/** One row of the All employees grid — the employee's current set. */
export type EmployeeAllowanceRow = {
  employeeId: number;
  employee: AllowanceEmployee;
  amounts: AllowanceAmounts;
  /** Each open allowance's own effective_from, keyed by component code. */
  since?: Record<string, string>;
  total: number;
  /** Start of the current set (newest History row); a removal counts as a change. */
  lastChanged: string;
};

/** One version of an employee's set — a row of the History tab. */
export type AllowanceHistoryEntry = {
  effectiveFrom: string;
  /** null on the current (active) version. */
  effectiveTo: string | null;
  amounts: AllowanceAmounts;
  total: number;
  createdBy: string | null;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string | null;
};

export type EmployeeAllowances = {
  employee: AllowanceEmployee;
  amounts: AllowanceAmounts;
  /** Each open allowance's own effective_from, keyed by component code. */
  since?: Record<string, string>;
  lastChanged: string | null;
};

/**
 * Replace an employee's set as of today. A changed amount closes the open line
 * today and opens a new one today; 0 / absent closes it today; unchanged
 * amounts are left alone. Every save is its own History version.
 */
export type SetAllowancesRequest = {
  amounts: AllowanceAmounts;
};

export type AllowanceEmployeesPage = Page<EmployeeAllowanceRow>;

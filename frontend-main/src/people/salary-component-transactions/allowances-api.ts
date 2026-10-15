// API for the allowance tabs on /people/salary-component-transactions.
// payroll-service EmployeeAllowanceController, routed by the gateway at
// /allowances/**. Reads need `payroll.read`, the PUT `payroll.write`.

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';
import type {
  AllowanceColumn,
  AllowanceEmployeesPage,
  AllowanceHistoryEntry,
  EmployeeAllowances,
  SetAllowancesRequest,
} from './allowances-types';

/** Server page-size cap (PageableDefaults.MAX_SIZE). */
const MAX_PAGE_SIZE = 200;

export const allowancesApi = {
  columns: (signal?: AbortSignal) =>
    http.get<AllowanceColumn[]>('/allowances/columns', { signal }),

  employees: (
    params: { q?: string; page?: number; size?: number } = {},
    signal?: AbortSignal,
  ) =>
    http.get<AllowanceEmployeesPage>(`/allowances/employees${qs({ ...params })}`, { signal }),

  /** Every row matching `q`, fetched page by page — for CSV export. */
  allEmployees: async (q?: string) => {
    const first = await allowancesApi.employees({ q, page: 0, size: MAX_PAGE_SIZE });
    const rest = await Promise.all(
      Array.from({ length: Math.max(0, first.totalPages - 1) }, (_, i) =>
        allowancesApi.employees({ q, page: i + 1, size: MAX_PAGE_SIZE }),
      ),
    );
    return [first, ...rest].flatMap((p) => p.content);
  },

  forEmployee: (employeeId: number, signal?: AbortSignal) =>
    http.get<EmployeeAllowances>(`/allowances/employees/${employeeId}`, { signal }),

  history: (employeeId: number, signal?: AbortSignal) =>
    http.get<AllowanceHistoryEntry[]>(`/allowances/employees/${employeeId}/history`, { signal }),

  save: (employeeId: number, body: SetAllowancesRequest) =>
    http.put<void>(`/allowances/employees/${employeeId}`, body),
};

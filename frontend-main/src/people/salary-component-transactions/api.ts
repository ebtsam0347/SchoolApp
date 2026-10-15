// API wrappers for salary-component-transactions (allowance-run).
// Routes through the gateway via `src/lib/http.ts`
// (bearer + idempotency + 401-refresh-retry).
//
// Endpoint paths mirror the payroll-run pattern using /allowance-run.

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';
import type {
  CreateSalaryComponentTransactionRequest,
  PageResponse,
  SalaryComponentTransactionDto,
  TransactionEmployeeDto,
} from './types';

/* -------------------- Salary Component Transactions ------------------- */

export const salaryComponentTransactionsApi = {
  list: (fiscalYear?: string, signal?: AbortSignal) =>
    http.get<SalaryComponentTransactionDto[]>(
      `/allowance-run${qs({ fiscalYear })}`,
      { signal },
    ),
  get: (id: number, signal?: AbortSignal) =>
    http.get<SalaryComponentTransactionDto>(
      `/allowance-run/${id}`,
      { signal },
    ),
  create: (body: CreateSalaryComponentTransactionRequest) =>
    http.post<SalaryComponentTransactionDto>(
      '/allowance-run',
      body,
    ),
  compute: (id: number) =>
    http.post<SalaryComponentTransactionDto>(
      `/allowance-run/${id}/compute`,
    ),
  approve: (id: number) =>
    http.post<SalaryComponentTransactionDto>(
      `/allowance-run/${id}/approve`,
    ),
  cancel: (id: number) =>
    http.post<SalaryComponentTransactionDto>(
      `/allowance-run/${id}/cancel`,
    ),
  reset: (id: number) =>
    http.post<SalaryComponentTransactionDto>(
      `/allowance-run/${id}/reset`,
    ),
  transactions: (
    id: number,
    params: { page?: number; size?: number } = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<TransactionEmployeeDto>>(
      `/allowance-run/${id}/transactions${qs({ ...params })}`,
      { signal },
    ),
};

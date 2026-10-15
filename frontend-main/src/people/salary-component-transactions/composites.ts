// Salary Component Transaction composites — one round-trip per page surface.
// Mirrors the payroll composites pattern.

import { http } from '@/lib/http';
import type { TransactionSummaryDto } from './types';

export const salaryComponentTransactionComposites = {
  summary: (id: number, signal?: AbortSignal) =>
    http.get<TransactionSummaryDto>(
      `/allowance-run/${id}/summary`,
      { signal },
    ),
};

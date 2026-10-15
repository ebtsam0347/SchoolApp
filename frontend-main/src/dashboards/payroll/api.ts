/**
 * Payroll dashboard API. One BFF call (M-D4) — server-side fan-out
 * to payroll + employee + anomaly-scatter computation. The adapter
 * stitches scatter variance onto anomaly queue rows and expands
 * uniform QueueItems into category-specific view-model rows.
 */
import { useQuery } from '@tanstack/react-query';

import { http } from '@/lib/http';

import type { BffPayrollDashboard } from '../bff-types';
import type { PayrollDashboardDto } from '../types';
import { toPayrollDashboardDto } from './adapter';

const QUERY_KEY = ['dashboard', 'payroll'] as const;

export function usePayrollDashboard() {
  return useQuery({
    queryKey: QUERY_KEY,
    queryFn: ({ signal }): Promise<PayrollDashboardDto> =>
      http
        .get<BffPayrollDashboard>('/bff/dashboard/payroll', { signal })
        .then(toPayrollDashboardDto),
    staleTime: 30_000,
  });
}

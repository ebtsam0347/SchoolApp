/**
 * Employee dashboard API. One BFF call (M-D1) — the entire layout
 * fans out to employee + payroll + attendance + holidays server-side.
 * The adapter handles the small shape difference between the BFF DTO
 * and the layout view-model.
 */
import { useQuery } from '@tanstack/react-query';

import { http } from '@/lib/http';

import type { BffEmployeeDashboard } from '../bff-types';
import type { EmployeeDashboardDto } from '../types';
import { toEmployeeDashboardDto } from './adapter';

const QUERY_KEY = ['dashboard', 'employee'] as const;

export function useEmployeeDashboard() {
  return useQuery({
    queryKey: QUERY_KEY,
    queryFn: ({ signal }): Promise<EmployeeDashboardDto> =>
      http
        .get<BffEmployeeDashboard>('/bff/dashboard/employee', { signal })
        .then(toEmployeeDashboardDto),
    staleTime: 30_000,
  });
}

/**
 * HR dashboard API. One BFF call (M-D3) — server-side fan-out to
 * employee + attendance + audit. Adapter expands the uniform queue
 * items into category-specific row types and computes
 * `ageDays` / `daysLeft` from the BFF's `due` field.
 */
import { useQuery } from '@tanstack/react-query';

import { http } from '@/lib/http';

import type { BffHrDashboard } from '../bff-types';
import type { HrDashboardDto } from '../types';
import { toHrDashboardDto } from './adapter';

const QUERY_KEY = ['dashboard', 'hr'] as const;

export function useHrDashboard() {
  return useQuery({
    queryKey: QUERY_KEY,
    queryFn: ({ signal }): Promise<HrDashboardDto> =>
      http
        .get<BffHrDashboard>('/bff/dashboard/hr', { signal })
        .then(toHrDashboardDto),
    staleTime: 30_000,
  });
}

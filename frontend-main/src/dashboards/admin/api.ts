/**
 * Admin / Auditor dashboard API. One BFF call (M-D5) — server-side
 * fan-out to audit + sagas + auth. Adapter normalises union strings
 * (status enums) and folds AuditEventView rows into a tighter
 * RoleChangeRow shape.
 */
import { useQuery } from '@tanstack/react-query';

import { http } from '@/lib/http';

import type { BffAdminDashboard } from '../bff-types';
import type { AdminDashboardDto } from '../types';
import { toAdminDashboardDto } from './adapter';

const QUERY_KEY = ['dashboard', 'admin'] as const;

export function useAdminDashboard() {
  return useQuery({
    queryKey: QUERY_KEY,
    queryFn: ({ signal }): Promise<AdminDashboardDto> =>
      http
        .get<BffAdminDashboard>('/bff/dashboard/admin', { signal })
        .then(toAdminDashboardDto),
    staleTime: 30_000,
  });
}

/**
 * Manager dashboard composite. The BFF splits this across two endpoints:
 *  • `/bff/dashboard/manager` — counts, 14d attendance, approvals,
 *    leave calendar, context strip.
 *  • `/bff/attendance/team/today` — per-person roll-call rows. We derive
 *    the `team.today.{present,late,absent,onLeave,pendingPunch}` rollup
 *    client-side from these rows.
 * We fan out in parallel and surface a single merged `ManagerDashboardDto`.
 */
import { useQuery } from '@tanstack/react-query';

import { http } from '@/lib/http';

import type { BffManagerDashboard, BffTeamAttendanceToday } from '../bff-types';
import type { ManagerDashboardDto } from '../types';
import { toManagerDashboardDto } from './adapter';

const QUERY_KEY = ['dashboard', 'manager'] as const;

export function useManagerDashboard() {
  return useQuery({
    queryKey: QUERY_KEY,
    queryFn: async ({ signal }): Promise<ManagerDashboardDto> => {
      const [composite, today] = await Promise.all([
        http.get<BffManagerDashboard>('/bff/dashboard/manager', { signal }),
        http
          .get<BffTeamAttendanceToday>('/bff/attendance/team/today', { signal })
          .catch(() => null),
      ]);
      return toManagerDashboardDto(composite, today);
    },
    staleTime: 30_000,
  });
}

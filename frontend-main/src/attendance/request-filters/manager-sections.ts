// Shared bits of the manager-only "Team …" / "Department …" sections on
// /time/leaves and /time/short-leave.

import { useQuery } from '@tanstack/react-query';

import { isApiError } from '@/lib';
import { employeesApi } from '@/people/api';

/**
 * Same key + options as the other /employees/me/team callers, so this is
 * usually a cache hit. Doubles as the "is a manager" check.
 */
export function useMyTeam() {
  return useQuery({
    queryKey: ['employees', 'me', 'team'],
    queryFn: ({ signal }) => employeesApi.myTeam(signal),
    staleTime: 5 * 60_000,
    retry: false,
  });
}

/**
 * On a build without a `…/team` (or `…/department`) route the path falls
 * through to `…/{id}`, which answers 400 (not a number) — or 404 / 405
 * depending on the gateway. A 403 is a build where only team-request
 * generators may read the department list. Any of those means "not there
 * for you", not "broken": the section hides itself.
 */
export function isSectionUnavailable(err: unknown): boolean {
  return isApiError(err) && [400, 403, 404, 405].includes(err.status);
}

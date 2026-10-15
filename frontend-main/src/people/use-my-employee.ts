// Shared "who am I, as an employee" read for chrome that renders outside
// /me — the topbar user menu, the /account stripe, anywhere that wants the
// signed-in user's avatar.
//
// `/auth/me` (and so `auth.user`) is auth-side: username, roles, permissions,
// no picture. The picture lives on the employee record, so chrome that wants
// it has to ask employee-service. `/employees/me` is a single assembled row
// — much lighter than the /bff/account/profile composite /me uses — and the
// whole app shares one cache entry under `['employees','me']`, a key several
// drawers already use.
//
// Standalone (non-employee-linked) users have no employee row at all, so the
// query stays disabled for them rather than firing a guaranteed 404.

import { useQuery, type QueryClient } from '@tanstack/react-query';

import { useAuth } from '@/auth';
import { employeesApi } from './api';

export const MY_EMPLOYEE_QUERY_KEY = ['employees', 'me'] as const;

/** Signed avatar GET URLs carry a 10-minute TTL (`hrms.employee.documents
 *  .download-url-ttl`). Going stale well inside that window keeps a remount
 *  from painting an <img> against an already-expired URL. */
const STALE_TIME = 5 * 60 * 1000;

export function useMyEmployee() {
  const auth = useAuth();
  const employeeLinked = auth.user?.employeeLinked === true;

  const query = useQuery({
    queryKey: MY_EMPLOYEE_QUERY_KEY,
    queryFn: ({ signal }) => employeesApi.me(signal),
    enabled: employeeLinked,
    staleTime: STALE_TIME,
    // A missing or unreadable employee row is not worth retrying on every
    // page — the chrome just falls back to initials.
    retry: false,
  });

  return {
    employee: query.data,
    employeeId: query.data?.id,
    profilePictureUrl: query.data?.profilePictureUrl ?? null,
    /** False for standalone users — they have no employee record to attach
     *  a picture to, so callers should hide upload affordances. */
    canHavePicture: employeeLinked,
    isLoading: query.isLoading,
  };
}

/**
 * Refresh every cached read that carries the caller's avatar, after a
 * successful profile-picture upload.
 *
 * The upload's own POST response comes off the write-side EmployeeDtoMapper
 * and has a null `profilePictureUrl` — only read paths mint the signed GET
 * URL. So this invalidates rather than patching from the response.
 */
export function invalidateMyProfilePicture(qc: QueryClient) {
  void qc.invalidateQueries({ queryKey: MY_EMPLOYEE_QUERY_KEY });
  void qc.invalidateQueries({ queryKey: ['bff', 'account', 'profile'] });
  void qc.invalidateQueries({ queryKey: ['bff', 'account', 'summary'] });
}

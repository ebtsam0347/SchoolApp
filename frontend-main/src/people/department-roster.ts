// The caller's own department, as a list of colleagues.
//
// Backed by `GET /employees/me/department/employees` — added specifically for
// this surface. It carries no permission requirement, which is the point: a
// plain EMPLOYEE holding TEAM_REQUEST_GENERATION can call it, where
// `/employees` and `/employees/directory` both demand `employee.read` (and
// granting that would hand the same user org-wide read access plus the whole
// HR sidebar). The department is resolved server-side from the caller, so
// there is nothing to pass and nothing to scope client-side.
//
// Rows are full `EmployeeDto`s — catalog ids, not labels — so anything that
// needs a designation / grade / section name resolves it against the
// catalogs (all auth-only too).
//
// Used by Team requests (/time/team-requests): the Profile tab lists them,
// and the request tabs pick one to file for.

import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { catalogsApi, employeesApi } from './api';
import type { DepartmentDto, EmployeeDirectoryRow, EmployeeDto } from './types';

export type DepartmentRoster = {
  rows: EmployeeDto[];
  isLoading: boolean;
  error: unknown;
  /** Total in the department, before the `q` filter. */
  total: number;
};

export type UseDepartmentRosterOptions = {
  /** Server-side name / employee-code search. Debounce before passing. */
  q?: string;
  /** Skip the request when the consumer isn't showing a roster. */
  enabled?: boolean;
};

/**
 * A department is small enough to hand over in one page, so this asks for
 * 500 rather than paginating — the table paginates what it gets.
 */
const PAGE_SIZE = 500;

export function useDepartmentRoster({
  q,
  enabled = true,
}: UseDepartmentRosterOptions = {}): DepartmentRoster {
  const search = q?.trim() || undefined;

  const query = useQuery({
    queryKey: ['employees', 'me', 'department', 'employees', search],
    queryFn: ({ signal }) =>
      employeesApi.myDepartmentEmployees({ q: search, size: PAGE_SIZE }, signal),
    staleTime: 60_000,
    enabled,
  });

  return {
    rows: query.data?.content ?? [],
    isLoading: query.isLoading,
    error: query.error ?? undefined,
    total: query.data?.totalElements ?? 0,
  };
}

/**
 * Roster rows in the `EmployeeDirectoryRow` shape, so the Directory's columns
 * and cards render them unchanged. The roster carries catalog ids, not
 * labels; designation / section / grade / city are resolved here against the
 * shared catalogs (auth-only, same query keys as the rest of the app).
 *
 * Manager is resolved from the roster itself — the only people this caller
 * can read. A manager outside the department stays unresolved (no name
 * shown) rather than being fetched through an `employee.read` endpoint.
 */
export function useDepartmentDirectoryRows(
  rows: EmployeeDto[],
  department: DepartmentDto | null,
  { enabled = true }: { enabled?: boolean } = {},
): EmployeeDirectoryRow[] {
  const departmentId = department?.id ?? rows[0]?.departmentId;
  const on = enabled && rows.length > 0;

  const designations = useQuery({
    queryKey: ['catalog', 'designations'],
    queryFn: ({ signal }) => catalogsApi.designations(signal),
    staleTime: 5 * 60_000,
    enabled: on,
  });
  const grades = useQuery({
    queryKey: ['catalog', 'grades'],
    queryFn: ({ signal }) => catalogsApi.grades(signal),
    staleTime: 5 * 60_000,
    enabled: on,
  });
  const sections = useQuery({
    queryKey: ['catalog', 'sections', departmentId],
    queryFn: ({ signal }) => catalogsApi.sections(departmentId, signal),
    staleTime: 5 * 60_000,
    enabled: on && departmentId != null,
  });
  const cities = useQuery({
    queryKey: ['catalog', 'cities'],
    queryFn: ({ signal }) => catalogsApi.cities(undefined, signal),
    staleTime: 5 * 60_000,
    enabled: on,
  });

  return useMemo(() => {
    const byId = <T extends { id?: number }>(list: T[] | undefined) =>
      new Map((list ?? []).map((x) => [x.id, x]));
    const designationById = byId(designations.data);
    const gradeById = byId(grades.data);
    const sectionById = byId(sections.data);
    const cityById = byId(cities.data);
    const colleagueById = byId(rows);

    return rows.map((e) => {
      const m = e.managerId != null ? colleagueById.get(e.managerId) : undefined;
      return {
        employee: e,
        resolved: {
          department: department ?? undefined,
          designation: designationById.get(e.designationId),
          grade: gradeById.get(e.gradeId),
          section: sectionById.get(e.sectionId),
          city: cityById.get(e.cityId),
          manager: m
            ? {
                id: m.id,
                fullName: m.fullName,
                employeeCode: m.employeeCode,
                profilePictureUrl: m.profilePictureUrl,
              }
            : undefined,
        },
      };
    });
  }, [rows, department, designations.data, grades.data, sections.data, cities.data]);
}

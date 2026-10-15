// Composites for the People module. One round-trip per page surface
// (overview / detail / directory / activity / onboarding / org chart).
//
// `directory`, `stats`, `history`, `orgTree`, and `onboardingLookups` are
// hosted by the employee service under `/employees/**` (see
// `docs/services/employee.openapi.json` → "Employees — Composites").
// `detail`, `timeline`, and `accountProfile` still live on the BFF — see
// `docs/services/bff.openapi.json`.
//
// Uses the same `http` wrapper so bearer + idempotency + 401-refresh
// behaviour matches the rest of the app.

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';
import type {
  AccountProfileBff,
  EmployeeDetail,
  EmployeeHistoryRow,
  EmployeeStats,
  EmploymentStatus,
  EmploymentType,
  OnboardingLookups,
  OrgTree,
  PageResponse,
  PageResponseEmployeeDirectoryRow,
  TimelinePageView,
} from './types';

export type DirectoryParams = {
  page?: number;
  size?: number;
  sort?: string;
  q?: string;
  status?: EmploymentStatus | null;
  employmentType?: EmploymentType | null;
  departmentId?: number | null;
  sectionId?: number | null;
  managerId?: number | null;
  gradeId?: number | null;
  designationId?: number | null;
  hasEss?: boolean | null;
  probationDueWithin?: number | null;
  probationOverdue?: boolean | null;
};

export const peopleBff = {
  detail: (id: number, signal?: AbortSignal) =>
    http.get<EmployeeDetail>(`/bff/employee/${id}`, { signal }),

  directory: (params: DirectoryParams = {}, signal?: AbortSignal) =>
    http.get<PageResponseEmployeeDirectoryRow>(
      `/employees/directory${qs({
        page: params.page,
        size: params.size,
        sort: params.sort,
        q: params.q,
        status: params.status ?? undefined,
        employmentType: params.employmentType ?? undefined,
        departmentId: params.departmentId ?? undefined,
        sectionId: params.sectionId ?? undefined,
        managerId: params.managerId ?? undefined,
        gradeId: params.gradeId ?? undefined,
        designationId: params.designationId ?? undefined,
        hasEss: params.hasEss ?? undefined,
        probationDueWithin: params.probationDueWithin ?? undefined,
        probationOverdue: params.probationOverdue ?? undefined,
      })}`,
      { signal },
    ),

  stats: (signal?: AbortSignal) =>
    http.get<EmployeeStats>('/employees/stats', { signal }),

  timeline: (
    id: number,
    params: { cursor?: string; limit?: number } = {},
    signal?: AbortSignal,
  ) =>
    http.get<TimelinePageView>(
      `/bff/employees/${id}/timeline${qs({
        cursor: params.cursor,
        limit: params.limit,
      })}`,
      { signal },
    ),

  /**
   * Paginated assignment-history feed with resolved catalog + manager labels.
   * The detail composite already embeds the first 10 rows as `recentHistory`;
   * use this for the dedicated History tab once it grows past the preview.
   */
  history: (
    id: number,
    params: { page?: number; size?: number } = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<EmployeeHistoryRow>>(
      `/employees/${id}/history/resolved${qs({ page: params.page, size: params.size })}`,
      { signal },
    ),

  onboardingLookups: (signal?: AbortSignal) =>
    http.get<OnboardingLookups>('/employees/onboarding-lookups', { signal }),

  orgTree: (params: { root?: number; depth?: number } = {}, signal?: AbortSignal) =>
    http.get<OrgTree>(
      `/employees/org-tree${qs({ root: params.root, depth: params.depth })}`,
      { signal },
    ),

  accountProfile: (signal?: AbortSignal) =>
    http.get<AccountProfileBff>('/bff/account/profile', { signal }),
};

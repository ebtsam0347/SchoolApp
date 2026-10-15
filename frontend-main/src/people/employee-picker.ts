// Service-neutral employee picker. Backed by GET /employees/picker on the
// employee service — fuzzy match on name + code, capped server-side (100).
// One source of truth for every typeahead surface that needs an ACTIVE
// employee by name/code (attendance operator flows, payroll off-cycle /
// increment / bonus, notification recipients, …).
//
// Replaces the legacy payrollComposites.employeesPicker shim.

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';

export type EmployeePickerCandidate = {
  id: number;
  fullName: string;
  employeeCode: string | null;
  departmentName: string | null;
  designationName: string | null;
  /** Absent on API builds that predate them. */
  sectionName?: string | null;
  profilePictureUrl?: string | null;
};

export type EmployeePickerResponse = {
  items: EmployeePickerCandidate[];
};

export const employeePickerApi = {
  search: (
    params: { q?: string; limit?: number } = {},
    signal?: AbortSignal,
  ) =>
    http.get<EmployeePickerResponse>(
      `/employees/picker${qs({ q: params.q, limit: params.limit })}`,
      { signal },
    ),
};

// Travel Policy Administration API (/travel/policies).

import { http, qs, type Page } from '@/lib';
import type {
  CreateGradeLimitRequest,
  CreatePolicyVersionRequest,
  GradeLimitResponse,
  PolicyVersionResponse,
  PolicyVersionSearchParams,
  UpdateGradeLimitRequest,
} from './policy-types';

export const travelPolicyApi = {
  /** GET /travel/policies — search policy versions (paginated). Requires travel.read. */
  search: (params: PolicyVersionSearchParams = {}, signal?: AbortSignal) =>
    http.get<Page<PolicyVersionResponse>>(`/travel/policies${qs(params)}`, { signal }),

  /** POST /travel/policies — create a policy version (starts in DRAFT). Requires travel.admin. */
  create: (body: CreatePolicyVersionRequest) =>
    http.post<PolicyVersionResponse>('/travel/policies', body),

  /** PATCH /travel/policies/{id} — update a DRAFT version (policyCode is immutable server-side). Requires travel.admin. */
  update: (id: number, body: CreatePolicyVersionRequest) =>
    http.patch<PolicyVersionResponse>(`/travel/policies/${id}`, body),

  /** DELETE /travel/policies/{id} — delete a DRAFT version and its grade limits. Requires travel.admin. */
  remove: (id: number) => http.del<void>(`/travel/policies/${id}`),

  /** GET /travel/policies/{policyVersionId}/grade-limits — list the grade limits of a policy version. Requires travel.read. */
  gradeLimits: (policyVersionId: number, signal?: AbortSignal) =>
    http.get<GradeLimitResponse[]>(`/travel/policies/${policyVersionId}/grade-limits`, { signal }),

  /** POST /travel/policies/{policyVersionId}/grade-limits — add a limit to a DRAFT policy version. Requires travel.admin. */
  createGradeLimit: (policyVersionId: number, body: CreateGradeLimitRequest) =>
    http.post<GradeLimitResponse>(`/travel/policies/${policyVersionId}/grade-limits`, body),

  /** PATCH /travel/policies/grade-limits/{id} — update a grade limit. Parent version must be DRAFT. Requires travel.admin. */
  updateGradeLimit: (id: number, body: UpdateGradeLimitRequest) =>
    http.patch<GradeLimitResponse>(`/travel/policies/grade-limits/${id}`, body),

  /** DELETE /travel/policies/grade-limits/{id} — delete a grade limit. Parent version must be DRAFT. Requires travel.admin. */
  deleteGradeLimit: (id: number) =>
    http.del<void>(`/travel/policies/grade-limits/${id}`),
};

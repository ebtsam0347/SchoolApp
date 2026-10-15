import { http } from '@/lib/http';
import { qs } from '@/lib/qs';
import type {
  ActivationStatus,
  BelongingIssuanceBody,
  BelongingIssuanceDto,
  UpdateClearanceRequest,
} from './types';

export const belongingIssuanceApi = {
  get: (id: number, signal?: AbortSignal) =>
    http.get<BelongingIssuanceDto>(`/belonging-issuance/${id}`, { signal }),

  create: (body: BelongingIssuanceBody) =>
    http.post<BelongingIssuanceDto>('/belonging-issuance', body),

  // PUT replaces the whole record — see BelongingIssuanceBody doc comment.
  update: (id: number, body: BelongingIssuanceBody) =>
    http.put<BelongingIssuanceDto>(`/belonging-issuance/${id}`, body),

  updateActivation: (id: number, status: ActivationStatus) =>
    http.patch<BelongingIssuanceDto>(
      `/belonging-issuance/${id}/activation${qs({ status })}`,
    ),

  updateClearance: (id: number, body: UpdateClearanceRequest) =>
    http.put<BelongingIssuanceDto>(`/belonging-issuance/${id}/clearance`, body),

  active: (signal?: AbortSignal) =>
    http.get<BelongingIssuanceDto[]>('/belonging-issuance/active', { signal }),

  pendingClearance: (signal?: AbortSignal) =>
    http.get<BelongingIssuanceDto[]>('/belonging-issuance/clearance', { signal }),

  employeeActive: (employeeId: number, signal?: AbortSignal) =>
    http.get<BelongingIssuanceDto[]>(
      `/belonging-issuance/employee/${employeeId}/active`,
      { signal },
    ),

  employeeClearance: (employeeId: number, signal?: AbortSignal) =>
    http.get<BelongingIssuanceDto[]>(
      `/belonging-issuance/employee/${employeeId}/clearance`,
      { signal },
    ),
};

import { http, qs, type Page } from '@/lib';
import type {
  CreateNewHiringRequest,
  CreateReplacementRequest,
  CreateRequisitionRequest,
  RejectRequisitionRequest,
  RequisitionDto,
  RequisitionEmployeeDetailDto,
  RequisitionListParams,
  RequisitionPendingApprovalsParams,
  UpdateRequisitionDetailRequest,
  UpdateRequisitionRequest,
} from './types';

export const requisitionApi = {
  list: (params: RequisitionListParams, signal?: AbortSignal) =>
    http.get<Page<RequisitionDto>>(`/requisitions${qs(params)}`, { signal }),

  /** Caller's own approval inbox across both chains (NEW_HIRE + REPLACEMENT). */
  pendingApprovals: (params: RequisitionPendingApprovalsParams, signal?: AbortSignal) =>
    http.get<Page<RequisitionDto>>(`/requisitions/pending-approvals${qs(params)}`, { signal }),

  get: (id: number, signal?: AbortSignal) =>
    http.get<RequisitionDto>(`/requisitions/${id}`, { signal }),

  details: (id: number, signal?: AbortSignal) =>
    http.get<RequisitionEmployeeDetailDto[]>(`/requisitions/${id}/details`, {
      signal,
    }),

  updateDetail: (
    requisitionId: number,
    detailId: number,
    body: UpdateRequisitionDetailRequest,
  ) =>
    http.patch<RequisitionEmployeeDetailDto>(
      `/requisitions/${requisitionId}/details/${detailId}`,
      body,
    ),

  leanRemove: (requisitionId: number, detailId: number, leanRemovedRemarks: string) =>
    http.post<void>(`/requisitions/${requisitionId}/details/${detailId}/lean-remove`, {
      leanRemovedRemarks,
    }),

  create: (body: CreateRequisitionRequest) =>
    http.post<RequisitionDto>('/requisitions', body),

  createNewHiring: (body: CreateNewHiringRequest) =>
    http.post<RequisitionDto>('/requisitions/new-hiring', body),

  createReplacement: (body: CreateReplacementRequest) =>
    http.post<RequisitionDto>('/requisitions/replacement', body),

  update: (id: number, body: UpdateRequisitionRequest) =>
    http.patch<RequisitionDto>(`/requisitions/${id}`, body),

  submit: (id: number) => http.post<RequisitionDto>(`/requisitions/${id}/submit`),

  approve: (id: number) => http.post<RequisitionDto>(`/requisitions/${id}/approve`),

  reject: (id: number, body: RejectRequisitionRequest) =>
    http.post<RequisitionDto>(`/requisitions/${id}/reject`, body),

  fulfill: (id: number) => http.post<RequisitionDto>(`/requisitions/${id}/fulfill`),

  cancel: (id: number) => http.post<RequisitionDto>(`/requisitions/${id}/cancel`),
};
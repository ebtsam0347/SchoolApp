// Factory Tour API. The service owns the request workflow:
// DRAFT -> PENDING_APPROVAL -> APPROVED (or REJECTED/CANCELLED).

import { http, qs, type Page } from '@/lib';
import type {
  CreateFactoryTourRequest,
  FactoryTourDto,
  FactoryTourListParams,
  RejectFactoryTourRequest,
  UpdateFactoryTourRequest,
} from './types';

export const factoryTourApi = {
  list: (params: FactoryTourListParams = {}, signal?: AbortSignal) =>
    http.get<Page<FactoryTourDto>>(`/factory-tours${qs(params)}`, { signal }),

  get: (id: number, signal?: AbortSignal) =>
    http.get<FactoryTourDto>(`/factory-tours/${id}`, { signal }),

  /** Creates the request in DRAFT; call submit when it is ready for approval. */
  create: (body: CreateFactoryTourRequest) =>
    http.post<FactoryTourDto>('/factory-tours', body),

  update: (id: number, body: UpdateFactoryTourRequest) =>
    http.patch<FactoryTourDto>(`/factory-tours/${id}`, body),

  submit: (id: number) => http.post<FactoryTourDto>(`/factory-tours/${id}/submit`),

  approve: (id: number) => http.post<FactoryTourDto>(`/factory-tours/${id}/approve`),

  reject: (id: number, body: RejectFactoryTourRequest) =>
    http.post<FactoryTourDto>(`/factory-tours/${id}/reject`, body),

  cancel: (id: number) => http.post<FactoryTourDto>(`/factory-tours/${id}/cancel`),

  pendingApprovals: (params: FactoryTourListParams = {}, signal?: AbortSignal) =>
    http.get<Page<FactoryTourDto>>(
      `/factory-tours/pending-approvals${qs(params)}`,
      { signal },
    ),
};

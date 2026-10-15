// Travel Request API (/travel/requests/**).

import { http, qs, type Page } from '@/lib';
import type {
  CreateTravelRequest,
  TravelReasonRequest,
  TravelRequestResponse,
  TravelRequestSearchParams,
} from './types';

export const travelRequestApi = {
  /** GET /travel/requests — searchable across requesters (needs travel.read). */
  search: (params: TravelRequestSearchParams = {}, signal?: AbortSignal) =>
    http.get<Page<TravelRequestResponse>>(`/travel/requests${qs(params)}`, { signal }),

  /** GET /travel/requests/mine — the signed-in employee's own requests. */
  listMine: (signal?: AbortSignal) =>
    http.get<Page<TravelRequestResponse>>('/travel/requests/mine', { signal }),

  get: (id: number, signal?: AbortSignal) =>
    http.get<TravelRequestResponse>(`/travel/requests/${id}`, { signal }),

  create: (body: CreateTravelRequest) =>
    http.post<TravelRequestResponse>('/travel/requests', body),

  update: (id: number, body: CreateTravelRequest) =>
    http.patch<TravelRequestResponse>(`/travel/requests/${id}`, body),

  submit: (id: number) => http.post<TravelRequestResponse>(`/travel/requests/${id}/submit`),

  approve: (id: number) => http.post<TravelRequestResponse>(`/travel/requests/${id}/approve`),

  reject: (id: number, body: TravelReasonRequest) =>
    http.post<TravelRequestResponse>(`/travel/requests/${id}/reject`, body),

  cancel: (id: number, body: TravelReasonRequest = {}) =>
    http.post<TravelRequestResponse>(`/travel/requests/${id}/cancel`, body),

  start: (id: number) => http.post<TravelRequestResponse>(`/travel/requests/${id}/start`),

  complete: (id: number) => http.post<TravelRequestResponse>(`/travel/requests/${id}/complete`),

  pendingApprovals: (signal?: AbortSignal) =>
    http.get<Page<TravelRequestResponse>>('/travel/requests/pending-approvals', { signal }),
};

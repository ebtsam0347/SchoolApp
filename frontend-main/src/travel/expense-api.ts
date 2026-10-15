// Travel Expenses API (/travel/expenses, /travel/requests/{id}/expenses).

import { http } from '@/lib';
import type {
  CreateTravelExpenseRequest,
  TravelExpenseResponse,
  UpdateTravelExpenseRequest,
} from './expense-types';
import type { TravelReasonRequest } from './types';

export const travelExpenseApi = {
  /** GET /travel/requests/{id}/expenses — every claim on a trip. Requires travel.read. */
  listForRequest: (travelRequestId: number, signal?: AbortSignal) =>
    http.get<TravelExpenseResponse[]>(`/travel/requests/${travelRequestId}/expenses`, { signal }),

  /** POST /travel/requests/{id}/expenses — claim an expense. Requires travel.write. */
  create: (travelRequestId: number, body: CreateTravelExpenseRequest) =>
    http.post<TravelExpenseResponse>(`/travel/requests/${travelRequestId}/expenses`, body),

  /** GET /travel/participants/{id}/expenses — one traveller's claims on a trip. Requires travel.read. */
  listForParticipant: (participantId: number, signal?: AbortSignal) =>
    http.get<TravelExpenseResponse[]>(`/travel/participants/${participantId}/expenses`, { signal }),

  /** GET /travel/expenses/{id} — get an expense. Requires travel.read. */
  get: (id: number, signal?: AbortSignal) =>
    http.get<TravelExpenseResponse>(`/travel/expenses/${id}`, { signal }),

  /** PATCH /travel/expenses/{id} — update a SUBMITTED expense. Requires travel.write. */
  update: (id: number, body: UpdateTravelExpenseRequest) =>
    http.patch<TravelExpenseResponse>(`/travel/expenses/${id}`, body),

  /** POST /travel/expenses/{id}/cancel — withdraw a claim. Requires travel.write. */
  cancel: (id: number, body: TravelReasonRequest = {}) =>
    http.post<TravelExpenseResponse>(`/travel/expenses/${id}/cancel`, body),
};

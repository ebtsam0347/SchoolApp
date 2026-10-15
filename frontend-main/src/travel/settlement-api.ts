// Travel Settlements API
// (/travel/requests/{travelRequestId}/participants/{participantId}/settlement/prepare).

import { http } from '@/lib';
import type { TravelSettlementResponse } from './settlement-types';

export const travelSettlementApi = {
  /** GET /travel/requests/{id}/settlements — every settlement on a trip.
   * Requires travel.read. */
  listForRequest: (travelRequestId: number, signal?: AbortSignal) =>
    http.get<TravelSettlementResponse[]>(`/travel/requests/${travelRequestId}/settlements`, {
      signal,
    }),

  /**
   * POST .../settlement/prepare — propose (or fetch the existing) DRAFT FINAL
   * settlement for one participant. Requires travel.write.
   */
  prepare: (travelRequestId: number, participantId: number) =>
    http.post<TravelSettlementResponse>(
      `/travel/requests/${travelRequestId}/participants/${participantId}/settlement/prepare`,
    ),

  /** POST /travel/settlements/{id}/submit — DRAFT -> SUBMITTED. 409 if the
   * settlement isn't DRAFT or has no items. Requires travel.write. */
  submit: (id: number) => http.post<TravelSettlementResponse>(`/travel/settlements/${id}/submit`),
};

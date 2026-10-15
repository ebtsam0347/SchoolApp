// Travel Settlements — final settlement of a participant's trip, exposed at
// gateway path /travel/requests/{travelRequestId}/settlements (list, needs
// travel.read) and
// /travel/requests/{travelRequestId}/participants/{participantId}/settlement/prepare
// (propose/fetch one participant's DRAFT FINAL settlement, needs
// travel.write).
//
// Preparing is idempotent: the caller supplies no expense/advance ids — the
// server discovers the participant's own settleable expenses (APPROVED /
// PARTIALLY_APPROVED / PAID) and paid advances and builds the settlement
// items. An existing DRAFT/FINAL settlement for the participant is returned
// unchanged; any other FINAL settlement is a 409. The trip must be COMPLETED
// or SETTLEMENT_PENDING.

import type { ApprovalStepDto, CurrentStageDto } from '@/attendance';

export type TravelSettlementType = 'DRAFT' | 'FINAL' | (string & {});

export type TravelSettlementStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'PAID'
  | (string & {});

/** Who ends up owing whom once the settlement nets out. */
export type TravelSettlementDirection =
  | 'COMPANY_PAYABLE'
  | 'EMPLOYEE_PAYABLE'
  | (string & {});

export type TravelSettlementResponse = {
  id: number;
  travelRequestId?: number | null;
  participantId?: number | null;
  employeeCode?: string | null;
  employeeName?: string | null;
  settlementNumber?: string | null;
  settlementType?: TravelSettlementType | null;
  status?: TravelSettlementStatus | null;
  totalClaimedExpenses?: number | null;
  totalEligibleExpenses?: number | null;
  totalApprovedExpenses?: number | null;
  totalAdvancesPaid?: number | null;
  totalAdjustments?: number | null;
  finalSettlementAmount?: number | null;
  settlementDirection?: TravelSettlementDirection | null;
  settlementCurrencyCode?: string | null;
  settlementSummary?: string | null;
  approvalRequestId?: number | null;
  approvedAt?: string | null;
  approvedBy?: number | null;
  settlementPaymentReference?: string | null;
  externalTransactionId?: string | null;
  settledAt?: string | null;
  finalized?: boolean | null;
  finalizedAt?: string | null;
  finalizedBy?: number | null;
  remarks?: string | null;
  createdAt?: string;
  createdBy?: string | null;
  version?: number;
  currentStage?: CurrentStageDto | null;
  steps?: ApprovalStepDto[] | null;
  /** Server-computed; drive button visibility off this, not off `status`. */
  actions?: string[] | null;
};

// Travel Requests — services/employee, exposed at gateway path /travel/**.
//
// This is the API that replaces the older /factory-tours/** endpoints for
// reads. Shapes mirror the live employee OpenAPI (TravelRequestResponse and
// friends); every wire field is optional-by-convention except `id`, since the
// service omits nulls on several branches.

import type { ApprovalStepDto, CurrentStageDto } from '@/attendance';

/**
 * Lifecycle per the service:
 * DRAFT -> SUBMITTED -> APPROVED -> IN_PROGRESS -> COMPLETED ->
 * SETTLEMENT_PENDING -> SETTLED, with REJECTED/CANCELLED as exits.
 */
export type TravelRequestStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'PENDING_APPROVAL'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'SETTLEMENT_PENDING'
  | 'SETTLED'
  | 'CANCELLED'
  | 'CANCELED'
  | (string & {});

export type TravelLegStatus =
  | 'PLANNED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED';

/** Who provides the vehicle for a leg. */
export type TravelVehicleSourceCode =
  | 'COMPANY'
  | 'EMPLOYEE'
  | 'RENTAL'
  | 'PUBLIC'
  | 'OTHER';

export type TravelRequestLegResponse = {
  id: number;
  travelRequestId?: number;
  legSequence?: number;
  originLocationCode?: string | null;
  originLocationName?: string | null;
  destinationLocationCode?: string | null;
  destinationLocationName?: string | null;
  plannedDepartureAt?: string | null;
  plannedArrivalAt?: string | null;
  actualDepartureAt?: string | null;
  actualArrivalAt?: string | null;
  legStatus?: TravelLegStatus | null;
  vehicleSourceCode?: TravelVehicleSourceCode | null;
  vehicleNumber?: string | null;
  vehicleDescription?: string | null;
  plannedDistanceKm?: number | null;
  actualDistanceKm?: number | null;
  remarks?: string | null;
  createdAt?: string;
  updatedAt?: string;
  version?: number;
};

export type TravelRequestParticipantResponse = {
  id: number;
  employeeCode?: string | null;
  employeeName?: string | null;
  /** e.g. REQUESTER / TRAVELLER — the requester's own row carries REQUESTER. */
  participantRole?: string | null;
  gradeId?: number | null;
  gradeCode?: string | null;
  gradeName?: string | null;
  designationId?: number | null;
  designationName?: string | null;
  departmentId?: number | null;
  departmentName?: string | null;
  sectionId?: number | null;
  sectionName?: string | null;
  traveling?: boolean | null;
  removalReason?: string | null;
  removedAt?: string | null;
  entitlementEligible?: boolean | null;
  createdAt?: string;
  updatedAt?: string;
  version?: number;
};

export type TravelRequestResponse = {
  id: number;
  requestNo?: string | null;
  requesterEmployeeCode?: string | null;
  requesterEmployeeName?: string | null;
  travelTypeCode?: string | null;
  currencyCode?: string | null;
  purpose?: string | null;
  startDatetime?: string | null;
  endDatetime?: string | null;
  legs?: TravelRequestLegResponse[] | null;
  policyVersionId?: number | null;
  policyVersionNo?: number | null;
  status?: TravelRequestStatus | null;
  approvalReference?: string | null;
  estimatedAmount?: number | null;
  submittedAt?: string | null;
  approvedAt?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  canceledAt?: string | null;
  cancellationReason?: string | null;
  participants?: TravelRequestParticipantResponse[] | null;
  createdAt?: string;
  updatedAt?: string;
  version?: number;
  currentStage?: CurrentStageDto | null;
  steps?: ApprovalStepDto[] | null;
  /** Server-computed; drive button visibility off this, not off `status`. */
  actions?: string[] | null;
};

/** Filters supported by GET /travel/requests (all optional). */
export type TravelRequestSearchParams = {
  status?: TravelRequestStatus;
  travelTypeCode?: string;
  requesterEmployeeId?: number;
  policyVersionId?: number;
};

export type CreateTravelParticipantRequest = {
  employeeCode: string;
};

export type CreateTravelLegRequest = {
  legSequence: number;
  originLocationCode: string;
  destinationLocationCode: string;
  plannedDepartureAt: string;
  plannedArrivalAt?: string;
  transportationTypeCode: string;
  vehicleSourceCode?: TravelVehicleSourceCode;
  vehicleNumber?: string;
  vehicleDescription?: string;
  plannedDistanceKm?: number;
  remarks?: string;
};

export type CreateTravelRequest = {
  travelTypeCode: string;
  currencyCode: string;
  purpose: string;
  startDatetime: string;
  endDatetime: string;
  participants: CreateTravelParticipantRequest[];
  legs: CreateTravelLegRequest[];
};

export type TravelReasonRequest = {
  reason?: string;
};

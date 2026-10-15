// Adapter: TravelRequestResponse (GET /travel/requests) -> FactoryTourDto.
//
// The Factory Tour table + drawers already render a flat, single-leg view of a
// travel request. The /travel API models the same request as a header plus
// legs[] and participants[], so this collapses it: the route is first leg's
// origin -> last leg's destination, vehicle/transport details come from the
// first leg, and the requester's org data comes from their participant row.
// Keeping the adapter here means the presentation components stay untouched.

import type { FactoryTourDto, FactoryTourStatus, TravelType, VehicleSource } from '@/factory-tour';
import type {
  TravelRequestParticipantResponse,
  TravelRequestResponse,
  TravelVehicleSourceCode,
} from './types';

/**
 * The /travel service uses SUBMITTED where the tour UI says PENDING_APPROVAL,
 * and it tracks the post-approval trip (IN_PROGRESS -> SETTLED) that the tour
 * UI has no column for — those all read as Approved here.
 */
export function toFactoryTourStatus(status?: string | null): FactoryTourStatus {
  switch (status) {
    case 'DRAFT':
      return 'DRAFT';
    case 'APPROVED':
    case 'IN_PROGRESS':
    case 'COMPLETED':
    case 'SETTLEMENT_PENDING':
    case 'SETTLED':
      return 'APPROVED';
    case 'REJECTED':
      return 'REJECTED';
    case 'CANCELLED':
    case 'CANCELED':
      return 'CANCELLED';
    default:
      // SUBMITTED / UNDER_REVIEW / PENDING_APPROVAL and anything new.
      return 'PENDING_APPROVAL';
  }
}

/** No explicit flag on the wire — the type code carries it (e.g. INT_TOUR). */
function toTravelType(travelTypeCode?: string | null): TravelType {
  return /INT/i.test(travelTypeCode ?? '') ? 'INTERNATIONAL' : 'LOCAL';
}

const VEHICLE_SOURCE: Record<TravelVehicleSourceCode, VehicleSource | undefined> = {
  COMPANY: 'COMPANY_VEHICLE',
  EMPLOYEE: 'PERSONAL_VEHICLE',
  RENTAL: 'RENTED_VEHICLE',
  PUBLIC: 'PUBLIC_TRANSPORT',
  OTHER: undefined,
};

/** Date-only part of an ISO timestamp, for the "Submitted" column. */
function dateOnly(value?: string | null): string {
  return value ? value.slice(0, 10) : '';
}

function isRequester(p: TravelRequestParticipantResponse, code?: string | null) {
  return p.participantRole === 'REQUESTER' || (Boolean(code) && p.employeeCode === code);
}

export function toFactoryTourDto(src: TravelRequestResponse): FactoryTourDto {
  const legs = [...(src.legs ?? [])].sort(
    (a, b) => (a.legSequence ?? 0) - (b.legSequence ?? 0),
  );
  const first = legs[0];
  const last = legs[legs.length - 1];
  const participants = src.participants ?? [];
  const requester =
    participants.find((p) => isRequester(p, src.requesterEmployeeCode)) ?? participants[0];

  const distance = legs.reduce<number | null>((sum, leg) => {
    const km = leg.actualDistanceKm ?? leg.plannedDistanceKm;
    return km == null ? sum : (sum ?? 0) + km;
  }, null);

  return {
    id: src.id,
    requestId: src.requestNo ?? `#${src.id}`,
    requestType: 'Factory Tour',
    requestDate: dateOnly(src.submittedAt ?? src.createdAt),
    status: toFactoryTourStatus(src.status),
    cardNo: src.requesterEmployeeCode ?? requester?.employeeCode ?? '',
    name: src.requesterEmployeeName ?? requester?.employeeName ?? '',
    departmentId: requester?.departmentId ?? null,
    sectionId: requester?.sectionId ?? null,
    designationId: requester?.designationId ?? null,
    gradeId: requester?.gradeId ?? null,
    travelType: toTravelType(src.travelTypeCode),
    tourStart: src.startDatetime ?? first?.plannedDepartureAt ?? '',
    tourEnd: src.endDatetime ?? last?.plannedArrivalAt ?? '',
    origin: first?.originLocationName ?? first?.originLocationCode ?? '—',
    destination: last?.destinationLocationName ?? last?.destinationLocationCode ?? '—',
    city: last?.destinationLocationName ?? null,
    country: null,
    currency: src.currencyCode ?? null,
    vehicleSource: first?.vehicleSourceCode
      ? (VEHICLE_SOURCE[first.vehicleSourceCode] ?? null)
      : null,
    transportationType: first?.vehicleDescription ?? null,
    vehicleNo: first?.vehicleNumber ?? null,
    purpose: src.purpose ?? null,
    totalDistanceKm: distance,
    legs: legs.map((leg, idx) => ({
      sequence: leg.legSequence ?? idx + 1,
      origin: leg.originLocationName ?? leg.originLocationCode ?? '—',
      destination: leg.destinationLocationName ?? leg.destinationLocationCode ?? '—',
      vehicleSource: leg.vehicleSourceCode ? (VEHICLE_SOURCE[leg.vehicleSourceCode] ?? null) : null,
      transportationType: leg.vehicleDescription ?? null,
      vehicleNo: leg.vehicleNumber ?? null,
      distanceKm: leg.actualDistanceKm ?? leg.plannedDistanceKm ?? null,
      departureAt: leg.plannedDepartureAt ?? null,
      arrivalAt: leg.plannedArrivalAt ?? null,
      remarks: leg.remarks ?? null,
    })),
    additionalTravelPersons: participants
      .filter((p) => !isRequester(p, src.requesterEmployeeCode))
      .map((p) => ({ cardNo: p.employeeCode ?? '', name: p.employeeName ?? undefined })),
    participants: participants.map((p) => ({
      id: p.id,
      cardNo: p.employeeCode ?? '',
      name: p.employeeName,
    })),
    rawStatus: src.status ?? null,
    approvedByUserId: null,
    approvedAt: src.approvedAt ?? null,
    rejectReason: src.cancellationReason ?? null,
    createdAt: src.createdAt ?? '',
    updatedAt: src.updatedAt ?? '',
    createdBy: null,
    currentStage: src.currentStage ?? null,
    steps: src.steps ?? [],
    actions: src.actions ?? [],
  };
}

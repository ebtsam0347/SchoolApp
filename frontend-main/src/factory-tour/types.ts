// Factory Tour — self-service travel/vehicle-tour requests.
//
// Backed by services/employee, exposed at gateway path /factory-tours/**.
// Flow: DRAFT -> PENDING_APPROVAL -> APPROVED (or REJECTED/CANCELLED).
// Requires employee.tours.write for create/update/submit/cancel and
// employee.admin for approve/reject (enforced server-side — the client
// never re-implements that check; see `actions[]` on FactoryTourDto).

import type { ApprovalStepDto, CurrentStageDto } from '@/attendance';

export type TravelType = 'LOCAL' | 'INTERNATIONAL';

export type FactoryTourStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED';

export type VehicleSource =
  | 'COMPANY_VEHICLE'
  | 'PERSONAL_VEHICLE'
  | 'RENTED_VEHICLE'
  | 'PUBLIC_TRANSPORT';

export type TravelPerson = {
  cardNo: string;
  name?: string;
};

/** A participant row as recorded on the request — carries the id the
 * settlement/expense APIs address a traveller by. */
export type FactoryTourParticipant = {
  id: number;
  cardNo: string;
  name?: string | null;
};

/**
 * One hop of the trip, straight off the API's `legs[]`. The rest of the DTO
 * flattens a request to a single origin -> destination for the table; this
 * keeps every hop so the detail view can list them all.
 */
export type FactoryTourLeg = {
  sequence: number;
  origin: string;
  destination: string;
  vehicleSource?: VehicleSource | null;
  transportationType?: string | null;
  vehicleNo?: string | null;
  distanceKm?: number | null;
  departureAt?: string | null;
  arrivalAt?: string | null;
  remarks?: string | null;
};

/**
 * Actions the caller may take on this request right now — computed
 * server-side from status + caller identity (owner vs. approver). Drive
 * button visibility off this array; don't re-derive it from `status`.
 */
export type FactoryTourAction =
  | 'SUBMIT'
  | 'EDIT'
  | 'APPROVE'
  | 'REJECT'
  | 'CANCEL'
  | (string & {});

export type FactoryTourDto = {
  id: number;
  requestId: string;
  requestType: 'Factory Tour';
  requestDate: string;
  status: FactoryTourStatus;
  cardNo: string;
  name: string;
  departmentId?: number | null;
  sectionId?: number | null;
  designationId?: number | null;
  gradeId?: number | null;
  travelType: TravelType;
  tourStart: string;
  tourEnd: string;
  origin: string;
  destination: string;
  city?: string | null;
  country?: string | null;
  currency?: string | null;
  vehicleSource?: VehicleSource | null;
  transportationType?: string | null;
  vehicleNo?: string | null;
  purpose?: string | null;
  totalDistanceKm?: number | null;
  /** Every hop, in sequence. The flat fields above collapse leg 1 -> leg n. */
  legs: FactoryTourLeg[];
  additionalTravelPersons: TravelPerson[];
  /** Every participant row, requester included — id is what the
   * settlement/expense APIs address a traveller by. */
  participants: FactoryTourParticipant[];
  /** The /travel service's own status (COMPLETED, SETTLEMENT_PENDING,
   * SETTLED, ...) — `status` above collapses all of those to APPROVED, so
   * settlement UI gates off this instead. Null for factory-tour rows that
   * never came from /travel. */
  rawStatus?: string | null;
  approvedByUserId?: number | null;
  approvedAt?: string | null;
  rejectReason?: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy?: string | null;
  currentStage: CurrentStageDto | null;
  steps: ApprovalStepDto[];
  actions: FactoryTourAction[];
};

export type CreateFactoryTourRequest = {
  travelType: TravelType;
  tourStart: string;
  tourEnd: string;
  origin: string;
  destination: string;
  city?: string;
  country?: string;
  currency?: string;
  vehicleSource?: VehicleSource;
  transportationType?: string;
  vehicleNo?: string;
  purpose?: string;
  totalDistanceKm?: number;
  /** Optional — omit or send [] when there are no co-travellers. */
  additionalTravelPersons?: TravelPerson[];
};

/** Optional filters supported by GET /factory-tours. */
export type FactoryTourListParams = {
  status?: FactoryTourStatus;
  travelType?: TravelType;
  cardNo?: string;
  departmentId?: number;
  page?: number;
  size?: number;
  sort?: string;
};

/** PATCH /factory-tours/{id} is only valid while the request is a draft. */
export type UpdateFactoryTourRequest = Partial<CreateFactoryTourRequest>;

export type RejectFactoryTourRequest = {
  reason: string;
};

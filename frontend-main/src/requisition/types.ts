// Employee Requisition — headcount requests (new hires + replacements).
// Flow: DRAFT -> PENDING_APPROVAL -> APPROVED -> FULFILLED, with REJECTED /
// CANCELLED as terminal off-ramps. Requires employee.read / employee.write /
// employee.admin depending on the action (see api.ts).
//
// Each type runs its own multi-stage approval chain (dept head -> D-2 grade
// sign-off -> Director for NEW_HIRE; dept head -> HR -> D-2 grade sign-off
// for REPLACEMENT) — `currentStage`/`steps` carry that chain's live state on
// every response, and `actions` is the server's own computed answer to
// "what can the calling user do to this row right now" (ownership +
// permission + current-stage assignment already checked server-side). The
// UI must drive its buttons off `actions`, never re-derive that logic from
// `status` + role/ownership checks client-side.

import type { EmploymentType } from '@/people';
import type { ApprovalStepDto, CurrentStageDto } from '@/attendance';

export type { EmploymentType };

export type RequisitionType = 'NEW_HIRE' | 'REPLACEMENT';

export type RequisitionStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'FULFILLED'
  | 'CANCELLED';

/** Server-computed — what the calling user may do to this row right now. */
export type RequisitionAction = 'SUBMIT' | 'APPROVE' | 'REJECT' | 'CANCEL' | 'FULFILL';

export type EmployeeCategory = 'DIRECT' | 'INDIRECT';
export type RequisitionGender = 'MALE' | 'FEMALE' | 'OTHER';

export type RequisitionDetailItem = {
  noOfPosition?: number;
  sectionId?: number;
  designationId?: number;
  gradeId?: number;
  replacingEmployeeId?: number;
  employmentType: EmploymentType;
  employeeCategory?: EmployeeCategory;
  requiredDate?: string;
  yourName?: string;
  employeeGender?: RequisitionGender;
  shiftId?: number;
};

/** A position record returned by GET /requisitions/{id}/details. */
export type RequisitionEmployeeDetailDto = {
  id: number;
  requisitionId: number;
  replacingEmployeeId?: number;
  replacingEmployeeCode?: string;
  replacingEmployeeName?: string;
  newEmployeeCode?: string;
  temporaryEmployeeReference?: string;
  sectionId?: number;
  sectionCode?: string;
  sectionName?: string;
  designationId?: number;
  designationCode?: string;
  designationTitle?: string;
  gradeId?: number;
  gradeCode?: string;
  gradeName?: string;
  employmentType?: EmploymentType;
  employeeCategory?: EmployeeCategory;
  requiredUntil?: string;
  employeeGender?: RequisitionGender;
  shiftId?: number;
  leanRemoved?: boolean;
  leanRemovedRemarks?: string;
  leanRemovedBy?: number;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;
};

/** PATCH /requisitions/{id}/details/{detailId} — fill in the placement
 * identifiers once a candidate is lined up for a position row. Both fields
 * are optional; send whichever the user changed. */
export type UpdateRequisitionDetailRequest = {
  newEmployeeCode?: string;
  temporaryEmployeeReference?: string;
};

export type CreateNewHiringRequest = {
  departmentId: number;
  minimumEducation?: string;
  minimumExperience?: number;
  minimumSkill?: string;
  jobDescription?: string;
  requestReason?: string;
  hodNotes?: string;
  hotNotes?: string;
  details: RequisitionDetailItem[];
};

export type CreateReplacementRequest = {
  minimumEducation?: string;
  minimumExperience?: number;
  minimumSkill?: string;
  jobDescription?: string;
  requestReason?: string;
  hodNotes?: string;
  hotNotes?: string;
  details: RequisitionDetailItem[];
};

export type RequisitionDto = {
  id: number;
  type: RequisitionType;
  status: RequisitionStatus;
  departmentId?: number;
  sectionId?: number;
  designationId?: number;
  gradeId?: number;
  employmentType?: EmploymentType;
  vacancies: number;
  replacingEmployeeId?: number;
  reason?: string;
  requiredBy?: string;
  notes?: string;
  requestedByUserId?: number;
  approvedByUserId?: number;
  approvedAt?: string;
  rejectReason?: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  // Extended fields for New Hiring
  minimumEducation?: string;
  minimumExperience?: number;
  minimumSkill?: string;
  jobDescription?: string;
  requestReason?: string;
  hodNotes?: string;
  hotNotes?: string;
  totalPositionsHod?: number;
  total_position_hod?: number;
  totalPositionLean?: number;
  details?: RequisitionDetailItem[];
  /** Null once the chain is done (terminal status) or before submission. */
  currentStage: CurrentStageDto | null;
  steps: ApprovalStepDto[];
  actions: RequisitionAction[];
};

export type CreateRequisitionRequest = {
  type: RequisitionType;
  departmentId: number;
  sectionId?: number;
  designationId: number;
  gradeId?: number;
  employmentType: EmploymentType;
  vacancies: number;
  replacingEmployeeId?: number;
  reason?: string;
  requiredBy?: string;
  notes?: string;
};

/** PATCH /requisitions/{id} — only valid while the requisition is DRAFT. Type can't change after creation. */
export type UpdateRequisitionRequest = Partial<
  Omit<CreateRequisitionRequest, 'type'>
>;

export type RejectRequisitionRequest = {
  reason: string;
};

export type RequisitionListParams = {
  status?: RequisitionStatus;
  type?: RequisitionType;
  departmentId?: number;
  page?: number;
  size?: number;
  sort?: string;
};

/** GET /requisitions/pending-approvals — caller's own inbox across both chains. */
export type RequisitionPendingApprovalsParams = {
  from?: string;
  to?: string;
  page?: number;
  size?: number;
};

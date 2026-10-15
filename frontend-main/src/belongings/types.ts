// Belonging Issuance — the transactional record of a Belonging Master item
// (see `catalogsApi.belongingMasters` in `@/people`) handed to a specific
// employee. The employee-service surface only exposes filtered lists
// (`/active`, `/clearance`, per-employee variants) — there is no unfiltered
// "get all" endpoint, so the admin page is built around those two views.

export type ActivationStatus = 'ACTIVE' | 'INACTIVE';

export type ClearanceStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type BelongingCondition = 'NEW' | 'USED' | 'OLD';

export type BelongingIssuanceDto = {
  id?: number;
  belongingMasterId?: number;
  belongingName?: string;
  employeeId?: number;
  assetModel?: string;
  assetYear?: number;
  assetPrice?: number;
  issuedDate?: string;
  expiryDate?: string;
  buyBackPercEmployee?: number;
  buyBackPercEmployer?: number;
  condition?: BelongingCondition;
  activationStatus?: ActivationStatus;
  activationDate?: string;
  description?: string;
  clearanceStatus?: ClearanceStatus;
  clearanceId?: string;
  clearanceDate?: string;
};

/**
 * Body for POST /belonging-issuance and PUT /belonging-issuance/{id}. The
 * PUT replaces the whole record (same convention as Belonging Master) —
 * callers editing an existing issuance must resend its current
 * activation/clearance fields, not just the ones the form exposes.
 */
export type BelongingIssuanceBody = {
  belongingMasterId: number;
  employeeId: number;
  assetModel?: string;
  assetYear?: number;
  assetPrice?: number;
  issuedDate?: string;
  expiryDate?: string;
  buyBackPercEmployee?: number;
  buyBackPercEmployer?: number;
  condition?: BelongingCondition;
  activationStatus?: ActivationStatus;
  activationDate?: string;
  description?: string;
  clearanceStatus?: ClearanceStatus;
  clearanceId?: string;
  clearanceDate?: string;
};

export type UpdateClearanceRequest = {
  clearanceStatus: ClearanceStatus;
  clearanceId?: string;
  clearanceDate?: string;
};

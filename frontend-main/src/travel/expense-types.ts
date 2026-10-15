// Travel Expenses — claim and review actual travel expenses, exposed at
// gateway path /travel/expenses. Flow: SUBMITTED -> APPROVED /
// PARTIALLY_APPROVED -> PAID (or REJECTED / CANCELLED). Reads require
// travel.read; claiming/editing requires travel.write; review requires
// travel.approve; marking paid requires travel.settlement.approve.

export type TravelExpenseStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'PARTIALLY_APPROVED'
  | 'REJECTED'
  | 'PAID'
  | 'CANCELLED'
  | (string & {});

export type TravelExpenseResponse = {
  id: number;
  travelRequestId?: number | null;
  travelRequestParticipantId?: number | null;
  travelRequestEntitlementId?: number | null;
  employeeCode?: string | null;
  employeeName?: string | null;
  expenseCategoryCode?: string | null;
  expenseSubcategoryCode?: string | null;
  description?: string | null;
  expenseAt?: string | null;
  claimedAmount?: number | null;
  claimedCurrencyCode?: string | null;
  eligibleAmount?: number | null;
  nonReimbursableAmount?: number | null;
  approvedAmount?: number | null;
  settlementCurrencyCode?: string | null;
  exchangeRate?: number | null;
  convertedClaimedAmount?: number | null;
  receiptRequired?: boolean | null;
  receiptAttached?: boolean | null;
  receiptDocumentId?: string | null;
  receiptReference?: string | null;
  status?: TravelExpenseStatus | null;
  rejectionReason?: string | null;
  reviewedBy?: number | null;
  reviewedAt?: string | null;
  paidAt?: string | null;
  paymentReference?: string | null;
  remarks?: string | null;
  createdAt?: string;
  updatedAt?: string;
  version?: number;
};

/**
 * PATCH /travel/expenses/{id} — update a SUBMITTED expense. Requires
 * travel.write; 409 if the expense is no longer SUBMITTED. Note the update
 * body addresses the entitlement by `entitlementTypeCode`, unlike the
 * response's `travelRequestEntitlementId`.
 */
export type UpdateTravelExpenseRequest = {
  employeeCode: string;
  entitlementTypeCode?: string;
  expenseCategoryCode: string;
  expenseSubcategoryCode?: string;
  description?: string;
  expenseAt: string;
  claimedAmount: number;
  claimedCurrencyCode: string;
  receiptRequired?: boolean;
  receiptReference?: string;
  receiptDocumentId?: string;
  remarks?: string;
};

/**
 * POST /travel/requests/{travelRequestId}/expenses — claim an expense against
 * a trip; the service takes the same payload as the PATCH. The trip must be
 * APPROVED or later (409 otherwise), `employeeCode` must belong to a
 * participant of that trip (404 otherwise), and `entitlementTypeCode` is
 * matched against the participant's active entitlements — an expense with no
 * match is fully eligible pending review. `expenseAt` is a bare
 * LocalDateTime; build it with `toLocalDateTime()`.
 */
export type CreateTravelExpenseRequest = UpdateTravelExpenseRequest;

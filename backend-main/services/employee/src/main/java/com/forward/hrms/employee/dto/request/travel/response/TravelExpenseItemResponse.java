package com.forward.hrms.employee.dto.request.travel.response;

import com.forward.hrms.employee.entity.travel.TravelExpenseItem;
import com.forward.hrms.employee.entity.travel.TravelRequestParticipant;
import com.forward.hrms.employee.enums.travel.TravelExpenseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response representation of one actual expense/claim incurred during a
 * Travel Request. Exposes the claimed amount alongside the
 * policy-derived eligibility and the reviewer's decision.
 */
public record TravelExpenseItemResponse(

        /* Database identifier of the expense item. */
        Long id,

        /* Travel Request the expense was incurred under. */
        Long travelRequestId,

        /* Participant who incurred/claimed the expense. */
        Long travelRequestParticipantId,

        /* Entitlement this expense was evaluated against; null if none applies. */
        Long travelRequestEntitlementId,

        /* Business employee code of the claimant (resolved from the participant snapshot). */
        String employeeCode,

        /* Employee name of the claimant (resolved from the participant snapshot). */
        String employeeName,

        /* High-level category: ACCOMMODATION, MEAL, TRANSPORTATION, TOLL, PARKING, OTHER. */
        String expenseCategoryCode,

        /* More specific classification: HOTEL, DINNER, TAXI, FUEL, ... */
        String expenseSubcategoryCode,

        /* Human-readable description of the expense. */
        String description,

        /* When the expense was actually incurred. */
        LocalDateTime expenseAt,

        /* Amount claimed by the employee. */
        BigDecimal claimedAmount,

        /* Currency the expense was incurred in. */
        String claimedCurrencyCode,

        /* Maximum amount considered eligible for reimbursement under policy. */
        BigDecimal eligibleAmount,

        /* Portion of the claim that is not reimbursable (claimed - eligible/approved). */
        BigDecimal nonReimbursableAmount,

        /* Final amount approved by the reviewer; null until reviewed. */
        BigDecimal approvedAmount,

        /* Currency the expense will be settled/reimbursed in. */
        String settlementCurrencyCode,

        /* Exchange rate applied when claimed and settlement currency differ. */
        BigDecimal exchangeRate,

        /* Claimed amount converted into the settlement currency. */
        BigDecimal convertedClaimedAmount,

        /* Whether a supporting receipt is required. */
        Boolean receiptRequired,

        /* Whether the required receipt has been supplied. */
        Boolean receiptAttached,

        /* External document/file id of the receipt. */
        String receiptDocumentId,

        /* Reference number on the supporting document. */
        String receiptReference,

        /* Current processing state. */
        TravelExpenseStatus status,

        /* Reason supplied when the expense is rejected. */
        String rejectionReason,

        /* User id of the reviewer; null until reviewed. */
        Long reviewedBy,

        /* When the expense was reviewed. */
        LocalDateTime reviewedAt,

        /* When the expense was included in a payment. */
        LocalDateTime paidAt,

        /* External payroll/finance payment reference. */
        String paymentReference,

        /* Additional employee comments. */
        String remarks,

        /* When the record was created. */
        LocalDateTime createdAt,

        /* When the record was last updated. */
        LocalDateTime updatedAt,

        /* Optimistic-locking version. */
        Long version
) {

    public static TravelExpenseItemResponse of(TravelExpenseItem e) {
        return of(e, null);
    }

    /** Overload that fills {@code employeeCode} / {@code employeeName} from the participant snapshot. */
    public static TravelExpenseItemResponse of(TravelExpenseItem e, TravelRequestParticipant participant) {
        return new TravelExpenseItemResponse(
                e.getId(), e.getTravelRequestId(), e.getTravelRequestParticipantId(),
                e.getTravelRequestEntitlementId(),
                participant == null ? null : participant.getEmployeeNoSnapshot(),
                participant == null ? null : participant.getEmployeeNameSnapshot(),
                e.getExpenseCategoryCode(), e.getExpenseSubcategoryCode(), e.getDescription(), e.getExpenseAt(),
                e.getClaimedAmount(), e.getClaimedCurrencyCode(),
                e.getEligibleAmount(), e.getNonReimbursableAmount(), e.getApprovedAmount(),
                e.getSettlementCurrencyCode(), e.getExchangeRate(), e.getConvertedClaimedAmount(),
                e.getReceiptRequired(), e.getReceiptAttached(), e.getReceiptDocumentId(), e.getReceiptReference(),
                e.getStatus(), e.getRejectionReason(), e.getReviewedBy(), e.getReviewedAt(),
                e.getPaidAt(), e.getPaymentReference(), e.getRemarks(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
    }
}

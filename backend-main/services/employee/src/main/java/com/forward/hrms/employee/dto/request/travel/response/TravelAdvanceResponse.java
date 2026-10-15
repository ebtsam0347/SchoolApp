package com.forward.hrms.employee.dto.request.travel.response;

import com.forward.hrms.attendance.dto.response.ApprovalStepDto;
import com.forward.hrms.attendance.dto.response.CurrentStageDto;
import com.forward.hrms.employee.entity.travel.TravelRequestAdvance;
import com.forward.hrms.employee.entity.travel.TravelRequestParticipant;
import com.forward.hrms.employee.enums.travel.TravelAdvancePaymentMethodMode;
import com.forward.hrms.employee.enums.travel.TravelAdvanceStatus;
import com.forward.hrms.employee.enums.travel.TravelAdvanceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response representation of a Travel Request Advance. Exposes the
 * business information required by the client while keeping internal
 * JPA details hidden. Approval, payment and settlement information is
 * included because it represents the current lifecycle state.
 */
public record TravelAdvanceResponse(

        /* Database identifier of the Travel Advance. */
        Long id,

        /* ID of the Travel Request this advance belongs to. */
        Long travelRequestId,

        /* ID of the Travel Request Participant responsible for this advance. */
        Long travelRequestParticipantId,

        /* Business employee code of the participant. */
        String employeeCode,

        /* Employee display name associated with the participant. */
        String employeeName,

        /* Human-readable unique advance number, e.g. ADV-2026-000125. */
        String advanceNumber,

        /* Business type: INITIAL, ADDITIONAL, EMERGENCY, ADJUSTMENT. */
        TravelAdvanceType advanceType,

        /* Amount originally requested. */
        BigDecimal requestedAmount,

        /* Currency of the requested amount. */
        String requestedCurrencyCode,

        /* Amount approved by the authorized approval process. */
        BigDecimal approvedAmount,

        /* Currency in which the advance was approved. */
        String approvedCurrencyCode,

        /* Amount actually released/paid to the employee. */
        BigDecimal paidAmount,

        /* Currency in which the payment was actually made. */
        String paidCurrencyCode,

        /* Current lifecycle state of the advance. */
        TravelAdvanceStatus status,

        /* Business justification for the advance. */
        String purpose,

        /* Additional requester remarks. */
        String remarks,

        /* Reference to the external Approval Module request. */
        Long approvalRequestId,

        /* Timestamp when the advance was approved. */
        LocalDateTime approvedAt,

        /* Employee/user who approved the advance. Null until approved. */
        Long approvedBy,

        /* Payment reference returned by Payroll/Finance. */
        String paymentReference,

        /* External Payroll/Finance payment transaction identifier. */
        String externalPaymentId,

        /* Timestamp when payment was actually released. */
        LocalDateTime paidAt,

        /* Amount of the paid advance already consumed/settled against expenses. */
        BigDecimal settledAmount,

        /* Amount returned by the employee to the company. */
        BigDecimal returnedAmount,

        /* Currency used for settlement/reconciliation. */
        String settlementCurrencyCode,

        /* Payment method used by Payroll/Finance. */
        TravelAdvancePaymentMethodMode paymentMethod,

        /*
         * External reference identifying the employee's payment account.
         * Sensitive banking information is never returned through this DTO.
         */
        String paymentAccountReference,

        /* Indicates whether the external payment has been reconciled. */
        boolean paymentReconciled,

        /* Timestamp when payment reconciliation occurred. */
        LocalDateTime paymentReconciledAt,

        /* User/system actor who performed payment reconciliation. */
        Long paymentReconciledBy,

        /* Technical optimistic-locking version. */
        Long version,

        /* The PENDING stage of the TRAVEL_ADVANCE approval chain; null once finalized or still DRAFT. */
        CurrentStageDto currentStage,

        /* Every stage of the TRAVEL_ADVANCE approval chain in order; empty while DRAFT. */
        List<ApprovalStepDto> steps,

        /* Lifecycle actions the calling user can take right now (APPROVE / REJECT / CANCEL). */
        List<String> actions
) {

    /** Maps the entity 1:1; {@code employeeCode}/{@code employeeName} and the approval chain are empty. */
    public static TravelAdvanceResponse of(TravelRequestAdvance e) {
        return of(e, null, null, List.of(), List.of());
    }

    /** Overload that fills {@code employeeCode} / {@code employeeName} from the participant snapshot. */
    public static TravelAdvanceResponse of(TravelRequestAdvance e, TravelRequestParticipant participant) {
        return of(e, participant, null, List.of(), List.of());
    }

    public static TravelAdvanceResponse of(TravelRequestAdvance e, TravelRequestParticipant participant,
                                           CurrentStageDto currentStage,
                                           List<ApprovalStepDto> steps,
                                           List<String> actions) {
        return new TravelAdvanceResponse(
                e.getId(), e.getTravelRequestId(), e.getTravelRequestParticipantId(),
                participant == null ? null : participant.getEmployeeNoSnapshot(),
                participant == null ? null : participant.getEmployeeNameSnapshot(),
                e.getAdvanceNumber(), e.getAdvanceType(),
                e.getRequestedAmount(), e.getRequestedCurrencyCode(),
                e.getApprovedAmount(), e.getApprovedCurrencyCode(),
                e.getPaidAmount(), e.getPaidCurrencyCode(),
                e.getStatus(), e.getPurpose(), e.getRemarks(), e.getApprovalRequestId(),
                e.getApprovedAt(), e.getApprovedBy(),
                e.getPaymentReference(), e.getExternalPaymentId(), e.getPaidAt(),
                e.getSettledAmount(), e.getReturnedAmount(), e.getSettlementCurrencyCode(),
                e.getPaymentMethod(), e.getPaymentAccountReference(),
                e.isPaymentReconciled(), e.getPaymentReconciledAt(), e.getPaymentReconciledBy(),
                e.getVersion(),
                currentStage, steps, actions);
    }
}

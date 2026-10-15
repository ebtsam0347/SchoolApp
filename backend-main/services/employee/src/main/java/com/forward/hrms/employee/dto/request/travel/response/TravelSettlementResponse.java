package com.forward.hrms.employee.dto.request.travel.response;

import com.forward.hrms.attendance.dto.response.ApprovalStepDto;
import com.forward.hrms.attendance.dto.response.CurrentStageDto;
import com.forward.hrms.employee.enums.travel.TravelSettlementDirection;
import com.forward.hrms.employee.enums.travel.TravelSettlementStatus;
import com.forward.hrms.employee.entity.travel.TravelRequestParticipant;
import com.forward.hrms.employee.entity.travel.TravelRequestSettlement;
import com.forward.hrms.employee.enums.travel.TravelSettlementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response representation of a Travel Request Settlement. Exposes the
 * calculated financial result together with its settlement items.
 * System-controlled values (settlement number, approval / payment /
 * finalization information, calculated totals) are set by the backend.
 */
public record TravelSettlementResponse(

        /* Unique database identifier of the settlement. */
        Long id,

        /* ID of the Travel Request being settled. */
        Long travelRequestId,

        /* ID of the Travel Request Participant whose responsibility is being settled. */
        Long participantId,

        /* Employee code of the participant. */
        String employeeCode,

        /* Human-readable settlement reference, e.g. SET-2026-000125. */
        String settlementNumber,

        /* Type of settlement. */
        TravelSettlementType settlementType,

        /* Current lifecycle status of the settlement. */
        TravelSettlementStatus status,

        /* Total amount claimed through expense records included in this settlement. */
        BigDecimal totalClaimedExpenses,

        /* Total amount eligible according to Travel Policy. */
        BigDecimal totalEligibleExpenses,

        /* Total amount finally approved by the reviewer. */
        BigDecimal totalApprovedExpenses,

        /* Total amount of travel advances included in the settlement. */
        BigDecimal totalAdvancesPaid,

        /* Positive/negative business adjustments applied during settlement. */
        BigDecimal totalAdjustments,

        /* Final amount after reconciliation. */
        BigDecimal finalSettlementAmount,

        /* Whether the final balance is payable to, receivable from, or zero for the employee. */
        TravelSettlementDirection settlementDirection,

        /* Currency used for the final settlement. */
        String settlementCurrencyCode,

        /* Human-readable explanation of the settlement. */
        String settlementSummary,

        /* Approval workflow request ID from the Approval Module. */
        Long approvalRequestId,

        /* Timestamp when the settlement was approved. */
        LocalDateTime approvedAt,

        /* User/employee who approved the settlement. */
        Long approvedBy,

        /* Final company payment or employee recovery reference. */
        String settlementPaymentReference,

        /* External Payroll/Finance transaction identifier. */
        String externalTransactionId,

        /* Timestamp when the final payment/recovery occurred. */
        LocalDateTime settledAt,

        /* Indicates whether this settlement has been finalized. */
        Boolean finalized,

        /* Timestamp when the settlement was finalized. */
        LocalDateTime finalizedAt,

        /* User/system actor who finalized the settlement. */
        Long finalizedBy,

        /* Additional settlement remarks. */
        String remarks,

        /* Timestamp when the settlement was created. */
        LocalDateTime createdAt,

        /* Username that created the settlement. */
        String createdBy,

        /* Timestamp when the settlement was last updated. */
        LocalDateTime updatedAt,

        /* Username that last updated the settlement. */
        String updatedBy,

        /* Optimistic-locking version. */
        Long version,

        /* Individual financial records included in this settlement. */
        List<TravelSettlementItemResponse> items,

        /* The PENDING stage of the TRAVEL_SETTLEMENT approval chain; null once finalized or still DRAFT. */
        CurrentStageDto currentStage,

        /* Every stage of the TRAVEL_SETTLEMENT approval chain in order; empty while DRAFT. */
        List<ApprovalStepDto> steps,

        /* Lifecycle actions the calling user can take right now (APPROVE / REJECT / CANCEL). */
        List<String> actions
) {

    /** Header only; {@code items}, the resolved {@code employeeCode} and the approval chain are empty. */
    public static TravelSettlementResponse of(TravelRequestSettlement e) {
        return of(e, null);
    }

    public static TravelSettlementResponse of(TravelRequestSettlement e,
                                              List<TravelSettlementItemResponse> items) {
        return of(e, items, null);
    }

    /** Fills {@code employeeCode} from the participant snapshot. */
    public static TravelSettlementResponse of(TravelRequestSettlement e,
                                              List<TravelSettlementItemResponse> items,
                                              TravelRequestParticipant participant) {
        return of(e, items, participant, null, List.of(), List.of());
    }

    public static TravelSettlementResponse of(TravelRequestSettlement e,
                                              List<TravelSettlementItemResponse> items,
                                              TravelRequestParticipant participant,
                                              CurrentStageDto currentStage,
                                              List<ApprovalStepDto> steps,
                                              List<String> actions) {
        return new TravelSettlementResponse(
                e.getId(), e.getTravelRequestId(), e.getTravelRequestParticipantId(),
                participant == null ? null : participant.getEmployeeNoSnapshot(),
                e.getSettlementNumber(), e.getSettlementType(), e.getStatus(),
                e.getTotalClaimedExpenses(), e.getTotalEligibleExpenses(), e.getTotalApprovedExpenses(),
                e.getTotalAdvancesPaid(), e.getTotalAdjustments(), e.getFinalSettlementAmount(),
                e.getSettlementDirection(), e.getSettlementCurrencyCode(), e.getSettlementSummary(),
                e.getApprovalRequestId(), e.getApprovedAt(), e.getApprovedBy(),
                e.getSettlementPaymentReference(), e.getExternalTransactionId(), e.getSettledAt(),
                e.getIsFinalized(), e.getFinalizedAt(), e.getFinalizedBy(), e.getRemarks(),
                e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(),
                e.getVersion(), items,
                currentStage, steps, actions);
    }
}

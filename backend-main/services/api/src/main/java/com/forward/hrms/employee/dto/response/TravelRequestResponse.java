package com.forward.hrms.employee.dto.response;

import com.forward.hrms.attendance.dto.response.ApprovalStepDto;
import com.forward.hrms.attendance.dto.response.CurrentStageDto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response returned when retrieving a Travel Request. Represents the
 * overall Travel Request and its related participant and leg information,
 * providing a stable API contract independent of the persistence model.
 *
 * <p>Lives in the shared {@code api} module — like {@link FactoryTourDto} and
 * {@code RequisitionDto} — so it can be reused directly as an approvals-inbox
 * bucket in the BFF's {@code CombinedApprovalsInbox} composite, exactly as
 * the request/response contract of the owning {@code TravelRequestController}
 * endpoint. Entity-to-DTO mapping stays in employee-service's
 * {@code TravelRequestService} (mirrors {@code FactoryTourDtoAssembler}).
 */
@Schema(name = "TravelRequestResponse")
public record TravelRequestResponse(

        /* Database identifier of the Travel Request. */
        Long id,

        /* Human-readable business request number, e.g. TR-2026-000125. */
        String requestNo,

        /* Employee code of the employee who initiated the request. */
        String requesterEmployeeCode,

        /* Name of the employee who initiated the request. */
        String requesterEmployeeName,

        /* Type of travel, e.g. FACTORY_VISIT. */
        String travelTypeCode,

        /* Currency used for the Travel Request, e.g. PKR. */
        String currencyCode,

        /* Overall purpose of the travel. */
        String purpose,

        /* Planned travel start date and time. */
        LocalDateTime startDatetime,

        /* Planned travel end date and time. */
        LocalDateTime endDatetime,

        /* Journey legs of the overall trip. */
        List<TravelRequestLegResponse> legs,

        /* Identifier of the applicable travel policy version. */
        Long policyVersionId,

        /* Human-readable policy version number applied to the request. */
        Integer policyVersionNo,

        /* Current lifecycle status of the Travel Request. */
        String status,

        /* Reference returned by the Approval module. */
        String approvalReference,

        /* Estimated total cost of the trip. */
        BigDecimal estimatedAmount,

        /*
         * Fuel policy applicable to the overall request: the single policy
         * shared by every leg that has one, or null when legs carry
         * different fuel policies. See each leg's travelFuelPolicyId for
         * the per-leg source of truth.
         */
        Long travelFuelPolicyId,

        /* Sum of every leg's petrolIssued, maintained at Transport verification. */
        BigDecimal petrolIssued,

        /*
         * Sum of every leg's actualDistanceKm, maintained at Transport
         * verification. Pure summary — each leg's actualDistanceKm remains
         * the sole authoritative Transport-approved distance.
         */
        BigDecimal totalDistanceKm,

        /* Timestamp when the request was submitted. */
        LocalDateTime submittedAt,

        /* Timestamp when the request was approved. */
        LocalDateTime approvedAt,

        /* Timestamp when actual travel started. */
        LocalDateTime startedAt,

        /* Timestamp when the travel was completed. */
        LocalDateTime completedAt,

        /* Timestamp when the request was canceled. */
        LocalDateTime canceledAt,

        /* Reason for cancellation. */
        String cancellationReason,

        /* Employees associated with this Travel Request. */
        List<TravelRequestParticipantResponse> participants,

        /* Timestamp when the record was created. */
        LocalDateTime createdAt,

        /* Timestamp when the record was last updated. */
        LocalDateTime updatedAt,

        /* Optimistic-locking version. */
        Long version,

        /* The PENDING stage of the TRAVEL_REQUEST approval chain; null once finalized or still DRAFT. */
        @Schema(nullable = true) CurrentStageDto currentStage,

        /* Every stage of the TRAVEL_REQUEST approval chain in order; empty while DRAFT. */
        List<ApprovalStepDto> steps,

        /* Lifecycle actions the calling user can take right now (APPROVE / REJECT / CANCEL). */
        List<String> actions
) {
}

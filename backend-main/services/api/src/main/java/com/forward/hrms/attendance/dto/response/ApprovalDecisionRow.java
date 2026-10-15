package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * §12.2 — one row in the "approvals I have decided" history view, unified
 * across every commons-approval request type in the system: attendance's
 * leave / overtime / regularization / short leave, payroll's permanent /
 * short-term advances, and employee's requisitions / factory tours. The
 * {@code type} discriminator tells the FE which date / detail fields are
 * populated. Reused across three services' own {@code /approvals/history}
 * endpoints (same convention as {@link ApprovalStepDto} /
 * {@link CurrentStageDto}) and by the BFF's cross-service composite.
 *
 * <p>Not every type has a single subject employee: SHORT_TERM_ADVANCE
 * (a batch spans N employees), REQUISITION_NEW_HIRE (a role, not a
 * person), and FACTORY_TOUR_* (denormalized cardNo/name, no employeeId
 * column) all report {@code employeeId = 0} with null employeeCode /
 * fullName.
 */
@Schema(name = "ApprovalDecisionRow",
        description = "An approval decision (APPROVED or REJECTED) the caller closed at one stage " +
                "of one request. Unified across every approval-having request type in the system.")
public record ApprovalDecisionRow(
        @Schema(description = "Type discriminator.",
                allowableValues = {"LEAVE", "OVERTIME", "REGULARIZATION", "SHORT_LEAVE",
                        "PERMANENT_ADVANCE", "SHORT_TERM_ADVANCE",
                        "REQUISITION_NEW_HIRE", "REQUISITION_REPLACEMENT",
                        "FACTORY_TOUR_LOCAL", "FACTORY_TOUR_INTL",
                        "TRAVEL_REQUEST", "TRAVEL_ADVANCE", "TRAVEL_SETTLEMENT"})
        String type,
        long requestId,
        @Schema(nullable = true, description = "Human-readable request code (LR-…, OT-…, REG-…, SL-…, " +
                "PA-…, STA-…, tour requestId, TR-…, ADV-…, SET-…). Null for REQUISITION_* — no code column.")
        String requestCode,
        @Schema(description = "0 when the type has no single subject employee — see class doc.")
        long employeeId,
        @Schema(nullable = true) String employeeCode,
        @Schema(nullable = true) String fullName,
        @Schema(description = "When the caller closed the stage.")
        LocalDateTime decidedAt,
        @Schema(description = "When the underlying request was created — used to compute decision latency.")
        LocalDateTime requestCreatedAt,
        @Schema(description = "Stage outcome.", allowableValues = {"APPROVED", "REJECTED"})
        String decision,
        @Schema(description = "1-based stage index.") int stageNo,
        @Schema(description = "Stage role (MANAGER, HR, …).") String stageRole,
        @Schema(nullable = true, description = "Note recorded with the decision.") String note,
        @Schema(nullable = true, description = "LEAVE: fromDate. OVERTIME / REGULARIZATION: workDate. " +
                "SHORT_LEAVE: leaveDate. REQUISITION_*: requiredBy. FACTORY_TOUR_*: tourStart. " +
                "Null for advances.")
        LocalDate startDate,
        @Schema(nullable = true, description = "LEAVE: toDate. FACTORY_TOUR_*: tourEnd. Null otherwise.")
        LocalDate endDate,
        @Schema(nullable = true, description = "LEAVE: leave-type code (e.g. ANNUAL).")
        String leaveTypeCode,
        @Schema(nullable = true, description = "LEAVE: total working days requested.")
        BigDecimal days,
        @Schema(nullable = true, description = "Free-text reason/description/purpose/notes — " +
                "LEAVE / REGULARIZATION / SHORT_LEAVE: reason. PERMANENT_ADVANCE: description. " +
                "SHORT_TERM_ADVANCE: notes. REQUISITION_*: reason. FACTORY_TOUR_*: purpose.")
        String reason
) {}

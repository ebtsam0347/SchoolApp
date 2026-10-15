package com.forward.hrms.attendance.dto.request;

import com.forward.hrms.attendance.enums.LeaveCycleAnchor;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "UpdateLeaveTypeRequest",
        description = "HR-admin partial update. Any field left null is unchanged. `code` is " +
                "immutable; to retire a code use DELETE (soft-deactivate) and create a new type.")
public record UpdateLeaveTypeRequest(
        @Schema(nullable = true) @Size(min = 1, max = 64) String name,
        @Schema(nullable = true) Boolean paid,
        @Schema(nullable = true) @DecimalMin("0") BigDecimal defaultPerYear,
        @Schema(nullable = true) LeaveCycleAnchor cycleAnchor,
        @Schema(nullable = true) Boolean prorateOnEntry,
        @Schema(nullable = true) Boolean encashable,
        @Schema(nullable = true) @DecimalMin("0") BigDecimal carryForwardMax,
        @Schema(nullable = true) Boolean requiresApproval,
        @Schema(nullable = true) Boolean active,
        @Schema(nullable = true) Boolean showOnDashboard
) {}

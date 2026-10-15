package com.forward.hrms.attendance.dto.request;

import com.forward.hrms.attendance.enums.LeaveCycleAnchor;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "CreateLeaveTypeRequest",
        description = "HR-admin create payload. `code` must be unique and is immutable post-create — " +
                "it is denormalized into balance rows, leave requests, and audit events.")
public record CreateLeaveTypeRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ANNUAL")
        @NotBlank @Size(min = 1, max = 32) String code,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Annual leave")
        @NotBlank @Size(min = 1, max = 64) String name,

        @Schema(description = "Defaults to true.", nullable = true) Boolean paid,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "14")
        @NotNull @DecimalMin("0") BigDecimal defaultPerYear,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Entitlement cycle: JOINING (anniversary, e.g. annual leave), " +
                        "FISCAL (Jul–Jun, e.g. M1/Casual), or NONE (occasional, calendar-year lazy).")
        @NotNull LeaveCycleAnchor cycleAnchor,

        @Schema(description = "Prorate the first cycle for a mid-cycle joiner. FISCAL types only. " +
                "Defaults to false.", nullable = true) Boolean prorateOnEntry,

        @Schema(description = "Encash unused balance when the cycle closes at renewal. " +
                "Defaults to false.", nullable = true) Boolean encashable,

        @Schema(description = "Defaults to 0.", nullable = true)
        @DecimalMin("0") BigDecimal carryForwardMax,

        @Schema(description = "Defaults to true.", nullable = true) Boolean requiresApproval,

        @Schema(description = "Defaults to true.", nullable = true) Boolean showOnDashboard
) {}

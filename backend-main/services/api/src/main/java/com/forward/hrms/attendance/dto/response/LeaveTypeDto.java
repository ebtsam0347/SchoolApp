package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "LeaveTypeDto")
public record LeaveTypeDto(
        long id,
        String code,
        String name,
        boolean paid,
        BigDecimal defaultPerYear,
        @Schema(description = "Entitlement cycle anchor: JOINING, FISCAL, or NONE.")
        String cycleAnchor,
        @Schema(description = "Prorate the first cycle for a mid-cycle joiner (FISCAL types).")
        boolean prorateOnEntry,
        @Schema(description = "Unused balance is encashed when the cycle closes at renewal.")
        boolean encashable,
        BigDecimal carryForwardMax,
        boolean requiresApproval,
        boolean active,
        @Schema(description = "If false, the dashboard balance card hides this type. " +
                "It still appears on the apply-leave drawer.")
        boolean showOnDashboard
) {}

package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateAnnualLeavePaymentStatusRequest")
public record UpdateAnnualLeavePaymentStatusRequest(
        @NotNull
        @Schema(description = "Set true to mark paid, false to mark unpaid.")
        Boolean paymentStatus
) {
}

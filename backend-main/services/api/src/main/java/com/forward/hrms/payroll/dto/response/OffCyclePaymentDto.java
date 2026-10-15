package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.OffCyclePaymentMode;
import com.forward.hrms.payroll.enums.OffCyclePaymentSource;
import com.forward.hrms.payroll.enums.OffCyclePaymentStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(name = "OffCyclePaymentDto")
public record OffCyclePaymentDto(
        long id,
        long employeeId,
        LocalDate paidOn,
        BigDecimal amount,
        String reason,
        @Schema(nullable = true) OffCyclePaymentMode paymentMode,
        @Schema(nullable = true) String referenceNo,
        @Schema(nullable = true) Long sourceEventId,
        @Schema(nullable = true) Long sourceInstallmentId,

        @Schema(description = "Internal transaction classification — HR, BONUS or FINAL_SETTLEMENT. "
                + "Not the HR-facing type; that is offCyclePaymentTypeName.")
        OffCyclePaymentSource source,

        @Schema(nullable = true,
                description = "Selected employee-service off-cycle payment type. Null on "
                        + "system-generated rows and on rows predating the master reference.")
        Long offCyclePaymentTypeId,
        @Schema(nullable = true,
                description = "The type's name as HR saw it when recording, so a later rename or "
                        + "deactivation of the master leaves this payment readable.")
        String offCyclePaymentTypeName,

        @Schema(description = "Lifecycle state. HR_APPROVED is authorized-but-unpaid; "
                + "PAID and CANCELLED are terminal and immutable.")
        OffCyclePaymentStatus status,

        @Schema(nullable = true, description = "User id of the HR user whose creation authorized this "
                + "payment. Null on rows migrated from before the lifecycle existed.")
        Long hrApprovedBy,
        @Schema(nullable = true) LocalDateTime hrApprovedAt,

        @Schema(nullable = true, description = "User id of the Accounts user who executed the payment. "
                + "Null until paid, and on rows migrated from before the lifecycle existed.")
        Long paidBy,
        @Schema(nullable = true) LocalDateTime paidAt,

        @Schema(description = "Username that created the row (audit column).")
        String createdBy,
        LocalDateTime createdAt,
        @Schema(description = "Username that last modified the row (audit column).")
        String updatedBy,
        LocalDateTime updatedAt
) {}

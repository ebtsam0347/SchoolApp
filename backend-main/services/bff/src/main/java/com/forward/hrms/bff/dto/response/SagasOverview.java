package com.forward.hrms.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cross-service saga overview for the operator console. Pulls rows from
 * both employee-service and payroll-service so the FE doesn't have to fan
 * out client-side. Each row carries a {@code service} discriminator so
 * the FE can deep-link into the right per-service detail view.
 */
@Schema(name = "SagasOverview",
        description = "Merged saga rows across employee + payroll. Sorted by updatedAt desc.")
public record SagasOverview(
        @Schema(description = "Merged rows, newest first.") List<Row> rows,
        @Schema(description = "Count of rows from employee-service in this response.") int employeeCount,
        @Schema(description = "Count of rows from payroll-service in this response.") int payrollCount,
        @Schema(description = "True if employee-service read failed (rows partial).") boolean employeeDegraded,
        @Schema(description = "True if payroll-service read failed (rows partial).") boolean payrollDegraded
) {

    @Schema(name = "SagaRow", description = "One saga row, annotated with its origin service.")
    public record Row(
            @Schema(description = "Which service the saga row lives in.",
                    allowableValues = {"employee", "payroll"}) String service,
            long id,
            String sagaName,
            String correlationKey,
            String status,
            String currentStep,
            int stepIndex,
            int attemptCount,
            LocalDateTime nextAttemptAt,
            String lastError,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String createdBy
    ) {}
}

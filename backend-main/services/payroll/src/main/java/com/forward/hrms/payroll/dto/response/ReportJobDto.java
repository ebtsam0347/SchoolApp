package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.ReportJob;
import com.forward.hrms.payroll.enums.ReportJobStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Async report job. Polled until status flips to READY, then the FE calls /reports/jobs/{id}/download.")
public record ReportJobDto(
        @Schema(description = "Job id.", requiredMode = Schema.RequiredMode.REQUIRED, example = "42") long id,
        @Schema(description = "Report type discriminator.", requiredMode = Schema.RequiredMode.REQUIRED, example = "PAY_REGISTER") String type,
        @Schema(description = "Lifecycle status.", requiredMode = Schema.RequiredMode.REQUIRED) ReportJobStatus status,
        @Schema(description = "Username that requested the report.", example = "saqib") String requestedBy,
        @Schema(description = "When the job was queued.", requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime createdAt,
        @Schema(description = "When the runner picked it up.") LocalDateTime startedAt,
        @Schema(description = "When the runner finished (success or failure).") LocalDateTime completedAt,
        @Schema(description = "Output file size in bytes; populated on READY.") Long outputSize,
        @Schema(description = "Error code on FAILED.") String errorCode,
        @Schema(description = "Error message on FAILED.") String errorMessage
) {
    public static ReportJobDto of(ReportJob j) {
        return new ReportJobDto(
                j.getId(),
                j.getType(),
                j.getStatus(),
                j.getRequestedBy(),
                j.getCreatedAt(),
                j.getStartedAt(),
                j.getCompletedAt(),
                j.getOutputSize(),
                j.getErrorCode(),
                j.getErrorMessage());
    }
}

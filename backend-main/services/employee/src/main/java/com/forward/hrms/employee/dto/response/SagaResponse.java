package com.forward.hrms.employee.dto.response;

import com.forward.hrms.commons.saga.SagaState;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "SagaResponse", description = "Status snapshot of a durable saga row.")
public record SagaResponse(
        long id,
        String sagaName,
        String correlationKey,
        @Schema(description = "RUNNING | COMPLETED | FAILED.") String status,
        String currentStep,
        int stepIndex,
        int attemptCount,
        LocalDateTime nextAttemptAt,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String createdBy
) {
    public static SagaResponse of(SagaState s) {
        return new SagaResponse(
                s.getId(),
                s.getSagaName(),
                s.getCorrelationKey(),
                s.getStatus().name(),
                s.getCurrentStep(),
                s.getStepIndex(),
                s.getAttemptCount(),
                s.getNextAttemptAt(),
                s.getLastError(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                s.getCreatedBy());
    }
}

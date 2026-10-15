package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

@Schema(name = "SetAdvanceWaiverRequest",
        description = "Waive or restore a skipped employee's permanent-advance installment for the run's month. " +
                "The advance's own monthly installment is not changed.")
public record SetAdvanceWaiverRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "true: the retry deducts 0 this month (a 0 ledger row is written). false: deduct " +
                        "the usual installment.")
        @NotNull Boolean waived
) {}

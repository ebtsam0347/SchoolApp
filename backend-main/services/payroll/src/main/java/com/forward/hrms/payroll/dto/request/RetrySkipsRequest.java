package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "RetrySkipsRequest",
        description = "Re-run compute for previously-skipped employees on a COMPUTED run. " +
                "Empty/omitted `employeeIds` retries every skip row on the run; otherwise only the " +
                "listed employees are retried. Ids that aren't currently skipped are quietly ignored.")
public record RetrySkipsRequest(
        @Schema(nullable = true,
                description = "Subset of employees to retry; omit or pass an empty list to retry every skip.")
        List<Long> employeeIds
) {}

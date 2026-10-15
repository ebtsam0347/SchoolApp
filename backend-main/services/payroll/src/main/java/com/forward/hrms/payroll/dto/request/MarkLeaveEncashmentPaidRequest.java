package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MarkLeaveEncashmentPaidRequest")
public record MarkLeaveEncashmentPaidRequest(
        @Schema(nullable = true,
                description = "Optional payroll run id when paid as part of a regular run. " +
                        "Leave null for off-cycle / cash payouts.")
        Long payrollRunId
) {}

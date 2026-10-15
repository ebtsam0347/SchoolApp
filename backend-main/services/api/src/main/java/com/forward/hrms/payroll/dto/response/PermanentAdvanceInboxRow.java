package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.AdvanceApprovalStage;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PermanentAdvanceInboxRow",
        description = "A permanent advance pending the caller's decision, tagged with which stage " +
                "it's sitting at so a caller holding both payroll.advance.hr and payroll.advance.accounts " +
                "sees one merged list instead of two buckets.")
public record PermanentAdvanceInboxRow(
        PermanentAdvanceDto request,
        @Schema(description = "HR_APPROVAL (status=PENDING) or ACCOUNTS_APPROVAL (status=HR_APPROVED).")
        AdvanceApprovalStage approvalStage
) {}

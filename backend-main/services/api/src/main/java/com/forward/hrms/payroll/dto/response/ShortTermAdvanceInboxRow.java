package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.AdvanceApprovalStage;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ShortTermAdvanceInboxRow",
        description = "A short-term advance batch pending the caller's decision, tagged with which stage " +
                "it's sitting at so a caller holding both payroll.advance.hr and payroll.advance.accounts " +
                "sees one merged list instead of two buckets.")
public record ShortTermAdvanceInboxRow(
        ShortTermAdvanceBatchDto request,
        @Schema(description = "MANAGER_APPROVAL (status=PENDING), HR_APPROVAL (status=MANAGER_APPROVED) or " +
                "ACCOUNTS_APPROVAL (status=HR_APPROVED).")
        AdvanceApprovalStage approvalStage
) {}

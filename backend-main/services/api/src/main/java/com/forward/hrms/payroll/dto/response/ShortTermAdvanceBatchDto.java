package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.attendance.dto.response.ApprovalStepDto;
import com.forward.hrms.attendance.dto.response.CurrentStageDto;
import com.forward.hrms.payroll.enums.AdvanceStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "ShortTermAdvanceBatchDto")
public record ShortTermAdvanceBatchDto(
        long id,
        @Schema(description = "Human-readable code, e.g. STA-2026-0042.") String requestCode,
        @Schema(nullable = true) Long departmentId,
        @Schema(nullable = true) Long sectionId,
        int targetYear,
        int targetMonth,
        BigDecimal totalAmount,
        int totalEmployees,
        AdvanceStatus status,
        @Schema(nullable = true) Long hrDecidedBy,
        @Schema(nullable = true) LocalDateTime hrDecidedAt,
        @Schema(nullable = true) String hrNote,
        @Schema(nullable = true) Long accountsDecidedBy,
        @Schema(nullable = true) LocalDateTime accountsDecidedAt,
        @Schema(nullable = true) String accountsNote,
        @Schema(nullable = true) String rejectionReason,
        @Schema(nullable = true) String notes,
        @Schema(description = "Per-employee detail. Included on detail reads, omitted on list pages.", nullable = true)
        List<ShortTermAdvanceLineDto> lines,
        LocalDateTime createdAt,
        @Schema(nullable = true,
                description = "Derived from the real commons-approval chain (SHORT_TERM_ADVANCE). Same " +
                        "shape as leave/overtime/regularization so the FE renders one stepper component " +
                        "everywhere.")
        CurrentStageDto currentStage,
        List<ApprovalStepDto> steps,
        @Schema(description = "Actions the calling user can take right now.") List<String> actions
) {}

package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.attendance.dto.response.ApprovalStepDto;
import com.forward.hrms.attendance.dto.response.CurrentStageDto;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.payroll.enums.AdvanceStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "PermanentAdvanceDto")
public record PermanentAdvanceDto(
        long id,
        @Schema(description = "Human-readable code, e.g. PA-2026-0042.") String requestCode,
        long employeeId,
        @Schema(nullable = true,
                description = "Employee code, name, department, section and designation, resolved from the " +
                        "shared employee cache.")
        EmployeeSummary employee,
        @Schema(nullable = true,
                description = "Employee's date of joining, snapshotted when the advance was created. " +
                        "Null on advances created before it was recorded.")
        LocalDate dateOfJoining,
        BigDecimal amount,
        BigDecimal monthlyDeduction,
        BigDecimal bonusOffset,
        BigDecimal annualLeaveOffset,
        @Schema(nullable = true) BigDecimal gratuityAtRequest,
        @Schema(nullable = true) LocalDate receivingDate,
        @Schema(nullable = true) String description,
        AdvanceStatus status,
        @Schema(description = "Sum of all deductions applied so far, rupees.") BigDecimal deductedTotal,
        @Schema(description = "Outstanding balance, rupees = amount - deductedTotal.") BigDecimal remainingBalance,
        @Schema(nullable = true) Long hrDecidedBy,
        @Schema(nullable = true) LocalDateTime hrDecidedAt,
        @Schema(nullable = true) String hrNote,
        @Schema(nullable = true) Long accountsDecidedBy,
        @Schema(nullable = true) LocalDateTime accountsDecidedAt,
        @Schema(nullable = true) String accountsNote,
        @Schema(nullable = true) String rejectionReason,
        @Schema(nullable = true,
                description = "§7.payroll #38 — populated on disburse(). null until then.")
        LocalDateTime disbursedAt,
        @Schema(nullable = true,
                description = "§7.payroll #38 — userId of the disbursing actor.")
        Long disbursedBy,
        @Schema(nullable = true) LocalDateTime closedAt,
        LocalDateTime createdAt,
        Long cardNo,
        @Schema(nullable = true,
                description = "Derived from the real commons-approval chain (PERMANENT_ADVANCE). Same " +
                        "shape as leave/overtime/regularization so the FE renders one stepper component " +
                        "everywhere.")
        CurrentStageDto currentStage,
        List<ApprovalStepDto> steps,
        @Schema(description = "Actions the calling user can take right now.") List<String> actions,

        String fiscalYearCode,
        BigDecimal bonusOffset2,
        LocalDate annualLeaveMonth
) {}

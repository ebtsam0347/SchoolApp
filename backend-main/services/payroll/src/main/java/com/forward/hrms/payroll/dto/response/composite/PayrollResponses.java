package com.forward.hrms.payroll.dto.response.composite;

import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.payroll.dto.response.PayslipDto;
import com.forward.hrms.payroll.dto.response.PayrollRunDto;
import com.forward.hrms.payroll.dto.response.BonusInstallmentDto;
import com.forward.hrms.payroll.dto.response.OffCyclePaymentDto;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryDto;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryComponentDto;
import com.forward.hrms.payroll.dto.response.SalaryComponentDto;
import com.forward.hrms.payroll.dto.response.EmployeePayrollSettingsDto;
import com.forward.hrms.payroll.dto.response.EmployeeBankDetailsDto;
import com.forward.hrms.payroll.dto.response.GratuityDto;
import com.forward.hrms.commons.web.page.PageResponse;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

/**
 * FE-facing composite shapes for the payroll surfaces. Lives in
 * payroll-service per the doctrine that single-service composites
 * belong in their owning service. Each shape pairs upstream payroll
 * data with employee-summary enrichment from the shared cache.
 *
 * <p>Money is {@link BigDecimal} throughout per the §0.5 doctrine.
 */
public final class PayrollResponses {

    private PayrollResponses() {}

    @Schema(name = "MyPayslipRow",
            description = "Self-serve payslip row: the upstream payslip plus a short-lived " +
                    "signed PDF URL when generated.")
    public record MyPayslipRow(
            long id,
            long payrollRunId,
            int periodYear,
            int periodMonth,
            String fiscalYearCode,
            BigDecimal basic,
            BigDecimal allowances,
            BigDecimal gross,
            BigDecimal deductions,
            BigDecimal tax,
            BigDecimal eobi,
            BigDecimal pessiEmployer,
            BigDecimal net,
            BigDecimal lopDays,
            BigDecimal otHours,
            BigDecimal otAmount,
            @Schema(nullable = true) LocalDateTime generatedAt,
            String status,
            @Schema(nullable = true) String heldReason,
            @Schema(nullable = true, description = "Short-lived signed MinIO URL — null when no PDF yet.")
            String pdfUrl
    ) {
        public static MyPayslipRow of(PayslipDto p, String pdfUrl) {
            return new MyPayslipRow(p.id(), p.payrollRunId(), p.periodYear(), p.periodMonth(),
                    p.fiscalYearCode(), p.basic(), p.allowances(), p.gross(), p.deductions(),
                    p.tax(), p.eobi(), p.pessiEmployer(), p.net(), p.lopDays(), p.otHours(),
                    p.otAmount(), p.generatedAt(),
                    p.status() == null ? null : p.status().name(),
                    p.heldReason(), pdfUrl);
        }
    }

    @Schema(name = "MyPayslipsResponse")
    public record MyPayslipsResponse(
            long employeeId,
            int year,
            PageResponse<MyPayslipRow> payslips
    ) {}

    @Schema(name = "PayrollRunSummary",
            description = "Server-side rollup over a run's payslips so the FE doesn't sum 100s of " +
                    "rows client-side.")
    public record PayrollRunSummary(
            int employees,
            BigDecimal basic,
            BigDecimal allowances,
            BigDecimal gross,
            BigDecimal deductions,
            BigDecimal tax,
            BigDecimal eobi,
            BigDecimal pessiEmployer,
            BigDecimal net,
            int held,
            int releasePending
    ) {}

    @Schema(name = "PayslipLineItem", description = "One earnings/deductions line — code, display name, amount.")
    public record PayslipLineItem(
            String code,
            String name,
            BigDecimal amount
    ) {}

    @Schema(name = "PayslipEarnings")
    public record PayslipEarnings(
            List<PayslipLineItem> lines,
            BigDecimal grossSalary
    ) {}

    @Schema(name = "PayslipDeductions",
            description = "Deduction lines plus EOBI, synthesized as a line here since it's tracked " +
                    "as its own payslip column rather than a payslip_lines row.")
    public record PayslipDeductions(
            List<PayslipLineItem> lines,
            BigDecimal totalDeduction
    ) {}

    @Schema(name = "PayslipEmployeeInfo")
    public record PayslipEmployeeInfo(
            long employeeId,
            String employeeCode,
            String fullName,
            @Schema(nullable = true) String fatherName,
            @Schema(nullable = true) String designation,
            @Schema(nullable = true) String department,
            @Schema(nullable = true) String grade,
            @Schema(nullable = true) LocalDate dateOfJoining,
            @Schema(nullable = true) String profilePictureUrl
    ) {}

    @Schema(name = "PayslipBankInfo")
    public record PayslipBankInfo(
            @Schema(nullable = true) String bankName,
            @Schema(nullable = true) String branch,
            @Schema(nullable = true) String accountNumber
    ) {}

    @Schema(name = "PayslipSalaryInfo")
    public record PayslipSalaryInfo(
            BigDecimal basic,
            BigDecimal grossSalary,
            BigDecimal totalDeduction,
            BigDecimal netSalary,
            BigDecimal tax,
            BigDecimal eobi,
            BigDecimal pessiEmployer,
            @Schema(nullable = true, description = "Outstanding permanent-advance balance, summed " +
                    "across the employee's open advances. Null when the lookup failed upstream.")
            BigDecimal permanentAdvanceBalance
    ) {}

    @Schema(name = "PayslipAttendanceInfo",
            description = "Attendance for the payroll period derived from the raw attendance records, the " +
                    "holiday calendar, approved leave and the joining date. Weekly off is Sunday; public " +
                    "holidays are paid non-working days; days before the joining date are outside the " +
                    "window. paidDays is the legacy Days Worked (window - weekly offs - LOP). Null when " +
                    "attendance-service could not be reached.")
    public record PayslipAttendanceInfo(
            int weeklyOffDays,
            int holidayDays,
            int workingDays,
            BigDecimal presentDays,
            BigDecimal paidLeaveDays,
            BigDecimal unpaidLeaveDays,
            BigDecimal absentDays,
            @Schema(description = "Subset of absentDays: working days with no attendance record and no " +
                    "approved leave (attendance never closed for the day).")
            BigDecimal unrecordedAbsentDays,
            int halfDayRecords,
            int presentOnOffDays,
            @Schema(description = "Full-day approved leave on a day that also has a PRESENT record.")
            int leaveConflictDays,
            BigDecimal lopDays,
            BigDecimal paidDays
    ) {}

    @Schema(name = "PayslipLeaveLine",
            description = "One paid leave type's days availed this month vs. this entitlement cycle.")
    public record PayslipLeaveLine(
            String code,
            String name,
            BigDecimal availedThisMonth,
            BigDecimal availedThisCycle
    ) {}

    @Schema(name = "PayslipAnnualLeaveInfo",
            description = "Annual leave's dedicated block (legacy A.L Total/Availed/Paid/Balance).")
    public record PayslipAnnualLeaveInfo(
            BigDecimal total,
            BigDecimal availed,
            BigDecimal paid,
            BigDecimal balance
    ) {}

    @Schema(name = "PayslipLeaveInfo")
    public record PayslipLeaveInfo(
            List<PayslipLeaveLine> leaves,
            @Schema(nullable = true) PayslipAnnualLeaveInfo annualLeave
    ) {}

    @Schema(name = "PayslipReconciliation",
            description = "Arithmetic tie-out of the displayed lines to the persisted payslip totals. " +
                    "Differences are reported, never hidden: a non-zero difference means the line-level " +
                    "breakdown is incomplete (e.g. migrated payslips whose deduction detail was never " +
                    "stored as lines), not that the persisted totals are wrong.")
    public record PayslipReconciliation(
            BigDecimal earningsLineTotal,
            @Schema(description = "persisted gross - sum(earning lines)") BigDecimal earningsDifference,
            BigDecimal deductionsLineTotal,
            @Schema(description = "persisted totalDeduction - sum(deduction lines)") BigDecimal deductionsDifference,
            @Schema(description = "gross - totalDeduction") BigDecimal netExpected,
            @Schema(description = "persisted net - (gross - totalDeduction)") BigDecimal netDifference,
            boolean reconciled,
            List<String> notes
    ) {}

    @Schema(name = "PayslipAdvanceInfo",
            description = "One advance kind for the payslip's month. Display only: never added to or " +
                    "subtracted from the net salary.")
    public record PayslipAdvanceInfo(
            @Schema(description = "Amount applied: for permanent advances the principal of the employee's open " +
                    "advances received by the period end; for short-term advances the amount approved for the " +
                    "payslip's month. 0 when there is none.")
            BigDecimal appliedAmount,
            @Schema(description = "Amount deducted in this payslip's month. Reported only when the persisted " +
                    "payslip actually contains it (bulk-imported payslips include advances in their deductions; " +
                    "engine-computed payslips deduct none), so it can never disagree with the net. 0 otherwise.")
            BigDecimal deductedAmount
    ) {}

    @Schema(name = "PayslipAdvances",
            description = "Advances block for the salary-slip preview. Purely informational and additive; every " +
                    "salary/net figure elsewhere in the row is unaffected.")
    public record PayslipAdvances(
            PayslipAdvanceInfo permanentAdvance,
            PayslipAdvanceInfo shortTermAdvance
    ) {}

    @Schema(name = "PayslipWithEmployee",
            description = "Run-detail payslip row, restructured to match Forward Sports' legacy " +
                    "payslip layout: nested employee/bank/salary objects, earnings/deductions as " +
                    "line-item lists, and an attendance + leave snapshot for the period. " +
                    "`bank` and `leaves` are null when the upstream lookup has nothing on file or " +
                    "failed (best-effort — never fails the whole row).")
    public record PayslipWithEmployee(
            long id,
            int periodYear,
            int periodMonth,
            String fiscalYearCode,
            String status,
            @Schema(nullable = true) String heldReason,
            @Schema(nullable = true) LocalDateTime generatedAt,
            PayslipEmployeeInfo employee,
            @Schema(nullable = true) PayslipBankInfo bank,
            PayslipSalaryInfo salary,
            PayslipEarnings earnings,
            PayslipDeductions deductions,
            @Schema(nullable = true) PayslipAttendanceInfo attendance,
            @Schema(nullable = true) PayslipLeaveInfo leaves,
            PayslipReconciliation reconciliation,
            PayslipAdvances advances
    ) {}

    @Schema(name = "PayrollRunDetail")
    public record PayrollRunDetail(
            PayrollRunDto run,
            PayrollRunSummary summary,
            PageResponse<PayslipWithEmployee> payslips
    ) {}

    @Schema(name = "PayrollRunsListItem")
    public record PayrollRunsListItem(
            PayrollRunDto run,
            PayrollRunSummary summary
    ) {}

    @Schema(name = "PayrollRunsListResponse")
    public record PayrollRunsListResponse(
            String fiscalYearCode,
            List<PayrollRunsListItem> runs
    ) {}

    @Schema(name = "IncrementBatchLineWithEmployee",
            description = "Increment-batch line with the employee resolved to a name + code.")
    public record IncrementBatchLineWithEmployee(
            long id,
            EmployeeSummary employee,
            BigDecimal oldBasic,
            BigDecimal newBasic,
            @Schema(nullable = true) BigDecimal oldIncomeTax,
            @Schema(nullable = true) BigDecimal newIncomeTax,
            @Schema(nullable = true) LocalDate effectiveFrom,
            String status,
            @Schema(nullable = true) String errorMessage
    ) {}

    @Schema(name = "IncrementBatchDetail")
    public record IncrementBatchDetail(
            long id,
            String name,
            String status,
            LocalDate effectiveFrom,
            @Schema(nullable = true) String notes,
            @Schema(nullable = true) LocalDateTime appliedAt,
            @Schema(nullable = true) Long appliedBy,
            List<IncrementBatchLineWithEmployee> lines
    ) {}

    @Schema(name = "BonusEventAmountWithEmployee")
    public record BonusEventAmountWithEmployee(
            long id,
            EmployeeSummary employee,
            LocalDate hireDate,
            int serviceDays,
            BigDecimal basicSnapshot,
            BigDecimal fullBonus
    ) {}

    @Schema(name = "BonusEventDetail")
    public record BonusEventDetail(
            long id,
            String fiscalYearCode,
            String name,
            String eventType,
            LocalDate referenceDate,
            int fullServiceDaysThreshold,
            String status,
            @Schema(nullable = true) String notes,
            @Schema(nullable = true) LocalDateTime activatedAt,
            @Schema(nullable = true) Long activatedBy,
            List<BonusInstallmentDto> installments,
            List<BonusEventAmountWithEmployee> amounts
    ) {}

    @Schema(name = "OffCyclePaymentRowWithEmployee",
            description = "Off-cycle payment row enriched with the employee summary.")
    public record OffCyclePaymentRowWithEmployee(
            long id,
            EmployeeSummary employee,
            LocalDate paidOn,
            BigDecimal amount,
            String reason,
            String paymentMode,
            @Schema(nullable = true) String referenceNo,
            @Schema(nullable = true) Long sourceEventId,
            @Schema(nullable = true) Long sourceInstallmentId,
            @Schema(description = "Transaction source — HR, BONUS or FINAL_SETTLEMENT. "
                    + "Not the HR-facing type.")
            String source,
            @Schema(nullable = true, description = "Selected employee-service payment type id.")
            Long offCyclePaymentTypeId,
            @Schema(nullable = true, description = "That type's name as HR saw it when recording.")
            String offCyclePaymentTypeName,
            @Schema(description = "Lifecycle state — HR_APPROVED, PAID or CANCELLED.")
            String status
    ) {}

    @Schema(name = "EmployeeSalaryTab",
            description = "One-shot bundle for the per-employee salary tab — replaces 8 fan-out calls.")
    public record EmployeeSalaryTab(
            long employeeId,
            @Schema(nullable = true) EmployeeSalaryDto currentBasic,
            List<EmployeeSalaryDto> basicHistory,
            List<EmployeeSalaryComponentDto> components,
            List<SalaryComponentDto> catalog,
            @Schema(nullable = true) EmployeePayrollSettingsDto payrollSettings,
            @Schema(nullable = true) EmployeeBankDetailsDto bankDetails,
            @Schema(nullable = true) GratuityDto gratuity,
            List<OffCyclePaymentDto> offCycle12Months
    ) {}

    @Schema(name = "PayslipQueueRow",
            description = "Disbursement queue row: HELD / RELEASE_PENDING payslips with run period " +
                    "and employee resolved.")
    public record PayslipQueueRow(
            long id,
            EmployeeSummary employee,
            int periodYear,
            int periodMonth,
            String fiscalYearCode,
            BigDecimal net,
            String status,
            @Schema(nullable = true) String heldReason,
            @Schema(nullable = true) LocalDateTime generatedAt
    ) {}

    @Schema(name = "PayslipQueueResponse")
    public record PayslipQueueResponse(
            String status,
            PageResponse<PayslipQueueRow> page
    ) {}
}

package com.forward.hrms.payroll.service.settlements;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.enums.EmployeeFinalSettlementStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FinalSettlementWorkflowValidatorService {

    private final EmployeeFinalSettlementRepository repository;
    private final PayslipRepository payslips;

    /**
     * Active settlements.
     * Employee cannot have another settlement while
     * one is still in progress.
     */
    private static final Set<EmployeeFinalSettlementStatus> ACTIVE_STATUSES =
            Set.of(
                    EmployeeFinalSettlementStatus.DRAFT,
                    EmployeeFinalSettlementStatus.HR_APPROVED,
                    EmployeeFinalSettlementStatus.PAID
            );

    /**
     * Prevent duplicate active settlements, and prevent a settlement
     * whose earned-salary days overlap a regular payroll run that's
     * already computed a payslip for the same employee/month (Audit
     * Finding 4 — without this, a regular run posted for a month before
     * that month's termination is processed pays the same days twice:
     * once via the payslip, once via the settlement's own basic-salary
     * proration). A settlement's period is always {@code [1st of
     * lastWorkingDay's month, lastWorkingDay]}, so year+month alone is
     * enough to detect the overlap. Blocks on ANY existing payslip for
     * that month regardless of status (DRAFT included) — a DRAFT
     * payslip already represents computed, about-to-be-posted pay for
     * those days, so HR must resolve it (reset/delete the payslip, or
     * exclude the employee from that run) before settling, not just
     * once it's been marked PAID.
     */
    public void canCreate(Long employeeId, LocalDate lastWorkingDay) {

        if (repository.existsByEmployeeIdAndStatusIn(
                employeeId,
                ACTIVE_STATUSES)) {

            throw new ConflictError(
                    PayrollErrorCode.FINAL_SETTLEMENT_ALREADY_ACTIVE,
                    "An active final settlement already exists for this employee."
            );
        }

        if (payslips.existsByEmployeeIdAndPeriodYearAndPeriodMonth(
                employeeId, lastWorkingDay.getYear(), lastWorkingDay.getMonthValue())) {

            throw new ConflictError(PayrollErrorCode.PAYROLL_ALREADY_PROCESSED_FOR_PERIOD,
                    "A regular payslip already exists for employee " + employeeId
                            + " for " + lastWorkingDay.getYear() + "-" + lastWorkingDay.getMonthValue()
                            + " — resolve it before creating a final settlement for this period.");
        }
    }

    /**
     * HR can approve only Draft settlement.
     */
    public void canApprove(EmployeeFinalSettlement settlement) {

        requireStatus(
                settlement,
                EmployeeFinalSettlementStatus.DRAFT
        );
    }

    /**
     * HR can reject only Draft settlement.
     */
    public void canReject(EmployeeFinalSettlement settlement) {

        requireStatus(
                settlement,
                EmployeeFinalSettlementStatus.DRAFT
        );
    }

    /**
     * Accounts can pay only HR approved settlement.
     */
    public void canMarkPaid(EmployeeFinalSettlement settlement) {

        requireStatus(
                settlement,
                EmployeeFinalSettlementStatus.HR_APPROVED
        );
    }

    /**
     * HR can cancel Draft or HR Approved settlement.
     * <p>
     * Paid settlements cannot be canceled.
     */
    public void canCancel(EmployeeFinalSettlement settlement) {

        switch (settlement.getStatus()) {

            case DRAFT:
            case HR_APPROVED:
                return;

            case PAID:
                throw new ConflictError(
                        PayrollErrorCode.FINAL_SETTLEMENT_INVALID_STATE,
                        "Paid settlement cannot be cancelled."
                );

            case CANCELLED:
                throw new ConflictError(
                        PayrollErrorCode.FINAL_SETTLEMENT_INVALID_STATE,
                        "Settlement is already cancelled."
                );

            case REJECTED:
                throw new ConflictError(
                        PayrollErrorCode.FINAL_SETTLEMENT_INVALID_STATE,
                        "Rejected settlement cannot be cancelled."
                );

            default:
                throw new ConflictError(
                        PayrollErrorCode.FINAL_SETTLEMENT_INVALID_STATE,
                        "Settlement cannot be cancelled."
                );
        }
    }

    /**
     * Helper.
     *
     * <p>409 + {@code FINAL_SETTLEMENT_INVALID_STATE}, not the bare
     * {@code IllegalStateException} this used to throw — commons-web's
     * {@code GlobalExceptionHandler} has no handler for that, so every
     * illegal transition ("approve an already-approved settlement") fell
     * through to {@code handleAny} and reached the FE as a
     * 500 INTERNAL_SERVER_ERROR instead of a retry-able conflict.
     */
    private void requireStatus(
            EmployeeFinalSettlement settlement,
            EmployeeFinalSettlementStatus expected) {

        if (settlement.getStatus() != expected) {

            throw new ConflictError(
                    PayrollErrorCode.FINAL_SETTLEMENT_INVALID_STATE,
                    "Settlement must be in status "
                            + expected
                            + " but is "
                            + settlement.getStatus()
            );
        }
    }
}
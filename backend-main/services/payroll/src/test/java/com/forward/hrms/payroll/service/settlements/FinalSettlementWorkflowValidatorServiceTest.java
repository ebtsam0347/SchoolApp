package com.forward.hrms.payroll.service.settlements;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.payroll.enums.EmployeeFinalSettlementStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plain unit test (no Spring, no DB) for
 * {@link FinalSettlementWorkflowValidatorService#canCreate}, covering
 * the payslip-overlap guard added for Audit Finding 4. The pre-existing
 * duplicate-active-settlement guard is exercised incidentally by every
 * scenario here (mocked to "none exists" unless a scenario says
 * otherwise).
 */
class FinalSettlementWorkflowValidatorServiceTest {

    private final EmployeeFinalSettlementRepository settlements = mock(EmployeeFinalSettlementRepository.class);
    private final PayslipRepository payslips = mock(PayslipRepository.class);
    private final FinalSettlementWorkflowValidatorService validator =
            new FinalSettlementWorkflowValidatorService(settlements, payslips);

    @Test
    void canCreate_noActiveSettlementAndNoPayslipForThePeriod_succeeds() {
        when(settlements.existsByEmployeeIdAndStatusIn(anyLong(), anySet())).thenReturn(false);
        when(payslips.existsByEmployeeIdAndPeriodYearAndPeriodMonth(anyLong(), anyInt(), anyInt()))
                .thenReturn(false);

        assertThatCode(() -> validator.canCreate(1L, LocalDate.of(2026, 4, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void canCreate_payslipAlreadyExistsForTheTerminationMonth_throwsConflict() {
        when(settlements.existsByEmployeeIdAndStatusIn(anyLong(), anySet())).thenReturn(false);
        when(payslips.existsByEmployeeIdAndPeriodYearAndPeriodMonth(eq(1L), eq(2026), eq(4)))
                .thenReturn(true);

        assertThatThrownBy(() -> validator.canCreate(1L, LocalDate.of(2026, 4, 20)))
                .isInstanceOf(ConflictError.class)
                .extracting(ex -> ((ConflictError) ex).code())
                .isEqualTo(PayrollErrorCode.PAYROLL_ALREADY_PROCESSED_FOR_PERIOD);
    }

    @Test
    void canCreate_payslipExistsForADifferentMonth_doesNotBlock() {
        when(settlements.existsByEmployeeIdAndStatusIn(anyLong(), anySet())).thenReturn(false);
        // A payslip exists, but for March — the April termination is unrelated.
        when(payslips.existsByEmployeeIdAndPeriodYearAndPeriodMonth(eq(1L), eq(2026), eq(3)))
                .thenReturn(true);
        when(payslips.existsByEmployeeIdAndPeriodYearAndPeriodMonth(eq(1L), eq(2026), eq(4)))
                .thenReturn(false);

        assertThatCode(() -> validator.canCreate(1L, LocalDate.of(2026, 4, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void canCreate_activeSettlementAlreadyExists_throwsIllegalState_beforeCheckingPayslips() {
        when(settlements.existsByEmployeeIdAndStatusIn(eq(1L), any())).thenReturn(true);

        assertThatThrownBy(() -> validator.canCreate(1L, LocalDate.of(2026, 4, 20)))
                .isInstanceOf(IllegalStateException.class);
    }
}

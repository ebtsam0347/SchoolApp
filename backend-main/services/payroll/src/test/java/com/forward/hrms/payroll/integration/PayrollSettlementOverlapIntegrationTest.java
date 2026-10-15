package com.forward.hrms.payroll.integration;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.employee.enums.GradeType;
import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.client.FairPriceShopClient;
import com.forward.hrms.payroll.client.dto.attendace.AttendanceSummaryDto;
import com.forward.hrms.payroll.client.dto.attendace.DurationDto;
import com.forward.hrms.payroll.client.dto.attendace.NormalOvertimeSummaryDto;
import com.forward.hrms.payroll.client.dto.attendace.TerminationAttendanceSummaryDto;
import com.forward.hrms.payroll.client.dto.employee.EmployeeTerminationInfo;
import com.forward.hrms.payroll.dto.request.CreateFinalSettlementRequest;
import com.forward.hrms.payroll.dto.response.TerminationLeavesSummaryDto;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.FiscalYear;
import com.forward.hrms.payroll.entity.PayrollConfig;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.enums.ForcedTerminationDeductionPercentage;
import com.forward.hrms.payroll.enums.NoticePayPercentage;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.PaymentMode;
import com.forward.hrms.payroll.enums.PayrollRunStatus;
import com.forward.hrms.payroll.enums.SettlementAdjustmentType;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementRepository;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryRepository;
import com.forward.hrms.payroll.repository.FiscalYearRepository;
import com.forward.hrms.payroll.repository.PayrollConfigRepository;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;
import com.forward.hrms.payroll.service.AttendanceSnapshot;
import com.forward.hrms.payroll.service.PayrollComputeService;
import com.forward.hrms.payroll.service.settlements.SettlementFinalizationService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Regression test for Audit Finding 4 (fixed): a regular payroll run
 * posted for a month, followed by a final settlement created for the
 * same employee/month, used to pay the same days twice — nothing
 * checked payslip existence before persisting a settlement. Proves both
 * halves of the fix:
 * <ol>
 *   <li>{@link com.forward.hrms.payroll.service.settlements.FinalSettlementWorkflowValidatorService#canCreate}
 *       now blocks settlement creation when a payslip already exists
 *       for the employee's termination month.</li>
 *   <li>{@link PayrollComputeService#computeRun} now excludes a
 *       terminated (inactive) employee from regular-run selection via
 *       {@code EmployeePayrollSettingsRepository.findAllByActiveTrue()},
 *       closing the {@code EmployeePayrollSettingsService.markInactive}
 *       gap the audit found.</li>
 * </ol>
 */
@SpringBootTest
@WithTestcontainers
class PayrollSettlementOverlapIntegrationTest {

    @Autowired PayrollComputeService computeService;
    @Autowired PayrollRunRepository runRepo;
    @Autowired EmployeeSalaryRepository salaryRepo;
    @Autowired EmployeePayrollSettingsRepository settingsRepo;
    @Autowired PayrollConfigRepository configRepo;
    @Autowired FiscalYearRepository fyRepo;
    @Autowired PayslipRepository payslipRepo;
    @Autowired SettlementFinalizationService settlements;
    @Autowired EmployeeFinalSettlementRepository settlementRepo;

    @MockitoBean EmployeeClient employeeClient;
    @MockitoBean AttendanceClient attendanceClient;
    @MockitoBean FairPriceShopClient fairPriceShopClient;

    private static final AtomicLong NEXT_EMPLOYEE_ID = new AtomicLong(9_700_000L);

    private YearMonth period;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    @BeforeEach
    void cleanAndSeed() {
        settlementRepo.deleteAll();
        payslipRepo.deleteAll();
        runRepo.deleteAll();
        salaryRepo.deleteAll();
        settingsRepo.deleteAll();
        configRepo.deleteAll();

        period = YearMonth.now();
        periodStart = period.atDay(1);
        periodEnd = period.atEndOfMonth();

        PayrollConfig cfg = new PayrollConfig();
        cfg.setEffectiveFrom(periodStart.minusYears(1));
        cfg.setEobiAmount(BigDecimal.ZERO);
        cfg.setPessiRatePercent(BigDecimal.ZERO);
        cfg.setPessiSalaryCap(BigDecimal.ZERO);
        cfg.setCanteenPerDayRateManagement(BigDecimal.ZERO);
        cfg.setCanteenPerDayRateOther(BigDecimal.ZERO);
        configRepo.save(cfg);

        CurrentUserHolder.set(hrActor());
    }

    @AfterEach
    void clearCaller() {
        CurrentUserHolder.clear();
    }

    @Test
    void settlementCreation_blockedWhenPayslipAlreadyExistsForTheSameMonth() {
        long employeeId = NEXT_EMPLOYEE_ID.getAndIncrement();
        BigDecimal basic = new BigDecimal("60000.00");

        EmployeePayrollSettings settings = new EmployeePayrollSettings();
        settings.setEmployeeId(employeeId);
        settings.setHireDate(periodStart.minusYears(2));
        settings.setEobiEnrolled(false);
        settings.setPaymentMode(PaymentMode.CASH);
        settingsRepo.save(settings);

        EmployeeSalary salary = new EmployeeSalary();
        salary.setEmployeeId(employeeId);
        salary.setBasic(basic);
        salary.setEffectiveFrom(periodStart.minusMonths(1));
        salaryRepo.save(salary);

        when(employeeClient.getMany(any(), any())).thenAnswer(inv ->
                Map.of(employeeId, new EmployeeClient.EmployeeBrief(
                        employeeId, "EMP-" + employeeId, "Overlap Test Employee",
                        null, null, null, null, null, null, null, null)));

        int days = period.lengthOfMonth();
        AttendanceSnapshot regularRunSnapshot = new AttendanceSnapshot(
                BigDecimal.ZERO, BigDecimal.valueOf(days), BigDecimal.ZERO,
                days, BigDecimal.valueOf(days), BigDecimal.ZERO, 0, 0);
        when(attendanceClient.snapshot(eq(employeeId), eq(periodStart), eq(periodEnd), any()))
                .thenReturn(regularRunSnapshot);

        // --- Step 1: regular payroll already ran for this month (still active). ---
        PayrollRun run = newRun();
        var computeResult = computeService.computeRun(run);
        assertThat(computeResult.employeeCount()).isEqualTo(1);
        assertThat(payslipRepo.existsByEmployeeIdAndPeriodYearAndPeriodMonth(
                employeeId, period.getYear(), period.getMonthValue())).isTrue();

        // --- Step 2: employee is terminated mid-month; HR now creates a
        // settlement covering days 1..15 of the SAME month the payslip
        // above already covers. ---
        LocalDate lwd = periodStart.plusDays(14);
        when(employeeClient.terminationInfo(employeeId)).thenReturn(new EmployeeTerminationInfo(
                employeeId, "EMP-" + employeeId, "Overlap Test Employee",
                "Dept", "Section", "Designation", "G1", GradeType.LABOUR, periodStart.minusYears(2)));

        AttendanceSummaryDto attendance = new AttendanceSummaryDto(15, 0, 0, 0, 0, 0, 0, 0);
        DurationDto zeroDuration = new DurationDto(0, "0h 0m");
        NormalOvertimeSummaryDto overtime = new NormalOvertimeSummaryDto(
                zeroDuration, zeroDuration, zeroDuration, zeroDuration);
        when(attendanceClient.terminationAttendanceSummary(employeeId, periodStart, lwd))
                .thenReturn(new TerminationAttendanceSummaryDto(employeeId, periodStart, lwd, attendance, overtime));
        when(attendanceClient.terminationLeavesSummary(employeeId, lwd))
                .thenReturn(new TerminationLeavesSummaryDto(null, null, BigDecimal.ZERO, BigDecimal.ZERO));

        CreateFinalSettlementRequest request = new CreateFinalSettlementRequest(
                employeeId, lwd, SettlementAdjustmentType.NONE,
                NoticePayPercentage.NONE, ForcedTerminationDeductionPercentage.PERCENT_0,
                null,
                "termination settlement — overlaps an already-computed payslip");

        // FIXED: settlement creation is blocked instead of silently
        // paying the same days twice.
        assertThatThrownBy(() -> settlements.create(request))
                .isInstanceOf(ConflictError.class)
                .extracting(ex -> ((ConflictError) ex).code())
                .isEqualTo(PayrollErrorCode.PAYROLL_ALREADY_PROCESSED_FOR_PERIOD);

        // Nothing persisted — the whole create() transaction rolled back.
        assertThat(settlementRepo.findAll()).noneMatch(s -> employeeId == s.getEmployeeId());
    }

    @Test
    void computeRun_excludesInactiveEmployee_afterMarkInactive() {
        long employeeId = NEXT_EMPLOYEE_ID.getAndIncrement();

        EmployeePayrollSettings settings = new EmployeePayrollSettings();
        settings.setEmployeeId(employeeId);
        settings.setHireDate(periodStart.minusYears(2));
        settings.setEobiEnrolled(false);
        settings.setPaymentMode(PaymentMode.CASH);
        // Simulates a termination that already ran markInactive() before
        // this month's regular payroll run is computed.
        settings.setActive(false);
        settingsRepo.save(settings);

        EmployeeSalary salary = new EmployeeSalary();
        salary.setEmployeeId(employeeId);
        salary.setBasic(new BigDecimal("60000.00"));
        salary.setEffectiveFrom(periodStart.minusMonths(1));
        salaryRepo.save(salary);

        PayrollRun run = newRun();
        var result = computeService.computeRun(run);

        assertThat(result.employeeCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(payslipRepo.existsByEmployeeIdAndPeriodYearAndPeriodMonth(
                employeeId, period.getYear(), period.getMonthValue())).isFalse();
    }

    // ---------------------------------------------------------------- helpers

    private PayrollRun newRun() {
        FiscalYear fy = fyRepo.findAll().stream().findFirst().orElseThrow(() ->
                new IllegalStateException("V0003 should have seeded a CURRENT fiscal year"));
        PayrollRun run = new PayrollRun();
        run.setFiscalYearCode(fy.getCode());
        run.setPeriodYear(period.getYear());
        run.setPeriodMonth(period.getMonthValue());
        run.setStatus(PayrollRunStatus.LOCKED);
        return runRepo.save(run);
    }

    private CurrentUser hrActor() {
        return new CurrentUser(4444L, "hr-overlap-tester", Set.of(), Set.of("payroll.read", "payroll.write"));
    }
}

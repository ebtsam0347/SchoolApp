package com.forward.hrms.payroll.integration;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
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
import com.forward.hrms.payroll.dto.request.ApprovalRequest;
import com.forward.hrms.payroll.dto.request.CreateFinalSettlementRequest;
import com.forward.hrms.payroll.dto.response.FinalSettlementDto;
import com.forward.hrms.payroll.dto.response.TerminationLeavesSummaryDto;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.PayrollConfig;
import com.forward.hrms.payroll.entity.RfdAdvance;
import com.forward.hrms.payroll.enums.EmployeeFinalSettlementStatus;
import com.forward.hrms.payroll.enums.ForcedTerminationDeductionPercentage;
import com.forward.hrms.payroll.enums.NoticePayPercentage;
import com.forward.hrms.payroll.enums.SettlementAdjustmentType;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementRepository;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryRepository;
import com.forward.hrms.payroll.repository.PayrollConfigRepository;
import com.forward.hrms.payroll.repository.RfdAdvanceRepository;
import com.forward.hrms.payroll.service.settlements.SettlementFinalizationService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Final settlement deducts the RFD advance rows dated in the
 * last-working-day month
 * ({@link com.forward.hrms.payroll.service.settlements.RfdAdvanceSettlementService}).
 * Nothing is marked on the rows, so a rejected settlement's re-create
 * deducts the same month again.
 */
@SpringBootTest
@WithTestcontainers
class RfdAdvanceSettlementIntegrationTest {

    @Autowired SettlementFinalizationService settlements;
    @Autowired EmployeeFinalSettlementRepository settlementRepo;
    @Autowired EmployeeSalaryRepository salaryRepo;
    @Autowired EmployeePayrollSettingsRepository settingsRepo;
    @Autowired PayrollConfigRepository configRepo;
    @Autowired RfdAdvanceRepository rfdAdvanceRepo;

    @MockitoBean EmployeeClient employeeClient;
    @MockitoBean AttendanceClient attendanceClient;
    @MockitoBean FairPriceShopClient fairPriceShopClient;

    private static final AtomicLong NEXT_EMPLOYEE_ID = new AtomicLong(9_800_000L);

    @BeforeEach
    void cleanAndSeed() {
        settlementRepo.deleteAll();
        rfdAdvanceRepo.deleteAll();
        salaryRepo.deleteAll();
        settingsRepo.deleteAll();
        configRepo.deleteAll();

        PayrollConfig cfg = new PayrollConfig();
        cfg.setEffectiveFrom(LocalDate.now().minusYears(1));
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
    void rowInLastWorkingDayMonth_isDeducted() {
        long employeeId = seedTerminatingEmployee(new BigDecimal("50000.00"));
        seedRfdAdvance(employeeId, new BigDecimal("300.00"), LocalDate.now());

        FinalSettlementDto created = create(employeeId);

        EmployeeFinalSettlement persisted = settlementRepo.findById(created.id()).orElseThrow();
        assertThat(persisted.getRfdAdvance()).isEqualByComparingTo("300.00");
    }

    @Test
    void rowsOutsideLastWorkingDayMonth_areIgnored() {
        long employeeId = seedTerminatingEmployee(new BigDecimal("50000.00"));
        seedRfdAdvance(employeeId, new BigDecimal("300.00"), LocalDate.now().minusMonths(1));
        seedRfdAdvance(employeeId, new BigDecimal("150.00"), LocalDate.now());

        FinalSettlementDto created = create(employeeId);

        EmployeeFinalSettlement persisted = settlementRepo.findById(created.id()).orElseThrow();
        assertThat(persisted.getRfdAdvance()).isEqualByComparingTo("150.00");
    }

    @Test
    void rejectThenCreateAgain_deductsTheSameMonthAgain() {
        long employeeId = seedTerminatingEmployee(new BigDecimal("50000.00"));
        seedRfdAdvance(employeeId, new BigDecimal("300.00"), LocalDate.now());

        FinalSettlementDto first = create(employeeId);
        settlements.reject(first.id(), new ApprovalRequest("wrong last working day"));
        FinalSettlementDto second = create(employeeId);

        assertThat(settlementRepo.findById(second.id()).orElseThrow().getRfdAdvance())
                .isEqualByComparingTo("300.00");
        assertThat(settlementRepo.findById(first.id()).orElseThrow().getStatus())
                .isEqualTo(EmployeeFinalSettlementStatus.REJECTED);
    }

    @Test
    void noRfdAdvance_settlesAtZero() {
        long employeeId = seedTerminatingEmployee(new BigDecimal("50000.00"));

        FinalSettlementDto created = create(employeeId);

        EmployeeFinalSettlement persisted = settlementRepo.findById(created.id()).orElseThrow();
        assertThat(persisted.getRfdAdvance()).isEqualByComparingTo("0.00");
    }

    // ---------------------------------------------------------------- helpers

    private FinalSettlementDto create(long employeeId) {
        return settlements.create(new CreateFinalSettlementRequest(
                employeeId,
                LocalDate.now(),
                SettlementAdjustmentType.NONE,
                NoticePayPercentage.NONE,
                ForcedTerminationDeductionPercentage.PERCENT_0,
                null,
                "termination settlement"
        ));
    }

    private RfdAdvance seedRfdAdvance(long employeeId, BigDecimal amount, LocalDate transactionDate) {
        RfdAdvance rfd = new RfdAdvance();
        rfd.setEmployeeId(employeeId);
        rfd.setAmount(amount);
        rfd.setTransactionState("From Bonus 1");
        rfd.setTransactionDate(transactionDate);
        return rfdAdvanceRepo.save(rfd);
    }

    private long seedTerminatingEmployee(BigDecimal basic) {
        long employeeId = NEXT_EMPLOYEE_ID.getAndIncrement();

        EmployeePayrollSettings settings = new EmployeePayrollSettings();
        settings.setEmployeeId(employeeId);
        settings.setHireDate(LocalDate.now().minusMonths(3));
        settings.setEobiEnrolled(false);
        settingsRepo.save(settings);

        EmployeeSalary salary = new EmployeeSalary();
        salary.setEmployeeId(employeeId);
        salary.setBasic(basic);
        salary.setEffectiveFrom(LocalDate.now().minusMonths(2));
        salaryRepo.save(salary);

        when(employeeClient.terminationInfo(employeeId)).thenReturn(new EmployeeTerminationInfo(
                employeeId, "EMP-" + employeeId, "RFD Test Employee " + employeeId,
                "Dept", "Section", "Designation", "G1", GradeType.LABOUR, LocalDate.now().minusMonths(3)));

        LocalDate lwd = LocalDate.now();
        LocalDate periodStart = lwd.withDayOfMonth(1);
        AttendanceSummaryDto attendance = new AttendanceSummaryDto(
                lwd.getDayOfMonth(), 0, 0, 0, 0, 0, 0, 0);
        DurationDto zeroDuration = new DurationDto(0, "0h 0m");
        NormalOvertimeSummaryDto overtime = new NormalOvertimeSummaryDto(
                zeroDuration, zeroDuration, zeroDuration, zeroDuration);
        when(attendanceClient.terminationAttendanceSummary(employeeId, periodStart, lwd))
                .thenReturn(new TerminationAttendanceSummaryDto(employeeId, periodStart, lwd, attendance, overtime));

        when(attendanceClient.terminationLeavesSummary(employeeId, lwd))
                .thenReturn(new TerminationLeavesSummaryDto(null, null, BigDecimal.ZERO, BigDecimal.ZERO));

        return employeeId;
    }

    private CurrentUser hrActor() {
        return new CurrentUser(4545L, "hr-rfd-tester", Set.of(), Set.of("payroll.read", "payroll.write"));
    }
}

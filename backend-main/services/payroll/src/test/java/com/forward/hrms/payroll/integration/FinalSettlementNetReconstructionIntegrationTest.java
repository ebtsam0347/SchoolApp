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
import com.forward.hrms.payroll.dto.request.CreateFinalSettlementRequest;
import com.forward.hrms.payroll.dto.response.CombinedLeaveSummaryDto;
import com.forward.hrms.payroll.dto.response.FinalSettlementDto;
import com.forward.hrms.payroll.dto.response.TerminationLeaveSummaryDto;
import com.forward.hrms.payroll.dto.response.TerminationLeavesSummaryDto;
import com.forward.hrms.payroll.entity.BonusEvent;
import com.forward.hrms.payroll.entity.BonusEventAmount;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.EmployeeSalaryComponent;
import com.forward.hrms.payroll.entity.PayrollConfig;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.entity.ShortTermAdvanceBatch;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.BonusEventStatus;
import com.forward.hrms.payroll.enums.BonusEventType;
import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.enums.EmployeeFinalSettlementStatus;
import com.forward.hrms.payroll.enums.ForcedTerminationDeductionPercentage;
import com.forward.hrms.payroll.enums.NoticePayPercentage;
import com.forward.hrms.payroll.enums.SettlementAdjustmentType;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;
import com.forward.hrms.payroll.repository.BonusEventAmountRepository;
import com.forward.hrms.payroll.repository.BonusEventRepository;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementRepository;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryComponentRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryRepository;
import com.forward.hrms.payroll.repository.PayrollConfigRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceBatchRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceRepository;
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
 * Independent, hand-derived reconstruction of every component the audit
 * (Turn 7/8) named, seeded in a single settlement, checked against
 * {@link com.forward.hrms.payroll.settlement.engine.SettlementCalculationEngine}'s
 * actual output. Every input below is chosen so all intermediate
 * divisions are exact (no CEILING/HALF_UP rounding activates anywhere in
 * this scenario) — this isolates "is the engine's addition/subtraction
 * topology correct" from "is any one calculator's rounding correct"
 * (rounding is separately, narrowly proven in
 * {@code SettlementSalaryCalculatorPolicyVerificationTest} and
 * {@code GratuityServicePolicyVerificationTest}).
 *
 * <p>Formula under test, read directly from
 * {@code SettlementCalculationEngine.calculate}:
 * <pre>
 * grossSettlement  = grossSalary + gratuity + leaveEncashment + outstandingBonus + noticePayAmount
 * totalDeductions  = incomeTax + otherDeductions + permanentAdvanceTotal + shortTermAdvance
 *                     + leaveRecovery + fairPriceShopAmount + canteenAmount + forcedDeductionAmount
 * netSettlement    = grossSettlement - totalDeductions
 * </pre>
 *
 * <p>Hand-derived expectation for the scenario seeded below. {@code
 * lastWorkingDay} is pinned to a fixed date (2026-04-22, a 30-day month)
 * rather than {@code LocalDate.now()} — once {@code SettlementSalaryCalculator}
 * (Audit Finding 1 fix) prorates earned basic over the REAL elapsed
 * calendar days in the settlement period, a present-day count that isn't
 * relative to "today" would make this test's result depend on which day
 * of the month it happens to run:
 * <pre>
 * basic = 52,000.00
 * present=20, paidLeave=2, weekend=0, holiday=0, elapsed(Apr 1-22)=22 days
 *   -&gt; paidDaysInPeriod = 20+2+0+0 = 22 = elapsed -&gt; zero LOP
 * basicEarnedSalary = 52,000.00 x 22 / 30 (days in April)  =  38,133.34
 * generic DEDUCTION component (2,600.00/26=100.00 x 22 [present+paidLeave]) = 2,200.00  (otherDeductions)
 * gratuity: hire 5y2m before lwd -&gt; 5 completed years
 *   dailyBasic 52,000/26=2,000.00 x 30 x 5           = 300,000.00
 * annual leave: payable=3 (encash), recoverable=1
 *   encashment 2,000.00 x 3 =   6,000.00 (addition)
 *   recovery   2,000.00 x 1 =   2,000.00 (deduction)
 * medical/casual: recoverable=2
 *   recovery   2,000.00 x 2 =   4,000.00 (deduction)
 *   -&gt; leaveRecovery = 2,000.00 + 4,000.00          =   6,000.00
 * outstandingBonus: fullBonus 6,000.00, no off-cycle payments paid  =   6,000.00
 * shortTermAdvance                                                  =   8,000.00 (deduction)
 * fairPriceShopAmount (external stub)                                =   1,500.00 (deduction)
 * canteenAmount: LABOUR, rate 50.00/day x 10 days                    =     500.00 (deduction)
 * noticePayAmount / forcedDeductionAmount: NONE / PERCENT_0          =       0.00
 *
 * grossSettlement = 38,133.34 + 300,000 + 6,000 + 6,000 + 0          = 350,133.34
 * totalDeductions = 0 + 2,200 + 0 + 8,000 + 6,000 + 1,500 + 500 + 0  =  18,200.00
 * netSettlement   = 350,133.34 - 18,200.00                           = 331,933.34
 * </pre>
 */
@SpringBootTest
@WithTestcontainers
class FinalSettlementNetReconstructionIntegrationTest {

    @Autowired SettlementFinalizationService settlements;
    @Autowired EmployeeFinalSettlementRepository settlementRepo;
    @Autowired EmployeeSalaryRepository salaryRepo;
    @Autowired EmployeePayrollSettingsRepository settingsRepo;
    @Autowired PayrollConfigRepository configRepo;
    @Autowired ShortTermAdvanceRepository advanceRepo;
    @Autowired ShortTermAdvanceBatchRepository advanceBatchRepo;
    @Autowired PermanentAdvanceRepository permanentAdvanceRepo;
    @Autowired PermanentAdvanceDeductionRepository permanentAdvanceDeductionRepo;
    @Autowired BonusEventRepository bonusEventRepo;
    @Autowired BonusEventAmountRepository bonusAmountRepo;
    @Autowired SalaryComponentRepository salaryComponentRepo;
    @Autowired EmployeeSalaryComponentRepository employeeSalaryComponentRepo;

    @MockitoBean EmployeeClient employeeClient;
    @MockitoBean AttendanceClient attendanceClient;
    @MockitoBean FairPriceShopClient fairPriceShopClient;

    // Testcontainers reuses one DB across separate test-JVM invocations in
    // this environment (see memory: employee_tests_hit_host_postgres.md /
    // Turn 6 findings) — seeding from a fixed literal collided with
    // leftover rows from earlier runs. Seed from nanoTime instead, and
    // (below) never blanket-delete the shared salary_components catalog:
    // other already-persisted rows there are referenced by other tests'
    // payslip_lines and deleting them fails on the FK.
    private static final AtomicLong NEXT_EMPLOYEE_ID =
            new AtomicLong(9_000_000L + (System.nanoTime() & 0x00FFFFFFL));

    @BeforeEach
    void cleanAndSeed() {
        settlementRepo.deleteAll();
        permanentAdvanceDeductionRepo.deleteAll();
        permanentAdvanceRepo.deleteAll();
        advanceRepo.deleteAll();
        advanceBatchRepo.deleteAll();
        bonusAmountRepo.deleteAll();
        bonusEventRepo.deleteAll();
        salaryRepo.deleteAll();
        settingsRepo.deleteAll();
        configRepo.deleteAll();

        PayrollConfig cfg = new PayrollConfig();
        cfg.setEffectiveFrom(LocalDate.of(2026, 4, 22).minusYears(1));
        cfg.setEobiAmount(BigDecimal.ZERO);
        cfg.setPessiRatePercent(BigDecimal.ZERO);
        cfg.setPessiSalaryCap(BigDecimal.ZERO);
        cfg.setCanteenPerDayRateManagement(BigDecimal.ZERO);
        cfg.setCanteenPerDayRateOther(new BigDecimal("50.00"));
        configRepo.save(cfg);

        CurrentUserHolder.set(hrActor());
    }

    @AfterEach
    void clearCaller() {
        CurrentUserHolder.clear();
    }

    @Test
    void fullSettlement_everyComponentSeeded_matchesIndependentHandDerivation() {
        // Fixed, not LocalDate.now() — see class javadoc.
        LocalDate lwd = LocalDate.of(2026, 4, 22);
        long employeeId = NEXT_EMPLOYEE_ID.getAndIncrement();

        // --- salary + hire date (drives both earned salary and gratuity) ---
        EmployeePayrollSettings settings = new EmployeePayrollSettings();
        settings.setEmployeeId(employeeId);
        settings.setHireDate(lwd.minusYears(5).minusMonths(2));
        settings.setEobiEnrolled(false);
        settingsRepo.save(settings);

        EmployeeSalary salary = new EmployeeSalary();
        salary.setEmployeeId(employeeId);
        salary.setBasic(new BigDecimal("52000.00"));
        salary.setEffectiveFrom(lwd.minusYears(6));
        salaryRepo.save(salary);

        when(employeeClient.terminationInfo(employeeId)).thenReturn(new EmployeeTerminationInfo(
                employeeId, "EMP-" + employeeId, "Reconstruction Test Employee",
                "Dept", "Section", "Designation", "G1", GradeType.LABOUR, settings.getHireDate()));

        // --- generic DEDUCTION salary component (Turn 8 "generic deduction") ---
        SalaryComponent deductionComponent = new SalaryComponent();
        // Unique per run — salary_components.code is globally unique and
        // this catalog is never wiped between test runs (see the
        // NEXT_EMPLOYEE_ID comment above).
        deductionComponent.setCode("UD_" + employeeId);
        deductionComponent.setName("Union Dues");
        deductionComponent.setType(ComponentType.DEDUCTION);
        deductionComponent.setCalcType(CalcType.FLAT);
        deductionComponent.setDisplayOrder(1);
        deductionComponent.setActive(true);
        salaryComponentRepo.save(deductionComponent);

        EmployeeSalaryComponent employeeDeduction = new EmployeeSalaryComponent();
        employeeDeduction.setEmployeeId(employeeId);
        employeeDeduction.setComponentId(deductionComponent.getId());
        employeeDeduction.setAmount(new BigDecimal("2600.00"));
        employeeDeduction.setEffectiveFrom(lwd.minusYears(6));
        employeeSalaryComponentRepo.save(employeeDeduction);

        // --- attendance: present 20 + paidLeave 2 = 22 payable days ---
        LocalDate periodStart = lwd.withDayOfMonth(1);
        AttendanceSummaryDto attendance = new AttendanceSummaryDto(20, 0, 0, 2, 0, 0, 0, 0);
        DurationDto zeroDuration = new DurationDto(0, "0h 0m");
        NormalOvertimeSummaryDto overtime = new NormalOvertimeSummaryDto(
                zeroDuration, zeroDuration, zeroDuration, zeroDuration);
        when(attendanceClient.terminationAttendanceSummary(employeeId, periodStart, lwd))
                .thenReturn(new TerminationAttendanceSummaryDto(employeeId, periodStart, lwd, attendance, overtime));

        // --- leave: annual payable=3 recoverable=1 (encashable); medical/casual recoverable=2 ---
        TerminationLeaveSummaryDto annualLeave = new TerminationLeaveSummaryDto(
                1L, "ANNUAL", "Annual Leave", true,
                null, null,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("3"), new BigDecimal("1"),
                BigDecimal.ZERO, BigDecimal.ZERO);
        CombinedLeaveSummaryDto medicalCasual = new CombinedLeaveSummaryDto(
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("2"),
                BigDecimal.ZERO, BigDecimal.ZERO);
        when(attendanceClient.terminationLeavesSummary(employeeId, lwd))
                .thenReturn(new TerminationLeavesSummaryDto(annualLeave, medicalCasual, BigDecimal.ZERO, BigDecimal.ZERO));

        // --- canteen: LABOUR grade, 10 usage days at 50.00/day ---
        when(attendanceClient.canteenUsage(employeeId, periodStart, lwd, null))
                .thenReturn(new AttendanceClient.CanteenUsage(10, 0));

        // --- Fair Price Shop: external stub, 1,500.00 outstanding.
        // SettlementFairPriceShopCalculator passes context.getEmployeeCode()
        // through verbatim, which SettlementFinalizationService populates
        // from preview.employee().employeeCode() — the "EMP-<id>" value
        // stubbed on employeeClient.terminationInfo() above.
        when(fairPriceShopClient.outstandingAmount("EMP-" + employeeId, lwd))
                .thenReturn(new BigDecimal("1500.00"));

        // --- short-term advance: 8,000.00 ---
        ShortTermAdvanceBatch batch = new ShortTermAdvanceBatch();
        batch.setRequestCode("STB-RECON-" + employeeId);
        batch.setTargetYear(lwd.getYear());
        batch.setTargetMonth(lwd.getMonthValue());
        batch.setTotalAmount(new BigDecimal("8000.00"));
        batch.setTotalEmployees(1);
        batch.setStatus(AdvanceStatus.APPROVED);
        advanceBatchRepo.save(batch);

        ShortTermAdvance advance = new ShortTermAdvance();
        advance.setBatchId(batch.getId());
        advance.setEmployeeId(employeeId);
        advance.setAmount(new BigDecimal("8000.00"));
        advance.setTargetYear(lwd.getYear());
        advance.setTargetMonth(lwd.getMonthValue());
        advance.setStatus(ShortTermAdvanceStatus.APPROVED);
        advanceRepo.save(advance);

        // --- bonus: fullBonus 6,000.00, nothing paid via normal installments ---
        BonusEvent event = new BonusEvent();
        event.setFiscalYearCode("2025-26");
        event.setName("Reconstruction Test Bonus");
        event.setEventType(BonusEventType.EID_UL_FITR);
        event.setReferenceDate(lwd.minusDays(1));
        event.setStatus(BonusEventStatus.ACTIVE);
        bonusEventRepo.save(event);

        BonusEventAmount bonusAmount = new BonusEventAmount();
        bonusAmount.setBonusEventId(event.getId());
        bonusAmount.setEmployeeId(employeeId);
        bonusAmount.setHireDate(settings.getHireDate());
        bonusAmount.setServiceDays(1800);
        bonusAmount.setBasicSnapshot(new BigDecimal("6000.00"));
        bonusAmount.setFullBonus(new BigDecimal("6000.00"));
        bonusAmountRepo.save(bonusAmount);

        // --- create the settlement (NONE adjustment, no notice pay, no forced deduction) ---
        FinalSettlementDto created = settlements.create(new CreateFinalSettlementRequest(
                employeeId,
                lwd,
                SettlementAdjustmentType.NONE,
                NoticePayPercentage.NONE,
                ForcedTerminationDeductionPercentage.PERCENT_0,
                null,
                "net settlement reconstruction"
        ));

        assertThat(created.status()).isEqualTo(EmployeeFinalSettlementStatus.DRAFT);

        EmployeeFinalSettlement persisted = settlementRepo.findById(created.id()).orElseThrow();

        // ---- component-level assertions (independently hand-derived) ----
        assertThat(persisted.getEarnedBasicSalary()).isEqualByComparingTo("38133.34");
        assertThat(persisted.getGrossSalary()).isEqualByComparingTo("38133.34");
        assertThat(persisted.getGratuityEligible()).isTrue();
        assertThat(persisted.getGratuityCompletedYears()).isEqualTo(5);
        assertThat(persisted.getGratuityAmount()).isEqualByComparingTo("300000.00");
        assertThat(persisted.getAnnualExcessiveLeaveDays()).isEqualByComparingTo("1");
        assertThat(persisted.getAnnualExcessiveLeaveAmount()).isEqualByComparingTo("2000.00");
        assertThat(persisted.getMedicalCasualExcessiveLeaveDays()).isEqualByComparingTo("2");
        assertThat(persisted.getMedicalCasualExcessiveLeaveAmount()).isEqualByComparingTo("4000.00");
        assertThat(persisted.getOutstandingBonus()).isEqualByComparingTo("6000.00");
        assertThat(persisted.getShortTermAdvance()).isEqualByComparingTo("8000.00");
        assertThat(persisted.getPermanentAdvance()).isEqualByComparingTo("0.00");
        assertThat(persisted.getFairPriceShopAmount()).isEqualByComparingTo("1500.00");
        assertThat(persisted.getCanteenAmount()).isEqualByComparingTo("500.00");
        assertThat(persisted.getNoticePayAmount()).isEqualByComparingTo("0.00");
        assertThat(persisted.getForcedDeductionAmount()).isEqualByComparingTo("0.00");

        // ---- top-level reconstruction: this is the actual audit target ----
        assertThat(persisted.getGrossSettlement()).isEqualByComparingTo("350133.34");
        assertThat(persisted.getTotalDeductions()).isEqualByComparingTo("18200.00");
        assertThat(persisted.getNetSettlement()).isEqualByComparingTo("331933.34");

        // netSettlement must always equal grossSettlement - totalDeductions,
        // independent of the hand-derivation above — catches any future
        // change to the engine that updates one component without updating
        // the aggregate arithmetic.
        assertThat(persisted.getNetSettlement())
                .isEqualByComparingTo(persisted.getGrossSettlement().subtract(persisted.getTotalDeductions()));

        // The "excessive leave" split (annual + medical/casual) must sum to
        // exactly the leaveRecovery baked into totalDeductions above — this
        // is the invariant SettlementCalculationResult's own javadoc
        // asserts ("these just carry the split breakdown") rather than a
        // second, independently-summed deduction.
        BigDecimal leaveRecoverySum = persisted.getAnnualExcessiveLeaveAmount()
                .add(persisted.getMedicalCasualExcessiveLeaveAmount());
        assertThat(leaveRecoverySum).isEqualByComparingTo("6000.00");
    }

    private CurrentUser hrActor() {
        return new CurrentUser(4343L, "hr-recon-tester", Set.of(), Set.of("payroll.read", "payroll.write"));
    }
}

package com.forward.hrms.payroll.integration;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.kafka.outbox.OutboxEntry;
import com.forward.hrms.commons.kafka.outbox.OutboxRepository;
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
import com.forward.hrms.payroll.dto.request.CancelSettlementRequest;
import com.forward.hrms.payroll.dto.request.CreateFinalSettlementRequest;
import com.forward.hrms.payroll.dto.request.SettlementPaymentRequest;
import com.forward.hrms.payroll.dto.response.FinalSettlementDto;
import com.forward.hrms.payroll.dto.response.TerminationLeaveSummaryDto;
import com.forward.hrms.payroll.dto.response.TerminationLeavesSummaryDto;
import com.forward.hrms.payroll.entity.BonusEvent;
import com.forward.hrms.payroll.entity.BonusEventAmount;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.PayrollConfig;
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.entity.ShortTermAdvanceBatch;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.BonusEventStatus;
import com.forward.hrms.payroll.enums.BonusEventType;
import com.forward.hrms.payroll.enums.BonusPaymentStatus;
import com.forward.hrms.payroll.enums.EmployeeFinalSettlementStatus;
import com.forward.hrms.payroll.enums.ForcedTerminationDeductionPercentage;
import com.forward.hrms.payroll.enums.NoticePayPercentage;
import com.forward.hrms.payroll.enums.SettlementAdjustmentType;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;
import com.forward.hrms.payroll.events.PayrollEvents;
import com.forward.hrms.payroll.repository.BonusEventAmountRepository;
import com.forward.hrms.payroll.repository.BonusEventRepository;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementRepository;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryRepository;
import com.forward.hrms.payroll.repository.LeaveEncashmentRepository;
import com.forward.hrms.payroll.repository.PayrollConfigRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Full final-settlement lifecycle against a real Postgres (via
 * Testcontainers). employee-service and attendance-service are mocked at
 * their client boundary — the cross-service termination handoff itself
 * is covered separately in employee-service's own test suite
 * (ExitHistoricalLifecycleIntegrationTest), matching this codebase's
 * existing convention of testing each service's own transactional
 * behavior against a real DB and mocking the other services it calls
 * over HTTP.
 *
 * <p>Covers the audited defects: CANCEL not reversing (Issue 1), and the
 * REJECT/CANCEL → recreate financial double-count risk (Issue 3) — proved
 * here via real advance/bonus/leave-encashment row assertions, not just
 * settlement status.
 *
 * <p>Uses short-term advances (not permanent advances) to exercise the
 * "advance reopens on reversal" assertions. A separate, pre-existing bug
 * was found while writing this test: {@code PermanentAdvanceSettlementServiceNew
 * .process()} never sets {@code PermanentAdvanceDeduction.employeeId},
 * which the schema (V0047) has required NOT NULL since it was added —
 * so final-settlement processing of a permanent advance fails outright
 * with a constraint violation today, independent of anything in this
 * audit. Not fixed here (out of scope; reported separately) — short-term
 * advances exercise the same "closed at creation, reopened on reversal,
 * reconsidered exactly once on recreate" behavior without hitting it.
 */
@SpringBootTest
@WithTestcontainers
class FinalSettlementLifecycleIntegrationTest {

    @Autowired SettlementFinalizationService settlements;
    @Autowired EmployeeFinalSettlementRepository settlementRepo;
    @Autowired EmployeeSalaryRepository salaryRepo;
    @Autowired EmployeePayrollSettingsRepository settingsRepo;
    @Autowired PayrollConfigRepository configRepo;
    @Autowired ShortTermAdvanceRepository advanceRepo;
    @Autowired ShortTermAdvanceBatchRepository advanceBatchRepo;
    // Not used by these tests (see class javadoc — permanent advances hit
    // a separate, pre-existing bug), but cleaned up defensively: Testcontainers
    // reuses the DB across runs, and a leftover PermanentAdvance row from
    // an earlier iteration of this same low employeeId range would
    // otherwise leak into SettlementAdvanceCalculator's query for a
    // completely unrelated test's employee.
    @Autowired PermanentAdvanceRepository permanentAdvanceRepo;
    @Autowired PermanentAdvanceDeductionRepository permanentAdvanceDeductionRepo;
    @Autowired BonusEventRepository bonusEventRepo;
    @Autowired BonusEventAmountRepository bonusAmountRepo;
    @Autowired LeaveEncashmentRepository leaveEncashmentRepo;
    @Autowired OutboxRepository outboxRepo;

    @MockitoBean EmployeeClient employeeClient;
    @MockitoBean AttendanceClient attendanceClient;
    @MockitoBean FairPriceShopClient fairPriceShopClient;

    private static final AtomicLong NEXT_EMPLOYEE_ID = new AtomicLong(9_000_000L);
    private static final AtomicLong NEXT_ADVANCE_SEQ = new AtomicLong(1L);

    @BeforeEach
    void cleanAndSeed() {
        settlementRepo.deleteAll();
        permanentAdvanceDeductionRepo.deleteAll();
        permanentAdvanceRepo.deleteAll();
        advanceRepo.deleteAll();
        advanceBatchRepo.deleteAll();
        bonusAmountRepo.deleteAll();
        bonusEventRepo.deleteAll();
        leaveEncashmentRepo.deleteAll();
        salaryRepo.deleteAll();
        settingsRepo.deleteAll();
        configRepo.deleteAll();

        PayrollConfig cfg = new PayrollConfig();
        cfg.setEffectiveFrom(LocalDate.now().minusYears(1));
        cfg.setEobiAmount(BigDecimal.ZERO);
        cfg.setPessiRatePercent(BigDecimal.ZERO);
        cfg.setPessiSalaryCap(BigDecimal.ZERO);
        // Canteen off — SettlementCanteenCalculator short-circuits before
        // calling attendanceClient.canteenUsage(), which is deliberately
        // left unstubbed below.
        cfg.setCanteenPerDayRateManagement(BigDecimal.ZERO);
        cfg.setCanteenPerDayRateOther(BigDecimal.ZERO);
        configRepo.save(cfg);

        CurrentUserHolder.set(hrActor());
    }

    @AfterEach
    void clearCaller() {
        CurrentUserHolder.clear();
    }

    // ------------------------------------------------------------ A

    @Test
    void createApprovePay_happyPath_leavesEverythingClosedAndPaid() {
        long employeeId = seedTerminatingEmployee(new BigDecimal("50000.00"));
        seedShortTermAdvance(employeeId, new BigDecimal("10000.00"));
        long bonusEventId = seedBonusEvent(employeeId, new BigDecimal("5000.00"));

        FinalSettlementDto created = create(employeeId);
        assertThat(created.status()).isEqualTo(EmployeeFinalSettlementStatus.DRAFT);

        settlements.hrApproval(created.id(), new ApprovalRequest("looks correct"));
        var afterApproval = settlementRepo.findById(created.id()).orElseThrow();
        assertThat(afterApproval.getStatus()).isEqualTo(EmployeeFinalSettlementStatus.HR_APPROVED);

        settlements.markPaid(created.id(), new SettlementPaymentRequest(
                LocalDate.now(), "BANK", "REF-1", "paid"));

        var afterPaid = settlementRepo.findById(created.id()).orElseThrow();
        assertThat(afterPaid.getStatus()).isEqualTo(EmployeeFinalSettlementStatus.PAID);

        // Financial closing happened at CREATE and must still stand.
        var advance = advanceForEmployee(employeeId);
        assertThat(advance.getStatus()).isEqualTo(ShortTermAdvanceStatus.APPROVED);
        assertThat(advance.getFinalSettlementId()).isEqualTo(created.id());

        var bonusAmount = bonusAmountRepo.findAllByBonusEventId(bonusEventId).stream().findFirst().orElseThrow();
        assertThat(bonusAmount.getPaymentStatus()).isEqualTo(BonusPaymentStatus.PAID);

        assertThat(hasOutboxEvent(PayrollEvents.FINAL_SETTLEMENT_CREATED)).isTrue();
        assertThat(hasOutboxEvent(PayrollEvents.FINAL_SETTLEMENT_PAID)).isTrue();
    }

    // ------------------------------------------------------------ B

    @Test
    void createThenReject_reversesAdvanceAndBonusCompletely() {
        long employeeId = seedTerminatingEmployee(new BigDecimal("50000.00"));
        seedShortTermAdvance(employeeId, new BigDecimal("10000.00"));
        long bonusEventId = seedBonusEvent(employeeId, new BigDecimal("5000.00"));

        FinalSettlementDto created = create(employeeId);

        // Sanity: closing really happened before reversing it.
        assertThat(advanceForEmployee(employeeId).getFinalSettlementId()).isEqualTo(created.id());

        settlements.reject(created.id(), new ApprovalRequest("wrong employee"));

        var rejected = settlementRepo.findById(created.id()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(EmployeeFinalSettlementStatus.REJECTED);

        var advance = advanceForEmployee(employeeId);
        assertThat(advance.getStatus()).isEqualTo(ShortTermAdvanceStatus.APPROVED);
        assertThat(advance.getFinalSettlementId()).isNull();

        var bonusAmount = bonusAmountRepo.findAllByBonusEventId(bonusEventId).stream().findFirst().orElseThrow();
        assertThat(bonusAmount.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bonusAmount.getPaymentStatus()).isEqualTo(BonusPaymentStatus.PENDING);

        assertThat(leaveEncashmentRepo.existsByFinalSettlementId(created.id())).isFalse();
        assertThat(hasOutboxEvent(PayrollEvents.FINAL_SETTLEMENT_REJECTED)).isTrue();
    }

    // ------------------------------------------------------------ C

    @Test
    void createThenCancelFromDraft_reversesCompletely() {
        long employeeId = seedTerminatingEmployee(new BigDecimal("50000.00"));
        seedShortTermAdvance(employeeId, new BigDecimal("10000.00"));
        long bonusEventId = seedBonusEvent(employeeId, new BigDecimal("5000.00"));

        FinalSettlementDto created = create(employeeId);

        settlements.cancel(created.id(), new CancelSettlementRequest("data entry mistake"));

        var cancelled = settlementRepo.findById(created.id()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(EmployeeFinalSettlementStatus.CANCELLED);

        var advance = advanceForEmployee(employeeId);
        assertThat(advance.getStatus()).isEqualTo(ShortTermAdvanceStatus.APPROVED);
        assertThat(advance.getFinalSettlementId()).isNull();

        var bonusAmount = bonusAmountRepo.findAllByBonusEventId(bonusEventId).stream().findFirst().orElseThrow();
        assertThat(bonusAmount.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bonusAmount.getPaymentStatus()).isEqualTo(BonusPaymentStatus.PENDING);

        assertThat(leaveEncashmentRepo.existsByFinalSettlementId(created.id())).isFalse();
        assertThat(hasOutboxEvent(PayrollEvents.FINAL_SETTLEMENT_CANCELLED)).isTrue();
    }

    // ------------------------------------------------------------ D

    @Test
    void createApproveThenCancelFromHrApproved_reversesCompletely() {
        long employeeId = seedTerminatingEmployee(new BigDecimal("50000.00"));
        seedShortTermAdvance(employeeId, new BigDecimal("10000.00"));

        FinalSettlementDto created = create(employeeId);
        settlements.hrApproval(created.id(), new ApprovalRequest("ok"));

        settlements.cancel(created.id(), new CancelSettlementRequest("employee withdrew resignation"));

        var cancelled = settlementRepo.findById(created.id()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(EmployeeFinalSettlementStatus.CANCELLED);

        assertThat(advanceForEmployee(employeeId).getStatus()).isEqualTo(ShortTermAdvanceStatus.APPROVED);
    }

    // ------------------------------------------------------------ E / G / H / I

    @Test
    void rejectThenCreateAgainThenPay_noDoubleAdvanceBonusOrLeave() {
        long employeeId = seedTerminatingEmployee(new BigDecimal("60000.00"));
        seedShortTermAdvance(employeeId, new BigDecimal("12000.00"));
        long bonusEventId = seedBonusEvent(employeeId, new BigDecimal("6000.00"));
        seedAnnualLeavePayable(new BigDecimal("2"));

        FinalSettlementDto first = create(employeeId);
        settlements.reject(first.id(), new ApprovalRequest("wrong last working day"));

        FinalSettlementDto second = create(employeeId);
        assertThat(second.id()).isNotEqualTo(first.id());

        settlements.hrApproval(second.id(), new ApprovalRequest("correct now"));
        settlements.markPaid(second.id(), new SettlementPaymentRequest(
                LocalDate.now(), "BANK", "REF-2", "paid"));

        var paid = settlementRepo.findById(second.id()).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(EmployeeFinalSettlementStatus.PAID);

        // I — advance recovered exactly once, by the second settlement.
        var advance = advanceForEmployee(employeeId);
        assertThat(advance.getStatus()).isEqualTo(ShortTermAdvanceStatus.APPROVED);
        assertThat(advance.getFinalSettlementId()).isEqualTo(second.id());

        // G — bonus paid exactly once (not doubled across the two settlements).
        var bonusAmount = bonusAmountRepo.findAllByBonusEventId(bonusEventId).stream().findFirst().orElseThrow();
        assertThat(bonusAmount.getPaidAmount()).isEqualByComparingTo("6000.00");
        assertThat(bonusAmount.getPaymentStatus()).isEqualTo(BonusPaymentStatus.PAID);

        // H — leave encashed exactly once, under the second settlement.
        assertThat(leaveEncashmentRepo.existsByFinalSettlementId(first.id())).isFalse();
        assertThat(leaveEncashmentRepo.existsByFinalSettlementId(second.id())).isTrue();
        assertThat(leaveEncashmentRepo.findAll())
                .filteredOn(e -> employeeId == e.getEmployeeId())
                .hasSize(1);
    }

    // ------------------------------------------------------------ F / G / H / I

    @Test
    void cancelThenCreateAgainThenPay_noDoubleAdvanceBonusOrLeave() {
        long employeeId = seedTerminatingEmployee(new BigDecimal("60000.00"));
        seedShortTermAdvance(employeeId, new BigDecimal("12000.00"));
        long bonusEventId = seedBonusEvent(employeeId, new BigDecimal("6000.00"));
        seedAnnualLeavePayable(new BigDecimal("3"));

        FinalSettlementDto first = create(employeeId);
        settlements.cancel(first.id(), new CancelSettlementRequest("HR data entry mistake"));

        FinalSettlementDto second = create(employeeId);
        settlements.hrApproval(second.id(), new ApprovalRequest("ok"));
        settlements.markPaid(second.id(), new SettlementPaymentRequest(
                LocalDate.now(), "BANK", "REF-3", "paid"));

        var paid = settlementRepo.findById(second.id()).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(EmployeeFinalSettlementStatus.PAID);

        var advance = advanceForEmployee(employeeId);
        assertThat(advance.getStatus()).isEqualTo(ShortTermAdvanceStatus.APPROVED);
        assertThat(advance.getFinalSettlementId()).isEqualTo(second.id());

        var bonusAmount = bonusAmountRepo.findAllByBonusEventId(bonusEventId).stream().findFirst().orElseThrow();
        assertThat(bonusAmount.getPaidAmount()).isEqualByComparingTo("6000.00");
        assertThat(bonusAmount.getPaymentStatus()).isEqualTo(BonusPaymentStatus.PAID);

        assertThat(leaveEncashmentRepo.findAll())
                .filteredOn(e -> employeeId == e.getEmployeeId())
                .hasSize(1);
    }

    // ------------------------------------------------------------ helpers

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

    private ShortTermAdvance advanceForEmployee(long employeeId) {
        return advanceRepo.findAll().stream()
                .filter(a -> a.getEmployeeId().equals(employeeId))
                .findFirst().orElseThrow();
    }

    private long seedTerminatingEmployee(BigDecimal basic) {
        long employeeId = NEXT_EMPLOYEE_ID.getAndIncrement();

        EmployeePayrollSettings settings = new EmployeePayrollSettings();
        settings.setEmployeeId(employeeId);
        // Under the 6-month default gratuityMinServiceMonths — keeps
        // gratuity at zero so it doesn't complicate these assertions.
        settings.setHireDate(LocalDate.now().minusMonths(3));
        settings.setEobiEnrolled(false);
        settingsRepo.save(settings);

        EmployeeSalary salary = new EmployeeSalary();
        salary.setEmployeeId(employeeId);
        salary.setBasic(basic);
        salary.setEffectiveFrom(LocalDate.now().minusMonths(2));
        salaryRepo.save(salary);

        when(employeeClient.terminationInfo(employeeId)).thenReturn(new EmployeeTerminationInfo(
                employeeId, "EMP-" + employeeId, "Test Employee " + employeeId,
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

        // No annual leave payable by default — overridden per-test via
        // seedAnnualLeavePayable() where a test needs a non-zero encashment.
        when(attendanceClient.terminationLeavesSummary(employeeId, lwd))
                .thenReturn(new TerminationLeavesSummaryDto(null, null, BigDecimal.ZERO, BigDecimal.ZERO));

        return employeeId;
    }

    /**
     * Overrides every employee's leave summary (matched by argument
     * matcher, so it applies regardless of employeeId) to carry a payable
     * annual-leave balance, so LeaveSettlementService.process() has
     * something real to encash. Must be called after
     * seedTerminatingEmployee() for the same test.
     */
    private void seedAnnualLeavePayable(BigDecimal payableDays) {
        when(attendanceClient.terminationLeavesSummary(anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> new TerminationLeavesSummaryDto(
                        new TerminationLeaveSummaryDto(
                                1L, "ANNUAL", "Annual Leave", true,
                                null, null,
                                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                                payableDays, BigDecimal.ZERO,
                                BigDecimal.ZERO, BigDecimal.ZERO),
                        null,
                        BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private void seedShortTermAdvance(long employeeId, BigDecimal amount) {
        LocalDate lwd = LocalDate.now();

        ShortTermAdvanceBatch batch = new ShortTermAdvanceBatch();
        batch.setRequestCode("STB-TEST-" + NEXT_ADVANCE_SEQ.getAndIncrement());
        batch.setTargetYear(lwd.getYear());
        batch.setTargetMonth(lwd.getMonthValue());
        batch.setTotalAmount(amount);
        batch.setTotalEmployees(1);
        batch.setStatus(AdvanceStatus.APPROVED);
        advanceBatchRepo.save(batch);

        ShortTermAdvance advance = new ShortTermAdvance();
        advance.setBatchId(batch.getId());
        advance.setEmployeeId(employeeId);
        advance.setAmount(amount);
        advance.setTargetYear(lwd.getYear());
        advance.setTargetMonth(lwd.getMonthValue());
        advance.setStatus(ShortTermAdvanceStatus.APPROVED);
        advanceRepo.save(advance);
    }

    private long seedBonusEvent(long employeeId, BigDecimal fullBonus) {
        BonusEvent event = new BonusEvent();
        event.setFiscalYearCode("2025-26");
        event.setName("Test Eid Bonus");
        event.setEventType(BonusEventType.EID_UL_FITR);
        event.setReferenceDate(LocalDate.now().minusDays(1));
        event.setStatus(BonusEventStatus.ACTIVE);
        bonusEventRepo.save(event);

        BonusEventAmount amount = new BonusEventAmount();
        amount.setBonusEventId(event.getId());
        amount.setEmployeeId(employeeId);
        amount.setHireDate(LocalDate.now().minusMonths(3));
        amount.setServiceDays(90);
        amount.setBasicSnapshot(fullBonus);
        amount.setFullBonus(fullBonus);
        bonusAmountRepo.save(amount);

        return event.getId();
    }

    private boolean hasOutboxEvent(String eventType) {
        return outboxRepo.findAll().stream()
                .map(OutboxEntry::getPayload)
                .anyMatch(payload -> payload.contains(eventType));
    }

    private CurrentUser hrActor() {
        return new CurrentUser(4242L, "hr-tester", Set.of(), Set.of("payroll.read", "payroll.write"));
    }
}

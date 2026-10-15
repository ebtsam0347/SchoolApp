package com.forward.hrms.payroll.integration;

import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.client.FairPriceShopClient;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.EmployeeSalaryComponent;
import com.forward.hrms.payroll.entity.FiscalYear;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.entity.Bank;
import com.forward.hrms.payroll.entity.EmployeeBankDetails;
import com.forward.hrms.payroll.entity.PayrollRunSkip;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.entity.PayrollConfig;
import com.forward.hrms.payroll.enums.PaymentMode;
import com.forward.hrms.payroll.enums.PayrollRunStatus;
import com.forward.hrms.payroll.enums.PayslipStatus;
import com.forward.hrms.payroll.repository.AllowanceTransactionRepository;
import com.forward.hrms.payroll.repository.BankRepository;
import com.forward.hrms.payroll.repository.EmployeeBankDetailsRepository;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryComponentRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryRepository;
import com.forward.hrms.payroll.repository.FiscalYearRepository;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.repository.PayrollRunSkipRepository;
import com.forward.hrms.payroll.repository.PayslipLineRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;
import com.forward.hrms.payroll.repository.PayrollConfigRepository;
import com.forward.hrms.payroll.service.AttendanceSnapshot;
import com.forward.hrms.payroll.service.PayrollComputeService;
import com.forward.hrms.payroll.service.PayrollRunService;
import com.forward.hrms.payroll.service.PayrollRunUndoService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * End-to-end payroll compute against a real Postgres + Kafka + Redis + MinIO
 * (via Testcontainers). Validates the doctrine-level money rules:
 * EOBI on/off, PESSI cap edge, LOP CEILING. Other downstreams
 * (employee, attendance) are mocked — the contract there is verified
 * elsewhere.
 *
 * <p>Money is rupees, {@code numeric(15,2)}. Sub-paisa intermediates
 * round CEILING per {@link com.forward.hrms.commons.core.money.Money}.
 */
@SpringBootTest
@WithTestcontainers
class PayrollComputeIntegrationTest {

    @Autowired PayrollComputeService computeService;
    @Autowired PayrollRunRepository runRepo;
    @Autowired EmployeeSalaryRepository salaryRepo;
    @Autowired EmployeeSalaryComponentRepository componentLineRepo;
    @Autowired AllowanceTransactionRepository allowanceTransactionRepo;
    @Autowired EmployeePayrollSettingsRepository settingsRepo;
    @Autowired SalaryComponentRepository catalogRepo;
    @Autowired PayrollConfigRepository statRepo;
    @Autowired FiscalYearRepository fyRepo;
    @Autowired PayslipRepository payslipRepo;
    @Autowired PayslipLineRepository lineRepo;
    @Autowired PayrollRunSkipRepository skipRepo;
    @Autowired EmployeeBankDetailsRepository bankRepo;
    @Autowired BankRepository banksRepo;
    @Autowired PayrollRunService runService;
    @Autowired PayrollRunUndoService undoService;
    @Autowired com.forward.hrms.payroll.repository.PermanentAdvanceRepository permanentAdvanceRepo;
    @Autowired com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository permanentAdvanceDeductionRepo;
    @Autowired com.forward.hrms.payroll.repository.PermanentAdvanceWaiverRepository permanentAdvanceWaiverRepo;
    @Autowired com.forward.hrms.payroll.repository.RfdAdvanceRepository rfdAdvanceRepo;
    @Autowired com.forward.hrms.commons.saga.SagaService sagaService;
    @Autowired com.forward.hrms.commons.saga.SagaRepository sagaRepo;
    @Autowired PlatformTransactionManager txManager;

    @MockitoBean EmployeeClient employeeClient;
    @MockitoBean AttendanceClient attendanceClient;
    @MockitoBean FairPriceShopClient fairPriceShopClient;

    private final AtomicLong nextEmployeeId = new AtomicLong(1L);
    private YearMonth period;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    @BeforeEach
    void cleanAndSeed() {
        // Every test creates a PAYROLL_RUN_COMPUTE saga per run (via
        // runService.compute()/computeRun()). Left running, the background
        // SagaRunner poller keeps retrying it against a payroll_runs row
        // that runRepo.deleteAll() below is about to remove — "payroll run
        // X disappeared mid-saga" / UnexpectedRollbackException on every
        // poll tick until it exhausts its retry budget. Clear saga state
        // first so nothing outlives the run it points at.
        sagaRepo.deleteAll();
        lineRepo.deleteAll();
        payslipRepo.deleteAll();
        skipRepo.deleteAll();
        bankRepo.deleteAll();
        runRepo.deleteAll();
        // allowance_transaction FKs to employee_salary_components — must go
        // first, or componentLineRepo.deleteAll() below violates the FK on
        // any row left over from allowance-run tests.
        allowanceTransactionRepo.deleteAll();
        componentLineRepo.deleteAll();
        salaryRepo.deleteAll();
        settingsRepo.deleteAll();
        statRepo.deleteAll();

        period = YearMonth.now();
        periodStart = period.atDay(1);
        periodEnd = period.atEndOfMonth();

        PayrollConfig stat = new PayrollConfig();
        stat.setEffectiveFrom(periodStart.minusYears(1));
        stat.setEobiAmount(new BigDecimal("1500.00"));
        stat.setPessiRatePercent(new BigDecimal("6.00"));
        stat.setPessiSalaryCap(new BigDecimal("25000.00"));
        // Zero, not a real rate — canteen deduction isn't in scope for these
        // tests, and computeCanteen() short-circuits on rate <= 0 before
        // ever calling attendanceClient.canteenUsage(), which mockAttendance()
        // below doesn't stub (only NOT NULL at the DB level, business-wise 0 = off).
        stat.setCanteenPerDayRateManagement(BigDecimal.ZERO);
        stat.setCanteenPerDayRateOther(BigDecimal.ZERO);
        statRepo.save(stat);

        when(employeeClient.getMany(any(), any())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            Map<Long, EmployeeClient.EmployeeBrief> out = new LinkedHashMap<>();
            for (Long id : ids) {
                out.put(id, new EmployeeClient.EmployeeBrief(id, "EMP-" + id,
                        "Test Employee " + id, null, null, null, null, null, null, null, null));
            }
            return out;
        });
        when(employeeClient.getMany(any())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            Map<Long, EmployeeClient.EmployeeBrief> out = new LinkedHashMap<>();
            for (Long id : ids) {
                out.put(id, new EmployeeClient.EmployeeBrief(id, "EMP-" + id,
                        "Test Employee " + id, null, null, null, null, null, null, null, null));
            }
            return out;
        });
    }

    @Test
    void happyPath_eobiEnrolled_withAllowanceAndIncomeTax_postsExactRupeeNet() {
        long emp = seedEmployee(new BigDecimal("100000.00"), true, PaymentMode.CASH, List.of(
                new ComponentSeed("HRA", new BigDecimal("10000.00")),
                new ComponentSeed("INCOME_TAX", new BigDecimal("5000.00"))
        ));
        mockAttendance(emp, BigDecimal.ZERO, BigDecimal.ZERO);

        PayrollRun run = newRun();
        var result = computeService.computeRun(run);

        assertThat(result.employeeCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(0);

        Payslip ps = payslipFor(run.getId());
        assertThat(ps.getBasic()).isEqualByComparingTo("100000.00");
        assertThat(ps.getAllowances()).isEqualByComparingTo("10000.00");
        assertThat(ps.getGross()).isEqualByComparingTo("110000.00");
        assertThat(ps.getDeductions()).isEqualByComparingTo("0.00");
        assertThat(ps.getTax()).isEqualByComparingTo("5000.00");
        assertThat(ps.getEobi()).isEqualByComparingTo("1500.00");
        // gross 110000 > cap 25000, so pessi = 25000 * 6% = 1500 (employer cost; not in net)
        assertThat(ps.getPessiEmployer()).isEqualByComparingTo("1500.00");
        // net = gross - deductions - tax - eobi = 110000 - 0 - 5000 - 1500 = 103500
        assertThat(ps.getNet()).isEqualByComparingTo("103500.00");
    }

    @Test
    void eobiOff_dropsTheStatutoryDeduction() {
        long emp = seedEmployee(new BigDecimal("100000.00"), false, PaymentMode.CASH, List.of(
                new ComponentSeed("INCOME_TAX", new BigDecimal("5000.00"))
        ));
        mockAttendance(emp, BigDecimal.ZERO, BigDecimal.ZERO);

        PayrollRun run = newRun();
        computeService.computeRun(run);

        Payslip ps = payslipFor(run.getId());
        assertThat(ps.getEobi()).isEqualByComparingTo("0.00");
        assertThat(ps.getNet()).isEqualByComparingTo("95000.00"); // 100000 - 5000 tax
    }

    @Test
    void pessiUnderCap_chargesGrossAtRate() {
        long emp = seedEmployee(new BigDecimal("20000.00"), false, PaymentMode.CASH, List.of());
        mockAttendance(emp, BigDecimal.ZERO, BigDecimal.ZERO);

        PayrollRun run = newRun();
        computeService.computeRun(run);

        Payslip ps = payslipFor(run.getId());
        // gross 20000 < cap 25000: 20000 * 6% = 1200
        assertThat(ps.getPessiEmployer()).isEqualByComparingTo("1200.00");
        assertThat(ps.getNet()).isEqualByComparingTo("20000.00");
    }

    @Test
    void freshPayslipsAreDraft_notPaid() {
        long emp = seedEmployee(new BigDecimal("50000.00"), false, PaymentMode.CASH, List.of());
        mockAttendance(emp, BigDecimal.ZERO, BigDecimal.ZERO);

        PayrollRun run = newRun();
        computeService.computeRun(run);

        Payslip ps = payslipFor(run.getId());
        assertThat(ps.getStatus()).isEqualTo(PayslipStatus.DRAFT);
    }

    @Test
    void noBasicSalary_persistsSkipRow() {
        // Settings exists but no EmployeeSalary row → NO_BASIC_SALARY skip.
        long employeeId = nextEmployeeId.getAndIncrement();
        EmployeePayrollSettings s = new EmployeePayrollSettings();
        s.setEmployeeId(employeeId);
        s.setHireDate(periodStart.minusYears(1));
        s.setEobiEnrolled(false);
        s.setPaymentMode(PaymentMode.CASH);
        settingsRepo.save(s);

        PayrollRun run = newRun();
        var result = computeService.computeRun(run);

        assertThat(result.employeeCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(1);
        List<PayrollRunSkip> rows = skipRepo.findAllByPayrollRunId(run.getId());
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getEmployeeId()).isEqualTo(employeeId);
            assertThat(row.getErrorCode()).isEqualTo("NO_BASIC_SALARY");
            assertThat(row.getEmployeeCode()).isEqualTo("EMP-" + employeeId);
        });
    }

    @Test
    void bankTransferWithoutBank_persistsSkipRow_thenRetryResolvesOnceBankAdded() {
        long emp = seedEmployee(new BigDecimal("50000.00"), false, PaymentMode.BANK_TRANSFER, List.of());
        mockAttendance(emp, BigDecimal.ZERO, BigDecimal.ZERO);

        PayrollRun run = newRun();
        runRepo.save(run);

        // Drive through PayrollRunService so reset/retry-skips behaviours
        // are exercised end-to-end (skipped_count + skip rows + audit).
        // compute() is now async (kicks off PAYROLL_RUN_COMPUTE saga); the
        // test drives the step body synchronously via runComputeStep so
        // assertions don't race the runner thread.
        runService.compute(run.getId());
        computeService.runComputeStep(run.getId(), Map.of());
        var dto = runService.get(run.getId());
        assertThat(dto.skippedCount()).isEqualTo(1);
        assertThat(skipRepo.findAllByPayrollRunId(run.getId())).hasSize(1);
        assertThat(payslipRepo.findAll()).noneMatch(p -> p.getPayrollRunId() == run.getId());

        // HR fixes the data, retries — payslip appears, skip row goes away.
        Bank bankRow = banksRepo.findAll().stream().findFirst().orElseGet(() -> {
            Bank b = new Bank();
            b.setCode("TEST");
            b.setName("Test Bank");
            return banksRepo.save(b);
        });
        EmployeeBankDetails bank = new EmployeeBankDetails();
        bank.setEmployeeId(emp);
        bank.setBankId(bankRow.getId());
        bank.setAccountTitle("Test");
        bank.setIban("PK00TEST0000000000000000");
        bankRepo.save(bank);

        var afterRetry = runService.retrySkips(run.getId(), List.of(emp));
        assertThat(afterRetry.skippedCount()).isEqualTo(0);
        assertThat(skipRepo.findAllByPayrollRunId(run.getId())).isEmpty();
        Payslip ps = payslipFor(run.getId());
        assertThat(ps.getStatus()).isEqualTo(PayslipStatus.DRAFT);
    }

    @Test
    void undoPayslip_parksEmployeeAsSkip_andWaivedRetryDeductsZero() {
        // Ids well clear of other test classes' seeded advances / RFD rows,
        // which share this database and are keyed by employee id.
        nextEmployeeId.set(9_000_000L + (System.nanoTime() % 900_000L));
        long emp = seedEmployee(new BigDecimal("50300.00"), false, PaymentMode.CASH, List.of());
        mockAttendance(emp, BigDecimal.ZERO, BigDecimal.ZERO);
        FiscalYear fy = fyRepo.findAll().stream().findFirst().orElseThrow();
        com.forward.hrms.payroll.entity.PermanentAdvance pa = new com.forward.hrms.payroll.entity.PermanentAdvance();
        pa.setRequestCode("PA-U" + (emp % 100_000_000L));
        pa.setEmployeeId(emp);
        pa.setAmount(new BigDecimal("10000.00"));
        pa.setMonthlyDeduction(new BigDecimal("1000.00"));
        pa.setStatus(com.forward.hrms.payroll.enums.AdvanceStatus.APPROVED);
        pa.setFiscalYearCode(fy.getCode());
        pa = permanentAdvanceRepo.save(pa);

        PayrollRun run = newRun();
        runService.compute(run.getId());
        computeService.runComputeStep(run.getId(), Map.of());
        Payslip computed = payslipFor(run.getId());
        assertThat(permanentAdvanceDeductionRepo.employeeTotalPermanentAdvanceDeduction(emp))
                .isEqualByComparingTo("1000.00");
        // Cash net 50300 − 1000 = 49300 → rounded up to 49500 with a 200 RFD row.
        assertThat(rfdAdvanceRepo.sumForPeriod(emp, LocalDate.now(), LocalDate.now()))
                .isEqualByComparingTo("200.00");

        var afterUndo = undoService.undoPayslip(run.getId(), computed.getId());
        assertThat(afterUndo.skippedCount()).isEqualTo(1);
        assertThat(payslipRepo.findById(computed.getId())).isEmpty();
        assertThat(lineRepo.findAllByPayslipIdOrderByDisplayOrder(computed.getId())).isEmpty();
        assertThat(permanentAdvanceDeductionRepo.employeeTotalPermanentAdvanceDeduction(emp))
                .isEqualByComparingTo("0");
        assertThat(rfdAdvanceRepo.sumForPeriod(emp, LocalDate.now(), LocalDate.now()))
                .isEqualByComparingTo("0");
        List<PayrollRunSkip> parked = skipRepo.findAllByPayrollRunId(run.getId());
        assertThat(parked).singleElement()
                .satisfies(s -> assertThat(s.getErrorCode()).isEqualTo(PayrollRunUndoService.MANUALLY_UNDONE));

        var advance = undoService.skipAdvance(run.getId(), emp);
        assertThat(advance.advanceId()).isEqualTo(pa.getId());
        assertThat(advance.outstanding()).isEqualByComparingTo("10000.00");
        assertThat(advance.waivedThisMonth()).isFalse();
        assertThat(undoService.setSkipAdvanceWaiver(run.getId(), emp, true).waivedThisMonth()).isTrue();
        // The advance's own installment is untouched — next month deducts it again.
        assertThat(permanentAdvanceRepo.findById(pa.getId()).orElseThrow().getMonthlyDeduction())
                .isEqualByComparingTo("1000.00");

        // Preview computes the waived payslip and keeps nothing.
        var preview = undoService.previewSkip(run.getId(), emp);
        assertThat(preview.lines()).noneMatch(l -> "PERMANENT_ADVANCE".equals(l.code()));
        assertThat(payslipRepo.findEmployeeIdsByPayrollRunId(run.getId())).doesNotContain(emp);
        assertThat(permanentAdvanceDeductionRepo.findAllByEmployeeIdOrderByTransactionDateDescIdDesc(emp)).isEmpty();
        assertThat(rfdAdvanceRepo.sumForPeriod(emp, LocalDate.now(), LocalDate.now()))
                .isEqualByComparingTo("0");

        var afterRetry = runService.retrySkips(run.getId(), List.of(emp));
        assertThat(afterRetry.skippedCount()).isEqualTo(0);
        Payslip recomputed = payslipFor(run.getId());
        assertThat(recomputed.getNet()).isEqualByComparingTo(preview.net());
        assertThat(lineRepo.findAllByPayslipIdOrderByDisplayOrder(recomputed.getId()))
                .noneMatch(l -> "PERMANENT_ADVANCE".equals(l.getCode()));
        // The undone payslip's RFD is gone, so the retry doesn't recover it.
        assertThat(lineRepo.findAllByPayslipIdOrderByDisplayOrder(recomputed.getId()))
                .noneMatch(l -> "RFD_RECOVERY".equals(l.getCode()));
        // The waived month still leaves a 0 row in the ledger.
        assertThat(permanentAdvanceDeductionRepo.findAllByEmployeeIdOrderByTransactionDateDescIdDesc(emp))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.getDeductedAmount()).isEqualByComparingTo("0");
                    assertThat(d.getRemainingBalance()).isEqualByComparingTo("10000.00");
                });

        long runId = run.getId();
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            rfdAdvanceRepo.deleteCreatedByRun(runId);
            permanentAdvanceDeductionRepo.deleteBySource(
                    com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource.SALARY, runId);
        });
        permanentAdvanceRepo.delete(pa);
        new TransactionTemplate(txManager).executeWithoutResult(tx -> permanentAdvanceWaiverRepo
                .deleteForPeriod(emp, run.getPeriodYear(), run.getPeriodMonth()));
    }

    @Test
    void resetClearsSkipRows() {
        long employeeId = nextEmployeeId.getAndIncrement();
        EmployeePayrollSettings s = new EmployeePayrollSettings();
        s.setEmployeeId(employeeId);
        s.setHireDate(periodStart.minusYears(1));
        s.setEobiEnrolled(false);
        s.setPaymentMode(PaymentMode.CASH);
        settingsRepo.save(s);

        PayrollRun run = newRun();
        runService.compute(run.getId());
        computeService.runComputeStep(run.getId(), Map.of());
        var dto = runService.get(run.getId());
        assertThat(dto.skippedCount()).isEqualTo(1);
        assertThat(skipRepo.findAllByPayrollRunId(run.getId())).hasSize(1);

        var afterReset = runService.reset(run.getId());
        assertThat(afterReset.skippedCount()).isEqualTo(0);
        assertThat(skipRepo.findAllByPayrollRunId(run.getId())).isEmpty();
    }

    @Test
    void resetDropsPriorComputeSagaSoNextComputeProceeds() {
        long employeeId = nextEmployeeId.getAndIncrement();
        EmployeePayrollSettings s = new EmployeePayrollSettings();
        s.setEmployeeId(employeeId);
        s.setHireDate(periodStart.minusYears(1));
        s.setEobiEnrolled(false);
        s.setPaymentMode(PaymentMode.CASH);
        settingsRepo.save(s);

        PayrollRun run = newRun();
        runService.compute(run.getId());
        computeService.runComputeStep(run.getId(), Map.of());
        long firstSagaId = sagaService.findByKey("PAYROLL_RUN_COMPUTE",
                "payroll_run:" + run.getId()).orElseThrow().getId();

        runService.reset(run.getId());
        assertThat(sagaService.findByKey("PAYROLL_RUN_COMPUTE",
                "payroll_run:" + run.getId())).isEmpty();

        runService.compute(run.getId());
        var fresh = sagaService.findByKey("PAYROLL_RUN_COMPUTE",
                "payroll_run:" + run.getId()).orElseThrow();
        assertThat(fresh.getId()).isNotEqualTo(firstSagaId);
        assertThat(fresh.getStatus()).isEqualTo(
                com.forward.hrms.commons.saga.SagaState.Status.RUNNING);
    }

    @Test
    void lopProration_basicRoundsCeilingAtScale2() {
        long emp = seedEmployee(new BigDecimal("100000.00"), false, PaymentMode.CASH, List.of());
        mockAttendance(emp, BigDecimal.ONE, BigDecimal.ZERO);

        PayrollRun run = newRun();
        computeService.computeRun(run);

        int days = period.lengthOfMonth();
        BigDecimal expected = new BigDecimal("100000")
                .multiply(BigDecimal.valueOf(days - 1))
                .divide(BigDecimal.valueOf(days), 2, RoundingMode.CEILING);

        Payslip ps = payslipFor(run.getId());
        assertThat(ps.getBasic()).isEqualByComparingTo(expected);
    }

    /**
     * Proves payslips are committed per-row, not as one big transaction.
     * Seeds 60 employees so the compute crosses two
     * {@code PROGRESS_CHUNK=25} boundaries, then probes from a sibling
     * REQUIRES_NEW transaction at each progress pulse. Under PG's default
     * READ COMMITTED isolation, a sibling SELECT only sees rows already
     * committed — so a non-zero mid-run count is positive proof that
     * {@code BatchRunner.runWithPartial}'s per-row commit boundary works.
     */
    @Test
    void compute_commitsEachPayslipIndependently_notSingleTransaction() {
        int n = 60;
        List<Long> empIds = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            empIds.add(seedEmployee(new BigDecimal("50000.00"), false, PaymentMode.CASH, List.of()));
        }
        int days = period.lengthOfMonth();
        AttendanceSnapshot snap = new AttendanceSnapshot(
                BigDecimal.ZERO,
                BigDecimal.valueOf(days),
                BigDecimal.ZERO,
                days,
                BigDecimal.valueOf(days),
                BigDecimal.ZERO,
                0,
                0
        );
        when(attendanceClient.snapshot(anyLong(), eq(periodStart), eq(periodEnd), any()))
                .thenReturn(snap);

        PayrollRun run = newRun();
        long runId = run.getId();

        TransactionTemplate siblingTx = new TransactionTemplate(txManager);
        siblingTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        List<Integer> midRunVisibleCounts = new ArrayList<>();
        BiConsumer<Integer, Integer> probe = (processed, skipped) -> {
            Integer visible = siblingTx.execute(s ->
                    payslipRepo.findAllByPayrollRunIdAndStatus(runId, PayslipStatus.DRAFT).size());
            midRunVisibleCounts.add(visible);
        };

        var result = computeService.computeRun(run, Map.of(), probe);

        assertThat(result.employeeCount()).isEqualTo(n);
        assertThat(result.skippedCount()).isEqualTo(0);
        // Two chunks (25 + 25) plus a tail (10) → at least three pulses.
        assertThat(midRunVisibleCounts).hasSizeGreaterThanOrEqualTo(3);
        // First pulse fires after the first 25 rows commit; sibling must
        // see them. If everything were one tx, this would be 0.
        assertThat(midRunVisibleCounts.get(0)).isGreaterThanOrEqualTo(25);
        // Counts must monotonically grow toward n as more rows commit.
        for (int i = 1; i < midRunVisibleCounts.size(); i++) {
            assertThat(midRunVisibleCounts.get(i))
                    .isGreaterThanOrEqualTo(midRunVisibleCounts.get(i - 1));
        }
        assertThat(midRunVisibleCounts.get(midRunVisibleCounts.size() - 1)).isEqualTo(n);
    }

    /**
     * Drives compute via the real saga-step entry point and verifies
     * {@code GET /summary} returns a coherent shape: live counts plus
     * SQL-aggregated totals. Also asserts the live {@code skippedCount}
     * pulse — the {@code attendance.snapshot} stub reads the run row
     * from a sibling REQUIRES_NEW tx on every per-row call, and we
     * assert at least one read sees a non-zero {@code skippedCount}
     * before the saga step's final write. Without the pulse fix every
     * read would be 0.
     */
    @Test
    void summary_returnsLiveCountsAndTotals_andSkippedCountIsLivePulsed() {
        // Seed bad-settings FIRST so they fall into chunk 1; valid after,
        // so per-row attendance.snapshot calls in chunk 2 land AFTER the
        // chunk-1 pulse has already committed a non-zero skippedCount on
        // the run row.
        int badSettings = 5;
        int valid = 30;
        for (int i = 0; i < badSettings; i++) {
            long employeeId = nextEmployeeId.getAndIncrement();
            EmployeePayrollSettings s = new EmployeePayrollSettings();
            s.setEmployeeId(employeeId);
            s.setHireDate(periodStart.minusYears(1));
            s.setEobiEnrolled(false);
            s.setPaymentMode(PaymentMode.CASH);
            settingsRepo.save(s);
        }
        for (int i = 0; i < valid; i++) {
            seedEmployee(new BigDecimal("50000.00"), false, PaymentMode.CASH, List.of());
        }

        PayrollRun run = newRun();
        long runId = run.getId();

        int days = period.lengthOfMonth();
        AttendanceSnapshot snap = new AttendanceSnapshot(
                BigDecimal.ZERO, BigDecimal.valueOf(days), BigDecimal.ZERO,
                days, BigDecimal.valueOf(days), BigDecimal.ZERO, 0, 0);

        TransactionTemplate siblingTx = new TransactionTemplate(txManager);
        siblingTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        List<Integer> liveSkipReadings = new java.util.concurrent.CopyOnWriteArrayList<>();
        when(attendanceClient.snapshot(anyLong(), eq(periodStart), eq(periodEnd), any()))
                .thenAnswer(inv -> {
                    Integer pulsedSkipped = siblingTx.execute(s ->
                            runRepo.findById(runId).map(PayrollRun::getSkippedCount).orElse(-1));
                    liveSkipReadings.add(pulsedSkipped);
                    return snap;
                });

        runService.compute(runId);
        computeService.runComputeStep(runId, Map.of());

        // Chunk-1 pulse commits (25, 5) before chunk 2's attendance calls
        // fire — so at least one snapshot stub call records skippedCount=5.
        // Without the pulse fix, the run row would carry skippedCount=0
        // throughout the step and flip to 5 only at the end-of-step write.
        assertThat(liveSkipReadings).anyMatch(c -> c != null && c == badSettings);

        var summary = runService.summary(runId);
        assertThat(summary.id()).isEqualTo(runId);
        assertThat(summary.status()).isEqualTo(PayrollRunStatus.COMPUTED);
        assertThat(summary.totalCount()).isEqualTo(badSettings + valid);
        assertThat(summary.skippedCount()).isEqualTo(badSettings);
        assertThat(summary.successCount()).isEqualTo(valid);
        assertThat(summary.totals()).isNotNull();
        assertThat(summary.totals().employeeCount()).isEqualTo(valid);
        assertThat(summary.totals().basic()).isEqualByComparingTo(
                new BigDecimal("50000.00").multiply(BigDecimal.valueOf(valid)));
        assertThat(summary.totals().net()).isEqualByComparingTo(
                new BigDecimal("50000.00").multiply(BigDecimal.valueOf(valid)));
    }

    // ---------------------------------------------------------------- helpers

    private record ComponentSeed(String code, BigDecimal amount) {}

    private long seedEmployee(BigDecimal basic, boolean eobi, PaymentMode mode,
                              List<ComponentSeed> components) {
        long employeeId = nextEmployeeId.getAndIncrement();

        EmployeePayrollSettings s = new EmployeePayrollSettings();
        s.setEmployeeId(employeeId);
        s.setHireDate(periodStart.minusYears(2));
        s.setEobiEnrolled(eobi);
        s.setPaymentMode(mode);
        settingsRepo.save(s);

        EmployeeSalary sal = new EmployeeSalary();
        sal.setEmployeeId(employeeId);
        sal.setBasic(basic);
        sal.setEffectiveFrom(periodStart.minusMonths(1));
        salaryRepo.save(sal);

        for (ComponentSeed c : components) {
            SalaryComponent catalog = catalogRepo.findByCode(c.code()).orElseThrow(
                    () -> new IllegalStateException("missing catalog row: " + c.code()));
            EmployeeSalaryComponent line = new EmployeeSalaryComponent();
            line.setEmployeeId(employeeId);
            line.setComponentId(catalog.getId());
            line.setAmount(c.amount());
            line.setEffectiveFrom(periodStart.minusMonths(1));
            componentLineRepo.save(line);
        }
        return employeeId;
    }

    private void mockAttendance(long employeeId, BigDecimal lopDays, BigDecimal otHours) {
        int days = period.lengthOfMonth();
        BigDecimal active = BigDecimal.valueOf(days).subtract(lopDays);
        AttendanceSnapshot snap = new AttendanceSnapshot(
                lopDays,
                active,
                otHours,
                days,
                active,
                lopDays,
                0,
                0
        );
        when(attendanceClient.snapshot(eq(employeeId), eq(periodStart), eq(periodEnd), any())).thenReturn(snap);
    }

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

    private Payslip payslipFor(long runId) {
        return payslipRepo.findAll().stream()
                .filter(p -> Objects.equals(p.getPayrollRunId(), runId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no payslip for run " + runId));
    }
}

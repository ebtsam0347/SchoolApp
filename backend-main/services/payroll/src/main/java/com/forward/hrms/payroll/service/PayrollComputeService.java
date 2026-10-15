package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.batch.BatchOutcomes;
import com.forward.hrms.commons.batch.BatchRunner;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.core.money.Money;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.config.PayrollProperties;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.EmployeeSalaryComponent;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.entity.PayrollRunSkip;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.entity.PayslipLine;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.entity.PayrollConfig;
import com.forward.hrms.employee.enums.GradeType;
import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.PayrollRunStatus;
import com.forward.hrms.payroll.enums.PayslipStatus;
import com.forward.hrms.payroll.events.PayrollEvents;
import com.forward.hrms.payroll.repository.AnnualLeaveTransactionRepository;
import com.forward.hrms.payroll.repository.EmployeeBankDetailsRepository;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryComponentRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryRepository;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.repository.PayrollRunSkipRepository;
import com.forward.hrms.payroll.repository.PayslipLineRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceWaiverRepository;
import com.forward.hrms.payroll.repository.RfdAdvanceRepository;
import com.forward.hrms.payroll.client.FairPriceShopClient;
import com.forward.hrms.payroll.entity.AnnualLeaveTransaction;
import com.forward.hrms.payroll.entity.RfdAdvance;
import com.forward.hrms.payroll.enums.PaymentMode;
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.entity.PermanentAdvanceDeduction;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.payroll.dto.response.PayslipDto;
import com.forward.hrms.payroll.dto.response.PayslipDtoMapper;
import com.forward.hrms.payroll.dto.response.PayslipLineDtoMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Per-employee compute. Implementation matches the rules in
 * {@code docs/services/payroll.md} §7.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollComputeService {

    private final PayrollProperties props;

    private final EmployeePayrollSettingsRepository settingsRepo;
    private final EmployeeSalaryRepository salaryRepo;
    private final EmployeeSalaryComponentRepository componentLineRepo;
    private final SalaryComponentRepository componentCatalogRepo;
    private final EmployeeBankDetailsRepository bankRepo;
    private final PayslipRepository payslipRepo;
    private final PayslipLineRepository lineRepo;
    private final PayrollRunSkipRepository skipsRepo;
    private final PayrollRunRepository runRepo;
    private final ShortTermAdvanceRepository shortTermAdvanceRepo;
    private final PermanentAdvanceRepository permanentAdvanceRepo;
    private final PermanentAdvanceDeductionRepository permanentAdvanceDeductionRepo;
    private final PermanentAdvanceWaiverRepository permanentAdvanceWaiverRepo;
    private final RfdAdvanceRepository rfdAdvanceRepo;
    private final AnnualLeaveTransactionRepository annualLeaveRepo;

    private final PayrollConfigService payrollConfig;
    private final EmployeeClient employees;
    private final AttendanceClient attendance;
    private final FairPriceShopClient fairPriceShop;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final BatchRunner batch;
    private final PlatformTransactionManager txManager;

    /** Pulse {@code progress_count} every this-many rows. Enough to keep
     *  the FE moving; low enough that the writes are negligible cost.
     *  Each pulse commits independently via {@link BatchRunner#runInNewTxVoid}
     *  (REQUIRES_NEW) so the FE sees the counter tick mid-compute. */
    private static final int PROGRESS_CHUNK = 25;

    public record ComputeResult(int employeeCount, BigDecimal totalNet,
                                int skippedCount, List<SkippedEmployee> skipped) {

        public ComputeResult(int employeeCount, BigDecimal totalNet) {
            this(employeeCount, totalNet, 0, List.of());
        }
    }

    /** §7.payroll #13 — surfaced per-row skip reason for the run summary + outbox. */
    public record SkippedEmployee(long employeeId, String errorCode, String message) {}

    /** Backward-compat: no identity forwarding, no progress callback. Used by
     *  unit / integration tests that drive compute directly. */
    public ComputeResult computeRun(PayrollRun run) {
        return computeRun(run, null, null);
    }

    /**
     * Compute every active employee for the run.
     *
     * <p>{@code identity} is the {@code X-User-*} snapshot from the kickoff
     * caller — passed through to {@code employees.getMany(ids, identity)}
     * so the saga-thread bulk brief load doesn't 403. Pass {@code null}
     * from request-bound threads (the commons-web interceptor handles it
     * automatically there).
     *
     * <p>{@code onProgress} is invoked with {@code (processed, skipped)}
     * after each {@value #PROGRESS_CHUNK}-row chunk: {@code processed} is
     * the cumulative (already-computed + freshly-processed) employee
     * count, {@code skipped} is the cumulative failure count seen so
     * far. Pass {@code null} when no progress reporting is wanted
     * (tests). The runner writes both to the run row in a single
     * REQUIRES_NEW pulse so {@code GET /payroll/runs/{id}/summary} shows
     * live skip counts mid-run instead of a 0 that flips at the end.
     *
     * <p>Saga-retry safety: employees that already have a payslip on this
     * run (committed under per-row REQUIRES_NEW in an earlier attempt)
     * are filtered out so the unique
     * {@code (payroll_run_id, employee_id)} index doesn't surface as a
     * "duplicate" failure on every row of the second pass.
     */
    public ComputeResult computeRun(PayrollRun run,
                                    Map<String, String> identity,
                                    BiConsumer<Integer, Integer> onProgress) {
        LocalDate periodStart = LocalDate.of(run.getPeriodYear(), run.getPeriodMonth(), 1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());
        PayrollConfig stat = payrollConfig.effectiveOn(periodEnd);

        // Bulk-load the catalog once per run instead of per line. With ~20
        // active components × ~500 employees that was 10k findById round
        // trips per run. The catalog is small (dozens of rows), held in
        // memory for the run's duration, and rebuilt next run.
        Map<Long, SalaryComponent> catalog = componentCatalogRepo.findAll().stream()
                .collect(Collectors.toMap(SalaryComponent::getId, Function.identity()));

        // Pre-resolve the employee briefs the same way: one /employees/by-ids
        // batch (chunked at 200 inside EmployeeClient.getMany) instead of a
        // GET per row.
        // findAllByActiveTrue, not findAll — a terminated employee's
        // settings row is flipped inactive by
        // EmployeePayrollSettingsService.markInactive (Audit Finding 4);
        // including them here would generate a regular payslip alongside
        // their final settlement for the same days.
        List<EmployeePayrollSettings> all = settingsRepo.findAllByActiveTrue();
        Set<Long> ids = all.stream()
                .map(EmployeePayrollSettings::getEmployeeId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, EmployeeClient.EmployeeBrief> briefs = employees.getMany(ids, identity);
        // Catalog of grade → GradeType so per-employee compute can apply
        // the grade-conditional canteen rule without re-querying employee
        // service per row. Empty on lookup failure — compute then treats
        // every employee as LABOR (per-day canteen) which is the safer
        // default than charging everyone the weekly rate.
        Map<Long, GradeType> gradeTypes = employees.gradeTypesById(identity);
        // One read of the shop's month view for the whole run, keyed by card
        // number (= employee code). Throws if the shop DB is unreachable, so
        // the compute step fails and the saga retries rather than paying
        // everyone without the deduction.
        Map<String, BigDecimal> fairPrice = fairPriceShop.amountsForMonth(YearMonth.from(periodStart));

        // Filter out employees who already have a payslip on this run from a
        // partial earlier attempt — saga-retry safety.
        Set<Long> alreadyComputedIds = payslipRepo.findEmployeeIdsByPayrollRunId(run.getId());
        List<EmployeePayrollSettings> remaining = alreadyComputedIds.isEmpty() ? all
                : all.stream()
                        .filter(s -> !alreadyComputedIds.contains(s.getEmployeeId()))
                        .toList();

        // §7.payroll #12 — per-employee REQUIRES_NEW so a single bad row
        // doesn't roll back the whole run. Chunked so we can pulse
        // progress_count between chunks (the FE's "X / total" needs at
        // least one update per ~25 rows to feel live; the writes are
        // negligible cost vs. to compute itself).
        List<BatchOutcomes.Success<EmployeePayrollSettings, Payslip>> allOk = new ArrayList<>();
        List<BatchOutcomes.Failure<EmployeePayrollSettings>> allFailed = new ArrayList<>();
        int processed = alreadyComputedIds.size();
        for (int i = 0; i < remaining.size(); i += PROGRESS_CHUNK) {
            List<EmployeePayrollSettings> chunk = remaining.subList(i, Math.min(i + PROGRESS_CHUNK, remaining.size()));
            BatchOutcomes<EmployeePayrollSettings, Payslip> outcomes = batch.runWithPartial(chunk,
                    settings -> computeOne(run, settings, periodStart, periodEnd, stat, catalog,
                            briefs, gradeTypes, fairPrice, identity));
            allOk.addAll(outcomes.succeeded());
            allFailed.addAll(outcomes.failed());
            processed += chunk.size();
            if (onProgress != null) onProgress.accept(processed, allFailed.size());
        }

        int count = 0;
        BigDecimal totalNet = BigDecimal.ZERO;
        for (BatchOutcomes.Success<EmployeePayrollSettings, Payslip> ok : allOk) {
            count++;
            totalNet = totalNet.add(ok.result().getNet());
        }

        List<SkippedEmployee> skipped = new ArrayList<>(allFailed.size());
        for (BatchOutcomes.Failure<EmployeePayrollSettings> f : allFailed) {
            skipped.add(new SkippedEmployee(f.item().getEmployeeId(), f.errorCode(), f.message()));
            log.warn("compute failed for employee {} in run {}: {} ({})",
                    f.item().getEmployeeId(), run.getId(), f.message(), f.errorCode());
        }
        persistSkips(run.getId(), skipped, briefs);

        return new ComputeResult(count, totalNet, skipped.size(), skipped);
    }

    /**
     * Saga-step entry point. Drives a {@code COMPUTING} run to
     * {@code COMPUTED} on the runner thread: full compute + run-row
     * bookkeeping + outbox + audit. Idempotent — re-entry on a
     * {@code COMPUTED} run returns immediately so saga retry on a
     * partial earlier attempt is safe (the inner {@link #computeRun}
     * pass already filters out already-computed employees).
     *
     * <p>Tests call this directly to drive compute end-to-end against a
     * real DB without going through the saga runner.
     */
    @Transactional
    public ComputeResult runComputeStep(long runId, Map<String, String> identity) {
        PayrollRun run = runRepo.findById(runId).orElseThrow(
                () -> new IllegalStateException("payroll run " + runId + " disappeared mid-saga"));
        if (run.getStatus() == PayrollRunStatus.COMPUTED) {
            // Saga retry on a finalized run — return zero-shaped result.
            return new ComputeResult(0, BigDecimal.ZERO, run.getSkippedCount(), List.of());
        }

        BiConsumer<Integer, Integer> pulse = (processed, skipped) -> batch.runInNewTxVoid(() ->
                runRepo.findById(runId).ifPresent(r -> {
                    r.setProgressCount(processed);
                    r.setSkippedCount(skipped);
                }));
        ComputeResult result = computeRun(run, identity, pulse);

        run.setStatus(PayrollRunStatus.COMPUTED);
        run.setComputedAt(LocalDateTime.now());
        run.setSkippedCount(result.skippedCount());
        run.setProgressCount(run.getTotalCount() == 0 ? result.employeeCount() + result.skippedCount()
                : run.getTotalCount());

        outbox.publish(PayrollEvents.TOPIC, "payroll_run:" + runId,
                PayrollEvents.RUN_COMPUTED,
                new PayrollEvents.RunComputed(runId, run.getFiscalYearCode(),
                        result.employeeCount(), result.totalNet()));
        Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("employeeCount", result.employeeCount());
        auditAfter.put("totalNet", result.totalNet());
        if (result.skippedCount() > 0) {
            auditAfter.put("skippedCount", result.skippedCount());
            auditAfter.put("skipped", skipSampleForAudit(result.skipped()));
        }
        audit.emit("payroll_run", String.valueOf(runId), "run_computed", null, auditAfter, true);
        return result;
    }

    private static List<Map<String, Object>> skipSampleForAudit(List<SkippedEmployee> all) {
        int max = Math.min(20, all.size());
        List<Map<String, Object>> sample = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            SkippedEmployee s = all.get(i);
            sample.add(Map.of("employeeId", s.employeeId(),
                    "errorCode", s.errorCode(),
                    "message", s.message()));
        }
        return sample;
    }

    /**
     * Re-run compute for a specific subset of employees on an
     * already-computed run. Used by {@code POST /retry-skips} after HR
     * fixes the underlying data. Returns the per-employee outcomes;
     * callers reconcile against {@code payroll_run_skips}. Per-employee
     * REQUIRES_NEW still applies via {@link BatchRunner}; the unique
     * {@code (payroll_run_id, employee_id)} index on payslips protects
     * against double-insert if two HR users click retry concurrently.
     */
    public RetryOutcomes recomputeFor(PayrollRun run, List<Long> employeeIds) {
        if (employeeIds.isEmpty()) return new RetryOutcomes(List.of(), List.of());
        LocalDate periodStart = LocalDate.of(run.getPeriodYear(), run.getPeriodMonth(), 1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());
        PayrollConfig stat = payrollConfig.effectiveOn(periodEnd);

        Map<Long, SalaryComponent> catalog = componentCatalogRepo.findAll().stream()
                .collect(Collectors.toMap(SalaryComponent::getId, Function.identity()));
        List<EmployeePayrollSettings> targets = settingsRepo.findAllByEmployeeIdIn(employeeIds);
        Set<Long> ids = targets.stream().map(EmployeePayrollSettings::getEmployeeId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, EmployeeClient.EmployeeBrief> briefs = employees.getMany(ids);
        Map<Long, GradeType> gradeTypes = employees.gradeTypesById(null);
        Map<String, BigDecimal> fairPrice = fairPriceShop.amountsForMonth(YearMonth.from(periodStart));

        BatchOutcomes<EmployeePayrollSettings, Payslip> outcomes = batch.runWithPartial(targets,
                settings -> computeOne(run, settings, periodStart, periodEnd, stat, catalog,
                        briefs, gradeTypes, fairPrice, null));

        List<Long> succeededIds = new ArrayList<>(outcomes.succeededCount());
        for (BatchOutcomes.Success<EmployeePayrollSettings, Payslip> ok : outcomes.succeeded()) {
            succeededIds.add(ok.item().getEmployeeId());
        }
        List<SkippedEmployee> stillFailed = new ArrayList<>(outcomes.failedCount());
        for (BatchOutcomes.Failure<EmployeePayrollSettings> f : outcomes.failed()) {
            stillFailed.add(new SkippedEmployee(f.item().getEmployeeId(), f.errorCode(), f.message()));
            log.warn("retry-compute failed for employee {} in run {}: {} ({})",
                    f.item().getEmployeeId(), run.getId(), f.message(), f.errorCode());
        }
        return new RetryOutcomes(succeededIds, stillFailed);
    }

    public record RetryOutcomes(List<Long> succeededEmployeeIds, List<SkippedEmployee> stillFailed) {}

    /**
     * What {@link #recomputeFor} would produce for one employee, without
     * keeping it: the same per-employee compute runs in its own transaction,
     * which is then rolled back (payslip, lines, ledger and RFD rows all go).
     * Compute errors surface as-is, so HR sees why the retry would fail.
     */
    public PayslipDto previewFor(PayrollRun run, long employeeId) {
        LocalDate periodStart = LocalDate.of(run.getPeriodYear(), run.getPeriodMonth(), 1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> {
            status.setRollbackOnly();
            EmployeePayrollSettings settings = settingsRepo.findAllByEmployeeIdIn(List.of(employeeId))
                    .stream().findFirst()
                    .orElseThrow(() -> new NotFoundError(PayrollErrorCode.SETTINGS_NOT_FOUND,
                            "employee " + employeeId + " has no payroll settings"));
            PayrollConfig stat = payrollConfig.effectiveOn(periodEnd);
            Map<Long, SalaryComponent> catalog = componentCatalogRepo.findAll().stream()
                    .collect(Collectors.toMap(SalaryComponent::getId, Function.identity()));
            Map<Long, EmployeeClient.EmployeeBrief> briefs = employees.getMany(Set.of(employeeId));
            Map<Long, GradeType> gradeTypes = employees.gradeTypesById(null);
            Map<String, BigDecimal> fairPrice = fairPriceShop.amountsForMonth(YearMonth.from(periodStart));
            Payslip ps = computeOne(run, settings, periodStart, periodEnd, stat, catalog,
                    briefs, gradeTypes, fairPrice, null);
            return PayslipDtoMapper.of(ps, lineRepo.findAllByPayslipIdOrderByDisplayOrder(ps.getId())
                    .stream().map(PayslipLineDtoMapper::of).toList());
        });
    }

    private void persistSkips(long runId, List<SkippedEmployee> skipped,
                              Map<Long, EmployeeClient.EmployeeBrief> briefs) {
        if (skipped.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        List<PayrollRunSkip> rows = new ArrayList<>(skipped.size());
        for (SkippedEmployee s : skipped) {
            EmployeeClient.EmployeeBrief b = briefs.getOrDefault(s.employeeId(),
                    EmployeeClient.EmployeeBrief.placeholder(s.employeeId()));
            PayrollRunSkip row = new PayrollRunSkip();
            row.setPayrollRunId(runId);
            row.setEmployeeId(s.employeeId());
            row.setEmployeeCode(b.employeeCode());
            row.setEmployeeFullName(b.fullName());
            row.setErrorCode(s.errorCode());
            row.setMessage(truncate(s.message(), 1000));
            row.setCreatedAt(now);
            rows.add(row);
        }
        skipsRepo.saveAll(rows);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * Post-time flip: every DRAFT payslip on the run becomes PAID, then
     * any RELEASE_PENDING payslips whose source HELD lines are on the
     * just-posted run flip to RELEASE. HELD payslips stay HELD so they
     * skip disbursement this cycle. Idempotent — the bulk update is a
     * no-op on a re-run because no DRAFT rows remain.
     */
    @Transactional
    public void markReleasedSourcesPosted(PayrollRun newlyPosted) {
        payslipRepo.bulkUpdateStatusForRun(newlyPosted.getId(),
                PayslipStatus.DRAFT, PayslipStatus.PAID);
        List<Payslip> ours = payslipRepo.findAllByPayrollRunIdAndStatus(
                newlyPosted.getId(), PayslipStatus.PAID);
        for (Payslip p : ours) {
            // Find any HELD_SALARY_RELEASE line we wrote at compute time.
            for (PayslipLine line : lineRepo.findAllByPayslipIdOrderByDisplayOrder(p.getId())) {
                if (!"HELD_SALARY_RELEASE".equals(line.getCode())) continue;
                List<Payslip> heldList = payslipRepo.findAllByEmployeeIdAndStatus(
                        p.getEmployeeId(), PayslipStatus.RELEASE_PENDING);
                for (Payslip held : heldList) {
                    held.setStatus(PayslipStatus.RELEASED);
                    held.setReleasedInRunId(newlyPosted.getId());
                    outbox.publish(PayrollEvents.TOPIC, "payslip:" + held.getId(),
                            PayrollEvents.SALARY_RELEASED,
                            new PayrollEvents.SalaryReleased(held.getId(), held.getEmployeeId(),
                                    newlyPosted.getId(), held.getNet()));
                }
                break; // one carryover line is enough; multiple shouldn't happen but tolerate
            }
        }
    }

    private Payslip computeOne(PayrollRun run,
                               EmployeePayrollSettings settings,
                               LocalDate periodStart, LocalDate periodEnd,
                               PayrollConfig stat,
                               Map<Long, SalaryComponent> catalog,
                               Map<Long, EmployeeClient.EmployeeBrief> briefs,
                               Map<Long, GradeType> gradeTypes,
                               Map<String, BigDecimal> fairPrice,
                               Map<String, String> identity) {
        long employeeId = settings.getEmployeeId();
        Optional<EmployeeSalary> salary = salaryRepo.findEffectiveOn(employeeId, periodEnd)
                .stream().findFirst();
        if (salary.isEmpty() || salary.get().getBasic() == null
                || salary.get().getBasic().signum() == 0) {
            // Surfaced as a skip row instead of a silent drop so HR can see the
            // employee in the FE and fix the salary before retrying.
            throw new UnprocessableError(PayrollErrorCode.NO_BASIC_SALARY,
                    "no effective basic salary for employee " + employeeId
                            + " on " + periodEnd);
        }
        if (settings.getPaymentMode() == com.forward.hrms.payroll.enums.PaymentMode.BANK_TRANSFER
                && !bankRepo.existsByEmployeeId(employeeId)) {
            throw new ConflictError(PayrollErrorCode.BANK_DETAILS_REQUIRED,
                    "employee " + employeeId + " is BANK_TRANSFER but has no bank details");
        }

        AttendanceSnapshot snapshot = attendance.snapshot(employeeId, periodStart, periodEnd, identity);
        BigDecimal daysInPeriod = BigDecimal.valueOf(periodEnd.getDayOfMonth());
        BigDecimal activeDays = daysInPeriod.subtract(snapshot.lopDays());

        BigDecimal basicAfterLop = Money.of(salary.get().getBasic())
                .proration(activeDays, daysInPeriod).amount();
        BigDecimal allowances = BigDecimal.ZERO;
        BigDecimal deductions = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;

        EmployeeClient.EmployeeBrief brief = briefs.getOrDefault(employeeId,
                EmployeeClient.EmployeeBrief.placeholder(employeeId));
        Payslip ps = new Payslip();
        ps.setPayrollRunId(run.getId());
        ps.setEmployeeId(employeeId);
        ps.setEmployeeCode(brief.employeeCode());
        ps.setPeriodYear(run.getPeriodYear());
        ps.setPeriodMonth(run.getPeriodMonth());
        ps.setFiscalYearCode(run.getFiscalYearCode());
        ps.setLopDays(snapshot.lopDays());
        ps.setOtHours(snapshot.otHours());
        ps.setWorkingDays(snapshot.workingDays());
        ps.setPaidDays(snapshot.paidDays());
        ps.setAbsentDays(snapshot.absentDays());
        ps.setHolidaysInPeriod(snapshot.holidaysInPeriod());
        ps.setWeekendsInPeriod(snapshot.weekendsInPeriod());
        ps = payslipRepo.save(ps);
        long psId = ps.getId();

        List<PayslipLine> linesToSave = new ArrayList<>();
        linesToSave.add(line(psId, null, "BASIC", "Basic", ComponentType.EARNING, CalcType.FLAT,
                null, null, basicAfterLop, 0));

        List<EmployeeSalaryComponent> activeLines = componentLineRepo
                .findEffectiveDuring(employeeId, periodStart, periodEnd);

        int displayOrder = 100;
        for (EmployeeSalaryComponent line : activeLines) {
            SalaryComponent comp = catalog.get(line.getComponentId());
            if (comp == null) continue;
            if(!comp.isCalculateWithSalary()) continue;
            // §canteen — the CANTEEN deduction is now driven by canteen
            // device usage + the grade-conditional rule (see canteen
            // injection below). Any per-employee FLAT CANTEEN line is a
            // pre-rule remnant; skipping it here prevents double-charging
            // without us having to delete the historical employee-level
            // configuration rows.
            if ("CANTEEN".equals(comp.getCode())) continue;
            // Same for FAIR_PRICE_SHOP: it now comes from the shop's view
            // (injected below), so a hand-kept flat line would double-charge.
            if ("FAIR_PRICE_SHOP".equals(comp.getCode())) continue;
            BigDecimal amount;
            BigDecimal rate = null;
            BigDecimal days = null;
            if (comp.getCalcType() == CalcType.PER_DAY && comp.getType() == ComponentType.EARNING) {
                rate = line.getAmount();
                days = snapshot.daysPresent();
                amount = Money.of(rate).multiply(days).amount();
            } else if (comp.getType() == ComponentType.EARNING) {
                amount = Money.of(line.getAmount()).proration(activeDays, daysInPeriod).amount();
            } else {
                amount = line.getAmount();
            }
            if (comp.getType() == ComponentType.EARNING) allowances = allowances.add(amount);
            else if ("INCOME_TAX".equals(comp.getCode())) tax = tax.add(amount);
            else deductions = deductions.add(amount);

            linesToSave.add(line(psId, comp.getId(), comp.getCode(), comp.getName(),
                    comp.getType(), comp.getCalcType(), rate, days, amount, displayOrder));
            displayOrder += 10;
        }

        BigDecimal otAmount = computeOt(salary.get().getBasic(), snapshot.otHours());
        if (otAmount.signum() > 0) {
            allowances = allowances.add(otAmount);
            linesToSave.add(line(psId, null, "OT", "Overtime", ComponentType.EARNING, CalcType.FLAT,
                    null, null, otAmount, displayOrder));
            displayOrder += 10;
        }

        BigDecimal canteenAmount = computeCanteen(employeeId, periodStart, periodEnd,
                brief, gradeTypes, stat, identity);
        // Always written (0.00 when the canteen was not used this month).
        deductions = deductions.add(canteenAmount);
        linesToSave.add(line(psId, null, "CANTEEN", "Canteen", ComponentType.DEDUCTION, CalcType.FLAT,
                null, null, canteenAmount, displayOrder));
        displayOrder += 10;

        // Fair Price Shop purchases in the period month. Before the cash
        // 500-note rounding, so RFD tops up the net after this deduction.
        // Always written (0.00 when nothing was bought). The external shop DB
        // is not ours to trust, so a net-negative total is floored at zero.
        BigDecimal fairPriceAmount = brief.employeeCode() == null ? BigDecimal.ZERO
                : fairPrice.getOrDefault(brief.employeeCode().trim(), BigDecimal.ZERO).max(BigDecimal.ZERO);
        deductions = deductions.add(fairPriceAmount);
        linesToSave.add(line(psId, null, "FAIR_PRICE_SHOP", "Fair Price Shop", ComponentType.DEDUCTION,
                CalcType.FLAT, null, null, fairPriceAmount, displayOrder));
        displayOrder += 10;

        // Short-term advances are recovered in full from their target
        // month's salary. Only fully approved lines count; status stays
        // APPROVED, so a recompute of the same month picks up the same rows.
        // The line is always written (0.00 when there is no advance) so the
        // payslip shows the employee explicitly that nothing was recovered.
        BigDecimal shortTermAdvance = shortTermAdvanceRepo
                .findApprovedForEmployeeMonth(employeeId, run.getPeriodYear(), run.getPeriodMonth())
                .stream().map(ShortTermAdvance::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        deductions = deductions.add(shortTermAdvance);
        linesToSave.add(line(psId, null, "SHORT_TERM_ADVANCE", "Short Term Advance",
                ComponentType.DEDUCTION, CalcType.FLAT, null, null, shortTermAdvance, displayOrder));
        displayOrder += 10;

        // Permanent advance. Like the short-term line, always written (0.00
        // when nothing is recovered). A zero installment writes no ledger row.
        BigDecimal permanentAdvance = applyPermanentAdvance(run, employeeId, periodEnd);
        deductions = deductions.add(permanentAdvance);
        linesToSave.add(line(psId, null, "PERMANENT_ADVANCE", "Permanent Advance",
                ComponentType.DEDUCTION, CalcType.FLAT, null, null, permanentAdvance, displayOrder));
        displayOrder += 10;

        // Annual leave payout: HR marked the month's row payable and locked
        // it. One regular run per month, so a recompute re-reads the same row.
        BigDecimal annualLeaveAmount = annualLeaveRepo
                .findByEmployeeIdAndCalculationMonth(employeeId, periodStart)
                .filter(t -> Boolean.TRUE.equals(t.getLockStatus()) && Boolean.TRUE.equals(t.getPaymentStatus()))
                .map(AnnualLeaveTransaction::getNetAmount)
                .orElse(BigDecimal.ZERO);
        // Always written (0.00 when nothing is payable this month).
        allowances = allowances.add(annualLeaveAmount);
        linesToSave.add(line(psId, null, "ANNUAL_LEAVE", "Annual Leave",
                ComponentType.EARNING, CalcType.FLAT, null, null, annualLeaveAmount, displayOrder));
        displayOrder += 10;

        // RFD is recovered whatever the payment mode (an employee may have
        // moved cash → bank since it was lent); only paying it is cash-only.
        boolean cash = settings.getPaymentMode() == PaymentMode.CASH;
        BigDecimal rfdRecovered = rfdAdvanceRepo.sumForPeriod(employeeId, periodStart, periodEnd);
        // Always written (0.00 when nothing is recovered this month).
        deductions = deductions.add(rfdRecovered);
        linesToSave.add(line(psId, null, "RFD_RECOVERY", "RFD Advance Recovered",
                ComponentType.DEDUCTION, CalcType.FLAT, null, null, rfdRecovered, displayOrder));
        displayOrder += 10;

        BigDecimal carryNet = applyHeldReleaseCarryover(employeeId, psId, displayOrder, linesToSave);
        if (carryNet.signum() > 0) {
            allowances = allowances.add(carryNet);
            displayOrder += 10;
        }

        BigDecimal gross = basicAfterLop.add(allowances);

        BigDecimal eobi = settings.isEobiEnrolled() ? stat.getEobiAmount() : BigDecimal.ZERO;
        BigDecimal pessiBase = gross.subtract(carryNet).max(BigDecimal.ZERO);
        BigDecimal pessi = Money.of(pessiBase.min(stat.getPessiSalaryCap()))
                .multiply(stat.getPessiRatePercent()
                        .divide(BigDecimal.valueOf(100), 8, RoundingMode.CEILING))
                .amount();

        BigDecimal net = gross.subtract(deductions).subtract(tax).subtract(eobi);

        // Cash is paid in 500-rupee notes: round the net up and lend the
        // excess as RFD. It is an earning (so gross − deductions still equals
        // net) but stays out of the PESSI base, which is already fixed above.
        BigDecimal rfdPaid = cash ? payRfd(ps, periodEnd, net) : BigDecimal.ZERO;
        // Always written (0.00 for bank pay or when no top-up was needed).
        allowances = allowances.add(rfdPaid);
        gross = gross.add(rfdPaid);
        net = net.add(rfdPaid);
        linesToSave.add(line(psId, null, "RFD_ADVANCE", "RFD Advance Paid",
                ComponentType.EARNING, CalcType.FLAT, null, null, rfdPaid, displayOrder));

        ps.setBasic(basicAfterLop);
        ps.setAllowances(allowances);
        ps.setOtAmount(otAmount);
        ps.setGross(gross);
        ps.setDeductions(deductions);
        ps.setTax(tax);
        ps.setEobi(eobi);
        ps.setPessiEmployer(pessi);
        ps.setNet(net);
        lineRepo.saveAll(linesToSave);
        return ps;
    }

    /**
     * Monthly permanent-advance installment. Outstanding = sum of the
     * employee's APPROVED advances minus every deduction already in the
     * ledger; the installment is the latest APPROVED advance's
     * {@code monthly_deduction}, capped at that outstanding balance — or 0
     * when HR waived this month ({@code permanent_advance_waivers}). Writes
     * one (SALARY, run id) ledger row in the same per-employee tx as the
     * payslip; {@code PayrollRunService.reset} deletes it by that pair.
     */
    private BigDecimal applyPermanentAdvance(PayrollRun run, long employeeId, LocalDate periodEnd) {
        BigDecimal taken = permanentAdvanceRepo.employeeTotalPermanentAdvance(employeeId);
        if (taken == null || taken.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal deducted = permanentAdvanceDeductionRepo.employeeTotalPermanentAdvanceDeduction(employeeId);
        BigDecimal remaining = taken.subtract(deducted);
        if (remaining.signum() <= 0) return BigDecimal.ZERO;

        PermanentAdvance latest = permanentAdvanceRepo
                .findTopByEmployeeIdAndStatusOrderByIdDesc(employeeId, AdvanceStatus.APPROVED);
        if (latest == null || latest.getMonthlyDeduction() == null
                || latest.getMonthlyDeduction().signum() <= 0) return BigDecimal.ZERO;
        boolean waived = permanentAdvanceWaiverRepo.existsByEmployeeIdAndPeriodYearAndPeriodMonth(
                employeeId, run.getPeriodYear(), run.getPeriodMonth());
        BigDecimal amount = waived ? BigDecimal.ZERO : latest.getMonthlyDeduction().min(remaining);

        PermanentAdvanceDeduction d = new PermanentAdvanceDeduction();
        d.setEmployeeId(employeeId);
        d.setSource(com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource.SALARY);
        d.setSourceRefId(run.getId());
        d.setDeductedAmount(amount);
        d.setRemainingBalance(remaining.subtract(amount));
        d.setTransactionDate(periodEnd);
        d.setDescription((waived ? "Deduction waived for salary " : "Deduction from salary ")
                + periodEnd.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + periodEnd.getYear());
        permanentAdvanceDeductionRepo.save(d);
        return amount;
    }

    /**
     * A positive cash net is topped up to the next multiple of 500 and the
     * top-up saved as an RFD row dated the payslip's created date — so the
     * run for the month the salary is computed in deducts it, and
     * {@code PayrollRunService.reset} finds it by that date. Returns the
     * top-up, zero when the net is already a multiple of 500 or not positive.
     */
    private BigDecimal payRfd(Payslip ps, LocalDate periodEnd, BigDecimal net) {
        if (net.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal remainder = net.remainder(RFD_NOTE);
        if (remainder.signum() == 0) return BigDecimal.ZERO;
        BigDecimal amount = RFD_NOTE.subtract(remainder);

        RfdAdvance r = new RfdAdvance();
        r.setEmployeeId(ps.getEmployeeId());
        r.setAmount(amount);
        r.setTransactionState(RfdAdvance.FROM_SALARY
                + periodEnd.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + periodEnd.getYear());
        r.setTransactionDate(ps.getCreatedAt().toLocalDate());
        rfdAdvanceRepo.save(r);
        return amount;
    }

    private static final BigDecimal RFD_NOTE = BigDecimal.valueOf(500);

    private BigDecimal applyHeldReleaseCarryover(long employeeId, long newPayslipId, int displayOrder,
                                                 List<PayslipLine> sink) {
        BigDecimal total = BigDecimal.ZERO;
        for (Payslip held : payslipRepo.findAllByEmployeeIdAndStatus(employeeId, PayslipStatus.RELEASE_PENDING)) {
            String label = "Held Salary Released — " + held.getPayrollRunId();
            sink.add(line(newPayslipId, null, "HELD_SALARY_RELEASE", label,
                    ComponentType.EARNING, CalcType.FLAT, null, null, held.getNet(),
                    displayOrder));
            total = total.add(held.getNet());
        }
        return total;
    }

    private BigDecimal computeOt(BigDecimal basic, BigDecimal otHours) {
        if (otHours == null || otHours.signum() == 0) return BigDecimal.ZERO;
        BigDecimal hourly = basic.divide(props.standardMonthlyHours(), 10, RoundingMode.CEILING);
        return Money.of(hourly).multiply(otHours).amount();
    }

    /**
     * Grade-conditional canteen deduction. Two rates configured on
     * {@link PayrollConfig} — typically the management rate is higher
     * than the supervisor/labor rate.
     * <ul>
     *   <li><b>MANAGEMENT</b> — flat weekly subscription at the
     *       management rate. A single canteen attendance in any 7-day
     *       chunk (from day 1) makes the whole chunk billable:
     *       {@code weeks × managementRate × 7}.</li>
     *   <li><b>SUPERVISOR / LABOR</b> — pay-per-meal at the other rate:
     *       {@code days × otherRate}.</li>
     * </ul>
     * Returns zero when usage is zero, the applicable rate is zero, or
     * the upstream lookup failed (treated as "no canteen activity"
     * rather than failing the run for a non-critical deduction). Grade
     * missing or unknown defaults to LABOR — safer to under-charge
     * management than to over-charge labor.
     */
    private BigDecimal computeCanteen(long employeeId, LocalDate periodStart, LocalDate periodEnd,
                                      EmployeeClient.EmployeeBrief brief,
                                      Map<Long, GradeType> gradeTypes,
                                      PayrollConfig stat,
                                      Map<String, String> identity) {
        GradeType gradeType = brief == null || brief.gradeId() == null ? GradeType.LABOUR
                : gradeTypes.getOrDefault(brief.gradeId(), GradeType.LABOUR);
        BigDecimal rate = gradeType == GradeType.MANAGEMENT
                ? stat.getCanteenPerDayRateManagement()
                : stat.getCanteenPerDayRateOther();
        if (rate == null || rate.signum() <= 0) return BigDecimal.ZERO;
        AttendanceClient.CanteenUsage usage =
                attendance.canteenUsage(employeeId, periodStart, periodEnd, identity);
        if (usage.days() == 0) return BigDecimal.ZERO;
        if (gradeType == GradeType.MANAGEMENT) {
            return Money.of(rate)
                    .multiply(BigDecimal.valueOf(7L * usage.weeks()))
                    .amount();
        }
        return Money.of(rate)
                .multiply(BigDecimal.valueOf(usage.days()))
                .amount();
    }

    private PayslipLine line(long payslipId, Long componentId, String code, String name,
                             ComponentType type, CalcType calcType, BigDecimal rate, BigDecimal days,
                             BigDecimal amount, int displayOrder) {
        PayslipLine l = new PayslipLine();
        l.setPayslipId(payslipId);
        l.setComponentId(componentId);
        l.setCode(code);
        l.setName(name);
        l.setType(type);
        l.setCalcType(calcType);
        l.setRate(rate);
        l.setDays(days);
        l.setAmount(amount);
        l.setDisplayOrder(displayOrder);
        return l;
    }

}

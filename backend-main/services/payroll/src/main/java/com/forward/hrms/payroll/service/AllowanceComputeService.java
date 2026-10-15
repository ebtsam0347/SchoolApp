package com.forward.hrms.payroll.service;


import com.forward.hrms.commons.batch.BatchOutcomes;
import com.forward.hrms.commons.batch.BatchRunner;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.response.GradeDto;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.GradeType;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.*;
import com.forward.hrms.payroll.enums.AllowanceRunStatus;
import com.forward.hrms.payroll.enums.AllowanceTransactionStatus;
import com.forward.hrms.payroll.events.AllowanceEvents;
import com.forward.hrms.payroll.repository.*;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AllowanceComputeService {

    private final SalaryComponentRepository componentCatalogRepo;
    private final EmployeePayrollSettingsRepository settingsRepo;
    private final PayrollConfigService payrollConfig;
    private final EmployeeClient employees;
    private final Outbox outbox;
    private final EmployeeSalaryComponentRepository componentLineRepo;
    private final BatchRunner batch;
    private final AllowanceRunRepository runRepo;
    private final AuditEmitter audit;
    private final PayslipRepository payslipRepo;
    private final AllowanceTransactionRepository allowanceTransactionRepository;


    private static final int PROGRESS_CHUNK = 25;

    public record ComputeResult(int employeeCount, BigDecimal totalNet,
                                int skippedCount, List<SkippedEmployee> skipped) {

        public ComputeResult(int employeeCount, BigDecimal totalNet) {
            this(employeeCount, totalNet, 0, List.of());
        }
    }


    public record SkippedEmployee(long employeeId, String errorCode, String message) {}

    public ComputeResult computeRun(AllowanceRun run) {
        Map<Long, EmployeeClient.EmployeeBrief> activeEmployees =
                employees.getAllActiveEmployeesForAllowanceCalculation(null);
        if (activeEmployees == null || activeEmployees.isEmpty()) {
            return new ComputeResult(0, BigDecimal.ZERO);
        }
        return computeRun(run, null, activeEmployees, null);
    }


    public ComputeResult computeRun(AllowanceRun run,
                                    Map<String, String> identity,
                                    Map<Long, EmployeeClient.EmployeeBrief> activeEmployees,
                                    BiConsumer<Integer, Integer> onProgress) {

        LocalDate periodStart = LocalDate.of(run.getAllowanceYear(), run.getAllowanceMonth(), 1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());

        // Bulk-load the catalog once per run instead of per line. With ~20
        // active components × ~500 employees that was 10k findById round
        // trips per run. The catalog is small (dozens of rows), held in
        // memory for the run's duration, and rebuilt next run.
        Map<Long, SalaryComponent> catalog = componentCatalogRepo.findAll()
                        .stream()
                        .collect(Collectors.toMap(SalaryComponent::getId, Function.identity()));

        // Compute allowances only for ACTIVE employees, resolved up-front from employee-service.
        List<EmployeePayrollSettings> all = activeEmployees.isEmpty()
                ? List.of()
                : settingsRepo.findAllByEmployeeIdIn(activeEmployees.keySet());

        //get all grades at once
        List<GradeDto> allGrades = employees.getAllGrades(identity);

        // Filter out employees who already have a payslip on this run from a
        // partial earlier attempt — saga-retry safety.
        Set<Long> alreadyComputedIds = allowanceTransactionRepository.findByEmployeeIdsAndAllowanceRunId(run.getId());
        List<EmployeePayrollSettings> remaining = alreadyComputedIds.isEmpty() ? all
                : all.stream()
                .filter(s -> !alreadyComputedIds.contains(s.getEmployeeId()))
                .toList();

        // §7.payroll #12 — per-employee REQUIRES_NEW so a single bad row
        // doesn't roll back the whole run. Chunked so we can pulse
        // progress_count between chunks (the FE's "X / total" needs at
        // least one update per ~25 rows to feel live; the writes are
        // negligible cost vs. the compute itself).
        List<BatchOutcomes.Success<EmployeePayrollSettings, AllowanceTransaction>> allOk = new ArrayList<>();
        List<BatchOutcomes.Failure<EmployeePayrollSettings>> allFailed = new ArrayList<>();
        int processed = alreadyComputedIds.size();
        for(int i = 0; i < remaining.size(); i += PROGRESS_CHUNK){
            List<EmployeePayrollSettings> chunk = remaining.subList(i, Math.min(i + PROGRESS_CHUNK, remaining.size()));
            BatchOutcomes<EmployeePayrollSettings, AllowanceTransaction> outcomes = batch.runWithPartial(chunk,
                    settings -> computeOne(run, settings, periodStart, periodEnd, catalog,
                            activeEmployees, identity, allGrades));
            allOk.addAll(outcomes.succeeded());
            allFailed.addAll(outcomes.failed());
            processed += chunk.size();
            if (onProgress != null) onProgress.accept(processed, allFailed.size());
        }

        int count = 0;
        BigDecimal totalNet = BigDecimal.ZERO;
        for (BatchOutcomes.Success<EmployeePayrollSettings, AllowanceTransaction> ok : allOk) {
            count++;
            totalNet = totalNet.add(ok.result().getAmount());
        }

        List<AllowanceComputeService.SkippedEmployee> skipped = new ArrayList<>(allFailed.size());
        for (BatchOutcomes.Failure<EmployeePayrollSettings> f : allFailed) {
            skipped.add(new AllowanceComputeService.SkippedEmployee(f.item().getEmployeeId(), f.errorCode(), f.message()));
            log.warn("compute failed for employee {} in run {}: {} ({})",
                    f.item().getEmployeeId(), run.getId(), f.message(), f.errorCode());
        }
//        persistSkips(run.getId(), skipped, briefs);

        return new ComputeResult(count, totalNet, skipped.size(), skipped);

    }

    @Transactional
    public ComputeResult runComputeStep(long runId, Map<String, String> identity) {
        AllowanceRun run = runRepo.findById(runId).orElseThrow(
                () -> new IllegalArgumentException("Allowance run id " + runId + " does not exist")
        );
        if(run.getStatus() == AllowanceRunStatus.COMPUTED){
            return new ComputeResult(0, BigDecimal.ZERO);
        }

        BiConsumer<Integer, Integer> pulse = (processed, skipped) -> batch.runInNewTxVoid(() ->
                runRepo.findById(runId).ifPresent(r -> {
                    r.setProgressCount(processed);
                    r.setSkippedCount(skipped);
                }));
        Map<Long, EmployeeClient.EmployeeBrief> activeEmployees =
                employees.getAllActiveEmployeesForAllowanceCalculation(identity);
        if (activeEmployees == null || activeEmployees.isEmpty()) {
            log.info("Skipping allowance compute for runId={} because no active employees were found", runId);
            run.setStatus(AllowanceRunStatus.DRAFT);
            run.setComputedAt(null);
            run.setSkippedCount(0);
            run.setProgressCount(0);
            audit.emit("allowance_run", String.valueOf(runId),
                    "allowance_run_compute_skipped_no_active_employees", null,
                    Map.of("reason", "no_active_employees"), true);
            return new ComputeResult(0, BigDecimal.ZERO);
        }
        AllowanceComputeService.ComputeResult result = computeRun(run, identity, activeEmployees, pulse);
        run.setStatus(AllowanceRunStatus.COMPUTED);
        run.setComputedAt(LocalDateTime.now());
        run.setSkippedCount(result.skippedCount());
        run.setProgressCount(run.getTotalCount() == 0 ? result.employeeCount() + result.skippedCount()
                : run.getTotalCount());

        outbox.publish(AllowanceEvents.TOPIC, "allowance_run:" + runId,
                AllowanceEvents.RUN_COMPUTED,
                new AllowanceEvents.RunComputed(runId, run.getFiscalYearCode(),
                        result.employeeCount(), result.totalNet()));
        Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("employeeCount", result.employeeCount());
        auditAfter.put("totalNet", result.totalNet());
        if (result.skippedCount() > 0) {
            auditAfter.put("skippedCount", result.skippedCount());
            auditAfter.put("skipped", 0);
        }
        audit.emit("allowance_run", String.valueOf(runId), "run_computed", null, auditAfter, true);
        return result;
    }

    private AllowanceTransaction computeOne(
            AllowanceRun run,
            EmployeePayrollSettings settings,
            LocalDate periodStart, LocalDate periodEnd,
            Map<Long, SalaryComponent> catalog,
            Map<Long, EmployeeClient.EmployeeBrief> briefs,
            Map<String, String> identity,
            List<GradeDto> allGrades
    ) {
        long employeeId = settings.getEmployeeId();
        EmployeeClient.EmployeeBrief brief = briefs.get(employeeId);
        if (brief == null) {
            throw new IllegalStateException("active employee brief not found for employee " + employeeId);
        }
        if (brief.employmentStatus() != EmploymentStatus.ACTIVE) {
            throw new IllegalStateException("employee " + employeeId + " is not ACTIVE");
        }
        long gradeId = brief.gradeId();
        String employeeCode = brief.employeeCode();

        List<EmployeeSalaryComponent> activeLines = componentLineRepo.findByEmployeeIdAndEffectiveToIsNull(employeeId);

        List<AllowanceTransaction> linesToSave = new ArrayList<>();
        for (EmployeeSalaryComponent line : activeLines) {
            SalaryComponent comp = catalog.get(line.getComponentId());
            if (comp == null) {
                continue;
            }

            if (comp.isCalculateWithSalary()) {
                continue;
            }
            String gradeName = allGrades.stream()
                    .filter(g -> g.id() == gradeId)
                    .map(GradeDto::name)
                    .findFirst()
                    .orElse("Unknown");
            linesToSave.add(line(employeeId, employeeCode, gradeName, line.getId(), line.getAmount(), run));
        }

        List<AllowanceTransaction> saved = allowanceTransactionRepository.saveAll(linesToSave);

        if (saved.isEmpty()) {
            throw new IllegalStateException("No allowance transactions generated for employee " + employeeId);
        }

        return saved.getFirst();
    }

    private AllowanceTransaction line(
            long employeeId,
            String employeeCode,
            String gradeName,
            long componentId,
            BigDecimal amount,
            AllowanceRun run
    ) {

        AllowanceTransaction allowance = new AllowanceTransaction();
        allowance.setAllowanceRunId(run.getId());
        allowance.setEmployeeId(employeeId);
        allowance.setEmployeeCode(employeeCode);
        allowance.setAllowanceYear(run.getAllowanceYear());
        allowance.setAllowanceMonth(run.getAllowanceMonth());
        allowance.setFiscalYearCode(run.getFiscalYearCode());
        allowance.setGrade(gradeName);
        allowance.setEmployeeSalaryComponentId(componentId);
        allowance.setAmount(amount);
        allowance.setStatus(AllowanceTransactionStatus.DRAFT);

        return allowance;
    }
}

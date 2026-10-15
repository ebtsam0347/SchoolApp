package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.batch.BatchOutcomes;
import com.forward.hrms.commons.batch.BatchRunner;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.*;
import com.forward.hrms.payroll.enums.*;
import com.forward.hrms.payroll.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BonusTransactionComputeService {

    private static final int PROGRESS_CHUNK = 25;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final BonusNoSettingsRepository bonusNoSettingsRepository;
    private final BonusTransactionRepository bonusTransactionRepository;
    private final EmployeePayrollSettingsRepository employeePayrollSettingsRepository;
    private final EmployeeSalaryService employeeSalaryService;
    private final PermanentAdvanceRepository permanentAdvanceRepository;
    private final EmployeeClient employeeClient;
    private final BatchRunner batchRunner;
    private final AuditEmitter audit;
    private final FiscalYearRepository fiscalYearRepository;

    public record ComputeResult(int employeeCount, BigDecimal totalNet, int skippedCount,
                                List<SkippedEmployee> skipped) {}

    public record SkippedEmployee(long employeeId, String errorCode, String message) {}
    private record BonusComputeRow(long employeeId,
                                   EmployeeClient.EmployeeBrief brief,
                                   EmployeePayrollSettings payrollSettings) {}

    public ComputeResult compute(BonusNoSettings settings,
                                 Map<Long, EmployeeClient.EmployeeBrief> activeEmployees) {
        FiscalYear currentFiscalYear = fiscalYearRepository.findFiscalYearByCode(settings.getFiscalYearCode());
        Set<Long> activeEmployeeIds = activeEmployees.keySet();
        List<EmployeePayrollSettings> allSettings = activeEmployeeIds.isEmpty()
                ? List.of()
                : employeePayrollSettingsRepository.findAllByEmployeeIdIn(activeEmployeeIds);
        Map<Long, EmployeePayrollSettings> settingsByEmployeeId = new LinkedHashMap<>();
        for (EmployeePayrollSettings row : allSettings) {
            settingsByEmployeeId.put(row.getEmployeeId(), row);
        }

        Set<Long> alreadyComputed = bonusTransactionRepository.findEmployeeIdsByFiscalYearCodeAndBonusNo(
                settings.getFiscalYearCode(), settings.getBonusNo());
        List<BonusComputeRow> remaining = new ArrayList<>();
        for (Map.Entry<Long, EmployeeClient.EmployeeBrief> active : activeEmployees.entrySet()) {
            long employeeId = active.getKey();
            if (alreadyComputed.contains(employeeId)) {
                continue;
            }
            remaining.add(new BonusComputeRow(
                    employeeId,
                    active.getValue(),
                    settingsByEmployeeId.get(employeeId)));
        }

        List<BatchOutcomes.Success<BonusComputeRow, BonusTransaction>> allOk = new ArrayList<>();
        List<BatchOutcomes.Failure<BonusComputeRow>> allFailed = new ArrayList<>();
        for (int i = 0; i < remaining.size(); i += PROGRESS_CHUNK) {
            List<BonusComputeRow> chunk = remaining.subList(i, Math.min(i + PROGRESS_CHUNK, remaining.size()));
            BatchOutcomes<BonusComputeRow, BonusTransaction> outcomes = batchRunner.runWithPartial(
                    chunk, row -> computeOne(settings, row, currentFiscalYear));
            allOk.addAll(outcomes.succeeded());
            allFailed.addAll(outcomes.failed());
        }

        int employeeCount = 0;
        BigDecimal totalNet = BigDecimal.ZERO;
        for (BatchOutcomes.Success<BonusComputeRow, BonusTransaction> ok : allOk) {
            employeeCount++;
            totalNet = totalNet.add(ok.result().getNetAmount());
        }

        List<SkippedEmployee> skipped = allFailed.stream()
                .map(f -> new SkippedEmployee(f.item().employeeId(), f.errorCode(), f.message()))
                .toList();
        return new ComputeResult(employeeCount, totalNet, skipped.size(), skipped);
    }

    @Transactional
    public ComputeResult runComputeStep(long bonusNoSettingsId, Map<String, String> identity) {
        BonusNoSettings settings = bonusNoSettingsRepository.findById(bonusNoSettingsId).orElseThrow(
                () -> new IllegalStateException("bonus_no_settings " + bonusNoSettingsId + " not found"));
        if (settings.getStatus() == BonusNoSettingsStatus.COMPUTED) {
            return new BonusTransactionComputeService.ComputeResult(0, BigDecimal.ZERO, 0, List.of());
        }

        Map<Long, EmployeeClient.EmployeeBrief> activeEmployees =
                employeeClient.getAllActiveEmployeesForBonusCalculation(identity);
        if (activeEmployees == null || activeEmployees.isEmpty()) {
            log.info("Skipping bonus compute for bonusNoSettingsId={} because no active employees were found",
                    bonusNoSettingsId);
            settings.setStatus(BonusNoSettingsStatus.DRAFT);
            bonusNoSettingsRepository.save(settings);
            audit.emit("bonus_no_settings", String.valueOf(settings.getId()),
                    "bonus_transactions_compute_skipped_no_active_employees", null,
                    Map.of(
                            "fiscalYearCode", settings.getFiscalYearCode(),
                            "bonusNo", settings.getBonusNo(),
                            "reason", "no_active_employees"),
                    true);
            return new ComputeResult(0, BigDecimal.ZERO, 0, List.of());
        }

        ComputeResult result = compute(settings, activeEmployees);

        settings.setStatus(BonusNoSettingsStatus.COMPUTED);
        bonusNoSettingsRepository.save(settings);

        Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("fiscalYearCode", settings.getFiscalYearCode());
        auditAfter.put("bonusNo", settings.getBonusNo());
        auditAfter.put("employeeCount", result.employeeCount());
        auditAfter.put("totalNet", result.totalNet());
        if (!result.skipped().isEmpty()) {
            auditAfter.put("skippedCount", result.skippedCount());
            auditAfter.put("skipped", skipSampleForAudit(result.skipped()));
        }
        audit.emit("bonus_no_settings", String.valueOf(settings.getId()),
                "bonus_transactions_computed", null, auditAfter, true);
        return result;
    }

    private BonusTransaction computeOne(BonusNoSettings settings,
                                        BonusComputeRow row,
                                        FiscalYear currentFiscalYear) {
        long employeeId = row.employeeId();
        EmployeeClient.EmployeeBrief brief = row.brief();
        if (brief == null) {
            throw new UnprocessableError(PayrollErrorCode.EMPLOYEE_NOT_FOUND,
                    "employee brief not found for employee " + employeeId);
        }
        EmployeePayrollSettings employeePayrollSettings = row.payrollSettings();
        if (employeePayrollSettings == null) {
            throw new UnprocessableError(PayrollErrorCode.SETTINGS_NOT_FOUND,
                    "no payroll settings for employee, so cannot find payment mode" + employeeId);
        }
        EmploymentStatus employmentStatus = brief.employmentStatus();
        log.info("Employee status for employeeId={}, employmentStatus={}", employeeId, employmentStatus);
        if (employmentStatus != EmploymentStatus.ACTIVE) {
            throw new UnprocessableError(PayrollErrorCode.EMPLOYEE_INACTIVE,
                    "employee " + employeeId + " is not ACTIVE");
        }

        EmployeeSalary salary = employeeSalaryService.currentEntity(employeeId);
        BigDecimal basic = salary.getBasic();
        BigDecimal percentageOfSalary = settings.getPercentageOfSalary();

        LocalDate dateOfJoining = brief.joiningDate();
        if (dateOfJoining == null) {
            throw new UnprocessableError(PayrollErrorCode.EMPLOYEE_NOT_FOUND,
                    "joiningDate missing in employee record for employee " + employeeId);
        }
        LocalDate fiscalYearStart = currentFiscalYear.getStartDate();
        LocalDate fiscalYearEnd = currentFiscalYear.getEndDate();

        long noOfmonths = ChronoUnit.MONTHS.between(
                YearMonth.from(dateOfJoining),
                YearMonth.from(LocalDate.now())
        );

        long noOfDays;
        if(!dateOfJoining.isAfter(fiscalYearStart)){ //if dateOfJoining is not after fiscalYearStart then eligible days will full year
            noOfDays =(ChronoUnit.DAYS.between(fiscalYearStart, fiscalYearEnd.plusDays(1)));

        }else{
            //else DOJ > fiscalYearStart i.e its means after fiscalYearStart then eligible days will be from DOJ to fiscalYearEnd
            noOfDays = ChronoUnit.DAYS.between(dateOfJoining, fiscalYearEnd.plusDays(1));
        }

        BigDecimal salaryPercent = basic
                .divide(BigDecimal.valueOf(100))
                .multiply(percentageOfSalary); //(50000/100)*100

        //divide salary with no.of days in a year i.e. 365 to get per day salary
        BigDecimal numberOfDaysInFiscalYear = BigDecimal.valueOf(
                ChronoUnit.DAYS.between(fiscalYearStart, fiscalYearEnd.plusDays(1)));

        BigDecimal percentageSalaryAmount = salaryPercent
                .divide(numberOfDaysInFiscalYear, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(noOfDays))
                .setScale(0, RoundingMode.HALF_UP); //eligible days(noOfDays) * perDaySalary

        BigDecimal deductionAsLongTermAdvance = BigDecimal.ZERO; //will calculate in next phase
        BigDecimal netAmount = percentageSalaryAmount.subtract(deductionAsLongTermAdvance);

        // No 500-note RFD here: HR enters a bonus's RFD by hand
        // (rfd_advance_added on the transaction plus an rfd_advance row).
        BigDecimal rfdAdvanceAdded = BigDecimal.ZERO;
        if (netAmount.signum() > 0) {
            netAmount = netAmount.setScale(0, RoundingMode.HALF_UP);
        }

        BonusTransactionPaymentMode paymentMode;
        if(employeePayrollSettings.getPaymentMode() == PaymentMode.CASH){
            paymentMode = BonusTransactionPaymentMode.CASH;
        }else{
            paymentMode = BonusTransactionPaymentMode.BANK_TRANSFER;
        }

        BonusTransaction tx = new BonusTransaction();
        tx.setFiscalYearCode(settings.getFiscalYearCode());
        tx.setBonusNo(settings.getBonusNo());
        tx.setEmployeeId(employeeId);
        tx.setEmployeeCode(brief.employeeCode());
        tx.setDateOfJoining(dateOfJoining);
        tx.setBasicSalary(basic);
        tx.setNoOfMonths(noOfmonths);
        tx.setPercentageOfSalary(percentageOfSalary);
        tx.setPercentageSalaryAmount(percentageSalaryAmount);
        tx.setNoOfDays(noOfDays);
        tx.setDeductionAsLongTermAdvance(deductionAsLongTermAdvance);
        tx.setRfdAdvanceAdded(rfdAdvanceAdded);
        tx.setNetAmount(netAmount);
        tx.setBonusTransactionPaymentMode(paymentMode);
        tx.setStatus(BonusTransactionStatus.DRAFT);
        return bonusTransactionRepository.save(tx);
    }

    private static List<Map<String, Object>> skipSampleForAudit(List<SkippedEmployee> all) {
        int max = Math.min(20, all.size());
        List<Map<String, Object>> sample = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            SkippedEmployee s = all.get(i);
            sample.add(Map.of(
                    "employeeId", s.employeeId(),
                    "errorCode", s.errorCode(),
                    "message", s.message()));
        }
        return sample;
    }
}

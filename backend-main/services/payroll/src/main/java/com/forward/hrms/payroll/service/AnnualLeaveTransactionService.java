package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.cache.EmployeeSummaryMapper;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.client.dto.attendace.AnnualLeaveCalcBalanceResponseDto;
import com.forward.hrms.payroll.client.dto.employee.AnnualLeaveEligibleResponseDto;
import com.forward.hrms.payroll.dto.request.*;
import com.forward.hrms.payroll.dto.response.AnnualLeaveCalculateResponse;
import com.forward.hrms.payroll.dto.response.AnnualLeaveEmployeeProcessResult;
import com.forward.hrms.payroll.dto.response.AnnualLeaveLockMonthResponse;
import com.forward.hrms.payroll.dto.response.AnnualLeaveResponse;
import com.forward.hrms.payroll.entity.AnnualLeaveTransaction;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.AnnualLeaveErrorCode;
import com.forward.hrms.payroll.events.AnnualLeaveEvents;
import com.forward.hrms.payroll.repository.*;
import com.forward.hrms.payroll.specification.AnnualLeaveSpecification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class AnnualLeaveTransactionService {
    private final AnnualLeaveTransactionRepository annualLeaveTransactionRepository;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final EmployeeClient employeeClient;
    private final AttendanceClient attendanceClient;
    private final EmployeeSalaryRepository employeeSalaryRepository;
    @PersistenceContext
    private EntityManager em;
    private final EmployeeDisplayCache displayCache;
    private final PermanentAdvanceRepository permanentAdvanceRepository;
    private final PermanentAdvanceDeductionRepository permanentAdvanceDeductionRepository;
    private final TransactionTemplate transactionTemplate;

    public AnnualLeaveTransactionService(
            AnnualLeaveTransactionRepository  annualLeaveTransactionRepository,
            FiscalYearService fiscalYears,
            Outbox outbox,
            AuditEmitter audit, EmployeeClient employees, EmployeeClient employeeClient, AttendanceClient attendanceClient, EmployeeSalaryRepository employeeSalaryRepository, EmployeeDisplayCache displayCache, PermanentAdvanceRepository permanentAdvanceRepository, PermanentAdvanceDeductionRepository permanentAdvanceDeductionRepository,
            PlatformTransactionManager transactionManager
    ) {

        this.annualLeaveTransactionRepository = annualLeaveTransactionRepository;
        this.outbox = outbox;
        this.audit = audit;

        this.employeeClient = employeeClient;
        this.attendanceClient = attendanceClient;
        this.employeeSalaryRepository = employeeSalaryRepository;
        this.displayCache = displayCache;
        this.permanentAdvanceRepository = permanentAdvanceRepository;
        this.permanentAdvanceDeductionRepository = permanentAdvanceDeductionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public AnnualLeaveCalculateResponse calculateAnnualLeave(
            AnnualLeaveCalculateRequest request) {

        int month = request.month();
        int year = request.year();

        LocalDate calculationMonth = LocalDate.of(year, month, 1);

        List<AnnualLeaveEligibleResponseDto> employees = employeeClient.annualLeaveEligibleEmployee(year, month);
        log.info("total employee for calculating annual leave for month: {} and year: {} and employees are: {}", month, year, employees.size());

        if (employees == null || employees.isEmpty()) {
            throw new NotFoundError(AnnualLeaveErrorCode.ANNUAL_LEAVE_NOT_FOUND, "No eligible employees found for annual leave calculation.");
        }

        List<AnnualLeaveEmployeeProcessResult> success = new ArrayList<>();
        List<AnnualLeaveEmployeeProcessResult> failed = new ArrayList<>();
        final int ANNUAL_LEAVE_TYPE_ID = 1;
        for (AnnualLeaveEligibleResponseDto emp : employees) {

            // Salary Check
            Optional<EmployeeSalary> salaryOpt = employeeSalaryRepository.findCurrent(emp.employeeId());

            if (salaryOpt.isEmpty()) {
                failed.add(new AnnualLeaveEmployeeProcessResult(emp.employeeId(), emp.employeeCode(), "Failed", "Current salary not found."));
                continue;
            }

            // Already Calculated?
            if (annualLeaveTransactionRepository.existsByEmployeeIdAndCalculationMonth(emp.employeeId(), calculationMonth)) {
                failed.add(new AnnualLeaveEmployeeProcessResult(emp.employeeId(), emp.employeeCode(), "Failed",
                        "Annual leave has already been calculated for "
                                + calculationMonth.getMonth() + " " + year + "."));
                continue;
            }

            EmployeeSalary salary = salaryOpt.get();

            BigDecimal basicSalary = salary.getBasic();
            BigDecimal perDaySalary = basicSalary.divide(BigDecimal.valueOf(26), 2, RoundingMode.HALF_UP);

            AnnualLeaveCalcBalanceResponseDto annualLeaveBalanceResponse = attendanceClient.annualLeaveBalance(emp.employeeId(), ANNUAL_LEAVE_TYPE_ID, calculationMonth);

            BigDecimal allocated;
            BigDecimal used;
            BigDecimal pending;
            BigDecimal carried;
            BigDecimal available;

            LocalDate cycleStart;
            LocalDate cycleEnd;

            if (annualLeaveBalanceResponse != null) {

                // Balance found in Attendance
                allocated = annualLeaveBalanceResponse.allocated();
                used = annualLeaveBalanceResponse.used();
                pending = annualLeaveBalanceResponse.pending();
                carried = annualLeaveBalanceResponse.carried();
                available = annualLeaveBalanceResponse.available();

                cycleStart = annualLeaveBalanceResponse.cycleStart();
                cycleEnd = annualLeaveBalanceResponse.cycleEnd();

            } else {
                log.info("annual leave balance not found for employee {}", emp.employeeId());
                // No balance found
                // Default annual leave entitlement = 14 days
                allocated = BigDecimal.valueOf(14);
                used = BigDecimal.ZERO;
                pending = BigDecimal.ZERO;
                carried = BigDecimal.ZERO;
                available = BigDecimal.valueOf(14);

                cycleStart = LocalDate.of(calculationMonth.getYear() - 1, emp.joiningDate().getMonthValue(), 1);
                cycleEnd = cycleStart.plusYears(1).minusDays(1);
            }

            BigDecimal leaveAmount = perDaySalary.multiply(BigDecimal.valueOf(allocated.longValue()));
            BigDecimal grossAnnualLeaveAmount = perDaySalary.multiply(BigDecimal.valueOf(available.longValue()));

            //permanent advance calculation start here
            BigDecimal deductionAsLongTermAdvance = BigDecimal.ZERO;
            PermanentAdvance advanceAgainstAnnualLeaveLatest = permanentAdvanceRepository.findApprovedAdvanceAgainstAnnualLeaveLatest(
                    emp.employeeId(), calculationMonth).stream().findFirst().orElse(null);
            BigDecimal advanceTaken = permanentAdvanceRepository.employeeTotalPermanentAdvance(emp.employeeId());
            BigDecimal totalDeduction = permanentAdvanceDeductionRepository
                            .employeeTotalPermanentAdvanceDeduction(emp.employeeId());
            if(advanceAgainstAnnualLeaveLatest != null) {
                if (advanceTaken != null && advanceTaken.compareTo(BigDecimal.ZERO) > 0) {


                    // Already fully paid
                    if (totalDeduction.compareTo(advanceTaken) < 0) {
                        // Amount still remaining to pay
                        BigDecimal balance = advanceTaken.subtract(totalDeduction);

                    }
                }

                else{
                    deductionAsLongTermAdvance = BigDecimal.ZERO;
                }
            }
            else{
                deductionAsLongTermAdvance = BigDecimal.ZERO;
            }
            //permanent advance calculation end here

            BigDecimal netAmount = grossAnnualLeaveAmount.subtract(deductionAsLongTermAdvance);
            AnnualLeaveTransaction transaction = new AnnualLeaveTransaction();

            transaction.setEmployeeId(emp.employeeId());
            transaction.setEmployeeCode(emp.employeeCode());
            transaction.setCalculationMonth(calculationMonth);
            transaction.setBasicSalary(basicSalary);
            transaction.setPerDaySalary(perDaySalary);
            transaction.setCycleStart(cycleStart);
            transaction.setCycleEnd(cycleEnd);
            transaction.setEligibleLeaveDays(allocated.longValue());
            transaction.setAvailedLeaveDays(used.longValue());
            transaction.setPayableLeaveDays(available.longValue());
            transaction.setLeaveAmount(leaveAmount);
            transaction.setGrossAnnualLeaveAmount(grossAnnualLeaveAmount);
            transaction.setDeductionAsLongTermAdvance(deductionAsLongTermAdvance);
            transaction.setNetAmount(netAmount);
            transaction.setPaymentStatus(null);
            transaction.setLockStatus(null);
            transaction.setJoiningDate(emp.joiningDate());
            annualLeaveTransactionRepository.save(transaction);

            success.add(new AnnualLeaveEmployeeProcessResult(emp.employeeId(), emp.employeeCode(), "Success",
                    "Annual leave calculated successfully."));
        }

        // Publish event after successful calculation
        outbox.publish(AnnualLeaveEvents.TOPIC, "annual_leave_calculation:" + year + "-" + month, AnnualLeaveEvents.ANNUAL_LEAVE_CALCULATED,
                new AnnualLeaveEvents.Calculated(year, month, employees.size(), success.size(), failed.size())
        );

        // Audit
        audit.emit("annual_leave_transaction", year + "-" + month, "annual_leave_calculated", null,
                Map.of("year", year, "month", month, "totalEmployees", employees.size(), "successCount", success.size(),
                        "failedCount", failed.size()
                ),true
        );
        return new AnnualLeaveCalculateResponse(year, month, employees.size(), success.size(), failed.size(), success, failed);
    }

    @Transactional
    public PageResponse<AnnualLeaveResponse> getAnnualLeaves(
            AnnualLeaveSearchRequest request,
            Pageable pageable) {

        Page<AnnualLeaveTransaction> transactions =
                annualLeaveTransactionRepository.findAll(
                        AnnualLeaveSpecification.filter(
                                request.year(),
                                request.month(),
                                request.employeeCode(),
                                request.paymentStatus(),
                                request.paymentPending(),
                                request.lockStatus()
                        ),
                        pageable
                );

        // Collect employee IDs from current page
        List<Long> employeeIds = transactions.getContent()
                .stream()
                .map(AnnualLeaveTransaction::getEmployeeId)
                .toList();

        // Resolve all employees in one call
        Map<Long, EmployeeSummary> employees = resolveEmployees(employeeIds);

        // Map transactions to response
        Page<AnnualLeaveResponse> pageResult = transactions.map(
                transaction -> mapToResponse(
                        transaction,
                        employees.get(transaction.getEmployeeId())
                )
        );

        return PageResponse.from(pageResult);
    }

    public AnnualLeaveResponse updatePaymentStatus(Long id, UpdateAnnualLeavePaymentStatusRequest request) {
        AnnualLeaveTransaction transaction = annualLeaveTransactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundError(AnnualLeaveErrorCode.ANNUAL_LEAVE_NOT_FOUND,
                        "Annual leave transaction not found."));
        // Validation: Check if transaction is locked
        if (Boolean.TRUE.equals(transaction.getLockStatus())) {
            throw new ConflictError(AnnualLeaveErrorCode.ANNUAL_LEAVE_INVALID_STATE,
                    "Annual leave transaction is locked and payment status cannot be updated.");
        }

        // Get current payment status
        Boolean currentStatus = transaction.getPaymentStatus();
        Boolean newStatus = request.paymentStatus();
        if (newStatus == null) {
            throw new ConflictError(AnnualLeaveErrorCode.ANNUAL_LEAVE_INVALID_STATE,
                    "paymentStatus must not be null.");
        }

        // Validate the state transition
        if (currentStatus == null) {
            // First time setting status - allow both paid and unpaid
            handleInitialPaymentStatus(transaction, newStatus);
        } else if (currentStatus.equals(newStatus)) {
            // No change needed - idempotent
            log.info("Payment status already set to {} for transaction {}", newStatus, id);
        } else {
            // Toggling between paid and unpaid
            handlePaymentStatusToggle(transaction, currentStatus, newStatus);
        }

        return transactionTemplate.execute(status -> {
            AnnualLeaveTransaction latest = annualLeaveTransactionRepository.findById(id)
                    .orElseThrow(() -> new NotFoundError(AnnualLeaveErrorCode.ANNUAL_LEAVE_NOT_FOUND,
                            "Annual leave transaction not found."));
            if (Boolean.TRUE.equals(latest.getLockStatus())) {
                throw new ConflictError(AnnualLeaveErrorCode.ANNUAL_LEAVE_INVALID_STATE,
                        "Annual leave transaction is locked and payment status cannot be updated.");
            }
            latest.setPaymentStatus(newStatus);
            annualLeaveTransactionRepository.save(latest);

            EmployeeSummary employee = resolveEmployees(List.of(latest.getEmployeeId())).get(latest.getEmployeeId());
            Map<String, Object> before = new HashMap<>();
            before.put("previousStatus", currentStatus);
            Map<String, Object> after = new HashMap<>();
            after.put("paymentStatus", newStatus);
            audit.emit("annual_leave_transaction", String.valueOf(id),
                    newStatus ? "marked_paid" : "marked_unpaid",
                    before,
                    after,
                    true);
            return mapToResponse(latest, employee);
        });
    }

    private void handleInitialPaymentStatus(AnnualLeaveTransaction transaction, Boolean newStatus) {
        if (Boolean.FALSE.equals(newStatus) && transaction.getPayableLeaveDays() != null
                && transaction.getPayableLeaveDays() > 0) {
            // Creating a leave balance entry for the next cycle
            createLeaveBalanceCarry(transaction);
        }
    }

    private void handlePaymentStatusToggle(AnnualLeaveTransaction transaction, Boolean oldStatus, Boolean newStatus) {
        if (Boolean.FALSE.equals(oldStatus) && Boolean.TRUE.equals(newStatus)) {
            // Transitioning from unpaid to paid: delete the carry-over leave balance
            attendanceClient.removeAnnualLeaveUnpaidCarry(transaction.getId());
        } else if (Boolean.TRUE.equals(oldStatus) && Boolean.FALSE.equals(newStatus)) {
            // Transitioning from paid to unpaid: create a new carry-over leave balance
            createLeaveBalanceCarry(transaction);
        }
    }

    private void createLeaveBalanceCarry(AnnualLeaveTransaction transaction) {
        if (transaction.getPayableLeaveDays() == null || transaction.getPayableLeaveDays() <= 0) {
            return;
        }

        final int ANNUAL_LEAVE_TYPE_ID = 1; // Annual leave type ID

        try {
            // Create the leave balance entry with carry-over
            attendanceClient.createAnnualLeaveUnpaidCarry(
                    transaction.getId(),
                    transaction.getEmployeeId(),
                    ANNUAL_LEAVE_TYPE_ID,
                    BigDecimal.valueOf(transaction.getPayableLeaveDays()),
                    transaction.getJoiningDate()
            );

            log.info("Created leave balance carry for employee {} from annual leave unpaid transaction {}",
                    transaction.getEmployeeId(), transaction.getId());

        } catch (Exception ex) {
            log.error("Failed to create leave balance carry for transaction {}: {}", transaction.getId(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * Month-end close: locks every annual-leave transaction of the month in
     * one go. Refused while any row still has no payment decision (null
     * paymentStatus) - locking those would freeze them with neither a payment
     * nor a leave carry-forward. There is no unlock.
     */
    @Transactional
    public AnnualLeaveLockMonthResponse lockMonth(AnnualLeaveLockMonthRequest request) {
        int year = request.year();
        int month = request.month();
        LocalDate calculationMonth = LocalDate.of(year, month, 1);

        long total = annualLeaveTransactionRepository.countByCalculationMonth(calculationMonth);
        if (total == 0) {
            throw new NotFoundError(AnnualLeaveErrorCode.ANNUAL_LEAVE_NOT_FOUND,
                    "No annual leave transactions found for " + calculationMonth.getMonth() + " " + year + ".");
        }

        long pending = annualLeaveTransactionRepository.countByCalculationMonthAndPaymentStatusIsNull(calculationMonth);
        if (pending > 0) {
            throw new ConflictError(AnnualLeaveErrorCode.ANNUAL_LEAVE_INVALID_STATE,
                    pending + " annual leave transaction" + (pending == 1 ? " has" : "s have")
                            + " no payment status. Mark each as paid or unpaid before locking "
                            + calculationMonth.getMonth() + " " + year + ".",
                    Map.of("pendingCount", pending));
        }

        long alreadyLocked = annualLeaveTransactionRepository.countByCalculationMonthAndLockStatusTrue(calculationMonth);
        if (alreadyLocked == total) {
            throw new ConflictError(AnnualLeaveErrorCode.ANNUAL_LEAVE_INVALID_STATE,
                    "Annual leave for " + calculationMonth.getMonth() + " " + year + " is already locked.");
        }

        int locked = annualLeaveTransactionRepository.lockMonth(calculationMonth);

        audit.emit("annual_leave_transaction", year + "-" + month, "annual_leave_month_locked", null,
                Map.of("year", year, "month", month, "lockedCount", locked, "totalCount", total), true);
        return new AnnualLeaveLockMonthResponse(year, month, locked);
    }

    private Map<Long, EmployeeSummary> resolveEmployees(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Set<Long> distinct = new LinkedHashSet<>(ids);
        Map<Long, EmployeeDisplay> hits = displayCache.getMany(distinct);
        Map<Long, EmployeeSummary> out = new HashMap<>(hits.size());
        for (var en : hits.entrySet()) out.put(en.getKey(), EmployeeSummaryMapper.toSummary(en.getValue()));
        return out;
    }

    private AnnualLeaveResponse mapToResponse(
            AnnualLeaveTransaction t,
            EmployeeSummary employee) {

        return new AnnualLeaveResponse(
                t.getId(),
                t.getEmployeeId(),
                employee,
                t.getEmployeeCode(),
                t.getCalculationMonth(),
                t.getBasicSalary(),
                t.getPerDaySalary(),
                t.getCycleStart(),
                t.getCycleEnd(),
                t.getEligibleLeaveDays(),
                t.getAvailedLeaveDays(),
                t.getPayableLeaveDays(),
                t.getLeaveAmount(),
                t.getGrossAnnualLeaveAmount(),
                t.getDeductionAsLongTermAdvance(),
                t.getNetAmount(),
                t.getPaymentStatus(),
                t.getLockStatus(),
                t.getJoiningDate()
        );
    }
}

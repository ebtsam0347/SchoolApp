package com.forward.hrms.payroll.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.cache.EmployeeSummaryMapper;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.saga.SagaContext;
import com.forward.hrms.commons.saga.SagaService;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.dto.request.CreateAllowanceRunRequest;
import com.forward.hrms.payroll.dto.response.*;
import com.forward.hrms.payroll.entity.*;
import com.forward.hrms.payroll.enums.*;
import com.forward.hrms.payroll.events.AllowanceEvents;
import com.forward.hrms.payroll.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AllowanceRunService {
    private final AllowanceRunRepository runs;
    private final AllowanceTransactionRepository allowanceTransactionRepository;
    private final EmployeeSalaryComponentRepository employeeSalaryComponentRepository;
    private final PayrollRunSkipRepository skips;
    private final FiscalYearService fiscalYears;
    private final PayrollComputeService compute;
    private final AttendanceClient attendance;
    private final SagaService sagaService;
    private final ObjectMapper sagaJson;
    private final Outbox outbox;
    private final AuditEmitter audit;
    @PersistenceContext
    private EntityManager em;
    private final EmployeeDisplayCache displayCache;

    public AllowanceRunService(
            AllowanceRunRepository runs,
            AllowanceTransactionRepository allowanceTransactionRepository, EmployeeSalaryComponentRepository employeeSalaryComponentRepository,
            PayrollRunSkipRepository skips,
            FiscalYearService fiscalYears,
            PayrollComputeService compute,
            AttendanceClient attendance,
            SagaService sagaService,
            @Qualifier("sagaObjectMapper") ObjectMapper sagaJson,
            Outbox outbox,
            AuditEmitter audit, EmployeeDisplayCache displayCache
    ) {
        this.runs = runs;
        this.allowanceTransactionRepository = allowanceTransactionRepository;
        this.employeeSalaryComponentRepository = employeeSalaryComponentRepository;
        this.skips = skips;
        this.fiscalYears = fiscalYears;
        this.compute = compute;
        this.attendance = attendance;
        this.sagaService = sagaService;
        this.sagaJson = sagaJson;
        this.outbox = outbox;
        this.audit = audit;

        this.displayCache = displayCache;
    }

    @Transactional
    public AllowanceRunDto create(CreateAllowanceRunRequest req) {
        if (runs.existsByAllowanceYearAndAllowanceMonth(req.year(), req.month())) {
            throw new ConflictError(AllowanceErrorCode.ALLOWANCE_RUN_DUPLICATE,
                    "a run already exists for " + req.year() + "-" +
                            String.format("%02d", req.month()));
        }

        java.time.YearMonth requested = java.time.YearMonth.of(req.year(), req.month());
        java.time.YearMonth currentPlusOne = java.time.YearMonth.now().plusMonths(1);
        if (requested.isAfter(currentPlusOne)) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    AllowanceErrorCode.INVALID_PERIOD,
                    "period " + requested + " is more than one month in the future");
        }
        FiscalYear fy = fiscalYears.forPeriod(req.year(), req.month());
        AllowanceRun r = new AllowanceRun();
        r.setFiscalYearCode(fy.getCode());
        r.setAllowanceYear(req.year());
        r.setAllowanceMonth(req.month());
        r.setStatus(AllowanceRunStatus.DRAFT);
        r = runs.save(r);

        outbox.publish(AllowanceEvents.TOPIC, "allowance_run:" + r.getId(),
                AllowanceEvents.RUN_CREATED,
                new AllowanceEvents.RunCreated(r.getId(), fy.getCode(), r.getAllowanceYear(), r.getAllowanceMonth()));
        audit.emit("allowance_run", String.valueOf(r.getId()), "run_created", null,
                Map.of("fiscalYear", fy.getCode(),
                        "year", r.getAllowanceYear(), "month", r.getAllowanceMonth()),
                true);
        return AllowanceRunDtoMapper.of(r);
    }

    @Transactional(readOnly = true)
    public AllowanceRunDto get(long id) {
        return AllowanceRunDtoMapper.of(load(id));
    }

    @Transactional(readOnly = true)
    public List<AllowanceRunDto> listByFiscalYear(String fiscalYearCode) {
        return runs.findAllByFiscalYearCodeOrderByAllowanceYearDescAllowanceMonthDesc(fiscalYearCode)
                .stream().map(AllowanceRunDtoMapper::of).toList();
    }

    @Transactional
    public PageResponse<AllowanceTransactionDto> getTransactions(
            Long runId,
            AllowanceTransactionStatus status,
            String employeeCode,
            Pageable pageable) {

        // Get paginated data
        List<Object[]> rows = allowanceTransactionRepository.findTransactionsWithFilters(
                runId,
                status != null ? status.name() : null,
                employeeCode,
                pageable
        );

        // Get total count of distinct employees
        long totalEmployees = allowanceTransactionRepository.countDistinctEmployees(
                runId,
                status != null ? status.name() : null,
                employeeCode
        );

        // Group by employee
        Map<Long, AllowanceTransactionDto> employeeMap = new LinkedHashMap<>();

        for (Object[] row : rows) {
            Long employeeId = ((Number) row[0]).longValue();
            String empCode = (String) row[1];
            String grade = (String) row[2];
            String fiscalYearCode = (String) row[3];
            Integer allowanceYear = (Integer) row[4];
            Integer allowanceMonth = (Integer) row[5];
            Long componentId = ((Number) row[6]).longValue();
            String componentCode = (String) row[7];
            String componentName = (String) row[8];
            BigDecimal amount = BigDecimal.valueOf(((Number) row[9]).longValue());
            String componentStatus = (String) row[10];

            AllowanceTransactionDto dto = employeeMap.get(employeeId);
            if (dto == null) {
                List<Long> employeeIdList = new ArrayList<>();
                employeeIdList.add(employeeId);
                Map<Long, EmployeeSummary> employee = resolveEmployees(employeeIdList);
                dto = AllowanceTransactionDto.builder()
                        .employeeId(employeeId)
                        .employeeCode(empCode)
                        .employee(employee.get(employeeId))
                        .grade(grade)
                        .fiscalYearCode(fiscalYearCode)
                        .allowanceYear(allowanceYear)
                        .allowanceMonth(allowanceMonth)
                        .components(new ArrayList<>())
                        .build();
                employeeMap.put(employeeId, dto);
            }

            ComponentDto component = ComponentDto.builder()
                    .componentId(componentId)
                    .code(componentCode)
                    .name(componentName)
                    .amount(amount)
                    .status(componentStatus)
                    .build();

            dto.getComponents().add(component);
        }

        // Create Page with correct total count
        List<AllowanceTransactionDto> content = new ArrayList<>(employeeMap.values());
        Page<AllowanceTransactionDto> page = new PageImpl<>(content, pageable, totalEmployees);

        return PageResponse.from(page);
    }

    private Map<Long, EmployeeSummary> resolveEmployees(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Set<Long> distinct = new LinkedHashSet<>(ids);
        Map<Long, EmployeeDisplay> hits = displayCache.getMany(distinct);
        Map<Long, EmployeeSummary> out = new HashMap<>(hits.size());
        for (var en : hits.entrySet()) out.put(en.getKey(), EmployeeSummaryMapper.toSummary(en.getValue()));
        return out;
    }

    @Transactional(readOnly = true)
    public AllowanceRunTotalsDto totals(long runId, AllowanceTransactionStatus status, String q){
        load(runId);
        String trimmed = (q == null || q.isEmpty()) ? null : q.trim();

        var result = allowanceTransactionRepository.totalsForRun(runId, status, trimmed);
        return com.forward.hrms.payroll.dto.response.AllowanceRunTotalsDtoMapper.of(runId, result);
    }

    @Transactional(readOnly = true)
    public AllowanceRunSummaryDto summary(long runId){
        AllowanceRun r = load(runId);
        var result = allowanceTransactionRepository.totalsForRun(runId, null, null);
        AllowanceRunTotalsDto totals = AllowanceRunTotalsDtoMapper.of(runId, result);
        int success = Math.max(0, r.getProgressCount() - r.getSkippedCount());
        return new AllowanceRunSummaryDto(r.getId(), r.getFiscalYearCode(), r.getAllowanceYear(), r.getAllowanceMonth(), r.getStatus(),
                r.getComputedAt(), r.getPostedAt(), r.getTotalCount(), r.getProgressCount(), r.getSkippedCount(), success, totals);
    }

    @Transactional
    public AllowanceRunDto compute(long id) {
        AllowanceRun r = load(id);
        if (r.getStatus() == AllowanceRunStatus.COMPUTING) {
            startComputeSaga(r);   // idempotent — returns the existing saga row
            return AllowanceRunDtoMapper.of(r);
        }
//        if (r.getStatus() != AllowanceRunStatus.LOCKED) {
//            throw new ConflictError(AllowanceErrorCode.ALLOWANCE_RUN_INVALID_STATE,
//                    "compute requires LOCKED (current: " + r.getStatus() + ")");
//        }
        r.setStatus(AllowanceRunStatus.COMPUTING);
        r.setProgressCount(0);
        Long count = ((Number) em.createNativeQuery("""
            SELECT COUNT(DISTINCT e.id)
            FROM employee.employees e
            JOIN payroll.employee_salary_components esc
                ON esc.employee_id = e.id
            JOIN payroll.salary_components sc
                ON sc.id = esc.component_id
            WHERE e.employment_status = 'ACTIVE'
              AND esc.effective_to IS NULL
              AND sc.calculate_with_salary = FALSE;
            """)
                .getSingleResult())
                .longValue();
        r.setTotalCount(Math.toIntExact(count));
        startComputeSaga(r);
        return AllowanceRunDtoMapper.of(r);
    }

    private void startComputeSaga(AllowanceRun r) {
        ObjectNode payload = sagaJson.createObjectNode();
        payload.put("runId", r.getId());
        payload.put("fiscalYearCode", r.getFiscalYearCode());
        SagaContext.writeIdentity(payload, EmployeeClient.currentRequestIdentity());
        sagaService.start(AllowanceRunComputeSaga.NAME, "allowance_run:" + r.getId(), payload);
    }

    @Transactional
    public AllowanceRunDto approve(long id) {
     AllowanceRun r = load(id);
     if(r.getStatus() != AllowanceRunStatus.COMPUTED){
         throw new ConflictError(AllowanceErrorCode.ALLOWANCE_RUN_INVALID_STATE, "Approve requires COMPUTED (current: " + r.getStatus() + ")");
     }
     r.setStatus(AllowanceRunStatus.APPROVED);
     r.setApprovedAt(LocalDateTime.now());

     allowanceTransactionRepository.updateStatusByAllowanceRunId(id, AllowanceTransactionStatus.PAID);

     outbox.publish(AllowanceEvents.TOPIC, "allowance_run_approved" + id,
             AllowanceEvents.RUN_APPROVED, new AllowanceEvents.RunApproved(id, r.getFiscalYearCode()));
     audit.emit("allowance_run", String.valueOf(id), "allowance_approve", null,
             Map.of("approve_at", r.getApprovedAt().toString()),false);
     return AllowanceRunDtoMapper.of(r);
    }

    AllowanceRun load(long id) {
        return runs.findById(id).orElseThrow(
                () -> new NotFoundError(AllowanceErrorCode.ALLOWANCE_RUN_NOT_FOUND, "allowance run not found"));
    }

    @Transactional
    public AllowanceRunDto reset(long id){
        AllowanceRun r = load(id);
        if(r.getStatus() != AllowanceRunStatus.COMPUTED){
            throw new ConflictError(AllowanceErrorCode.ALLOWANCE_INVALID_STATE,
                    "reset requires COMPUTED (current: " + r.getStatus() + ")");
        }
        int deletedSalaryComponent = allowanceTransactionRepository.deleteAllByAllowanceRunId(id);
        int droppedSagaService = sagaService.clear(AllowanceRunComputeSaga.NAME, "allowance_run:" + id);
//        r.setStatus(AllowanceRunStatus.LOCKED);
        r.setStatus(AllowanceRunStatus.DRAFT);
        r.setComputedAt(null);
        r.setSkippedCount(0);
        r.setProgressCount(0);
        r.setTotalCount(0);

        String by = CurrentUserHolder.get().map(CurrentUser::username).orElse("system");
        outbox.publish(AllowanceEvents.TOPIC, "allowance_run:" + id,
                AllowanceEvents.RUN_RESET,
                new AllowanceEvents.RunReset(id, r.getFiscalYearCode(), deletedSalaryComponent, by));
        audit.emit("allowance_run", String.valueOf(id), "run_reset", null,
                Map.of("deletedAllowanceTransaction", deletedSalaryComponent,
                        "droppedSaga", droppedSagaService, "by", by), true);
        return AllowanceRunDtoMapper.of(r);
    }

    @Transactional
    public AllowanceRunDto cancel(long id){
        AllowanceRun r = load(id);
        if(r.getStatus() != AllowanceRunStatus.DRAFT && r.getStatus() != AllowanceRunStatus.LOCKED){
            throw new ConflictError(AllowanceErrorCode.ALLOWANCE_RUN_INVALID_STATE,
                    "cancel requires DRAFT or LOCKED (current: " + r.getStatus() + ")");
        }
        r.setStatus(AllowanceRunStatus.CANCELLED);
        return AllowanceRunDtoMapper.of(r);
    }

}

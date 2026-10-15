package com.forward.hrms.employee.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.commons.saga.SagaRepository;
import com.forward.hrms.commons.saga.SagaState;
import com.forward.hrms.employee.dto.response.ActiveProbationDto;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.EmployeeDtoMapper;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmploymentContract;
import com.forward.hrms.employee.enums.EssProvisioningStatus;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.EmploymentContractRepository;

import lombok.RequiredArgsConstructor;

/**
 * Builds enriched {@link EmployeeDto} instances for read paths — signs the
 * profile-picture URL, batches the direct-reports count, and (when asked)
 * resolves the active-probation summary. Mutation paths keep using
 * {@code EmployeeDtoMapper.of(e)} directly; this assembler is for endpoints that
 * the UI reads from.
 */
@Service
@RequiredArgsConstructor
public class EmployeeDtoAssembler {

    private final EmployeeRepository employees;
    private final EmploymentContractRepository contracts;
    private final StorageUrlService urls;
    private final SagaRepository sagas;

    @Transactional(readOnly = true)
    public EmployeeDto toDto(Employee e) {
        return toDto(e, false);
    }

    /**
     * Enrich a single employee. When {@code includeProbation} is true we also resolve
     * the active PROBATION contract — used by the per-employee detail endpoint and
     * the BFF's per-employee composite.
     */
    @Transactional(readOnly = true)
    public EmployeeDto toDto(Employee e, boolean includeProbation) {
        String url = urls.signAvatarGet(e.getProfilePictureKey());
        Integer reports = countDirectReports(List.of(e.getId())).getOrDefault(e.getId(), 0);
        ActiveProbationDto probation = null;
        if (includeProbation) {
            List<EmploymentContract> active = contracts.findActiveProbationsByEmployeeIds(List.of(e.getId()));
            if (!active.isEmpty()) probation = toActive(active.get(0));
        }
        EssProvisioningStatus essStatus = resolveEssStatus(List.of(e)).get(e.getId());
        Long managerUserId = e.getManagerId() == null
                ? null
                : resolveManagerUserIds(List.of(e.getManagerId())).get(e.getManagerId());
        return EmployeeDtoMapper.of(e, url, essStatus, reports, probation, managerUserId);
    }

    /** Enrich a page of employees in batch. */
    @Transactional(readOnly = true)
    public List<EmployeeDto> toDtos(List<Employee> rows, boolean includeProbation) {
        if (rows.isEmpty()) return List.of();
        List<Long> ids = rows.stream().map(Employee::getId).toList();
        Map<Long, Integer> reportCounts = countDirectReports(ids);
        Map<Long, ActiveProbationDto> probationByEmployee = includeProbation
                ? loadProbationsByEmployee(ids)
                : Map.of();
        Map<Long, EssProvisioningStatus> essByEmployee = resolveEssStatus(rows);
        Set<Long> managerIds = new HashSet<>();
        for (Employee e : rows) if (e.getManagerId() != null) managerIds.add(e.getManagerId());
        Map<Long, Long> managerUserIds = resolveManagerUserIds(managerIds);

        return rows.stream()
                .map(e -> EmployeeDtoMapper.of(
                        e,
                        urls.signAvatarGet(e.getProfilePictureKey()),
                        essByEmployee.get(e.getId()),
                        reportCounts.getOrDefault(e.getId(), 0),
                        probationByEmployee.get(e.getId()),
                        e.getManagerId() == null ? null : managerUserIds.get(e.getManagerId())))
                .toList();
    }

    /**
     * Batched lookup: manager employee id → manager user id. Empty input
     * returns an empty map; managers with no linked auth user are absent.
     */
    private Map<Long, Long> resolveManagerUserIds(Collection<Long> managerEmployeeIds) {
        if (managerEmployeeIds == null || managerEmployeeIds.isEmpty()) return Map.of();
        Map<Long, Long> out = new HashMap<>();
        for (Object[] row : employees.findUserIdsByEmployeeIds(managerEmployeeIds)) {
            out.put((Long) row[0], (Long) row[1]);
        }
        return out;
    }

    /**
     * Single batched query for the saga rows backing the page's employees.
     * Status precedence: a linked auth user pins COMPLETED regardless of the
     * saga (handles the rare race where the FE reads between the auth call
     * succeeding and the saga marking Done). Otherwise mirror the saga row,
     * defaulting to NOT_REQUESTED for employees with no saga history.
     */
    private Map<Long, EssProvisioningStatus> resolveEssStatus(List<Employee> rows) {
        Map<String, Long> keyToId = new HashMap<>(rows.size());
        for (Employee e : rows) keyToId.put("employee:" + e.getId(), e.getId());

        Map<Long, EssProvisioningStatus> sagaStatus = new HashMap<>();
        if (!keyToId.isEmpty()) {
            for (SagaState s : sagas.findByNameAndCorrelationKeyIn(OnboardingSaga.NAME, keyToId.keySet())) {
                Long id = keyToId.get(s.getCorrelationKey());
                if (id != null) sagaStatus.put(id, mapSagaStatus(s.getStatus()));
            }
        }

        Map<Long, EssProvisioningStatus> out = new HashMap<>(rows.size());
        for (Employee e : rows) {
            if (e.getUserId() != null) {
                out.put(e.getId(), EssProvisioningStatus.COMPLETED);
            } else {
                out.put(e.getId(), sagaStatus.getOrDefault(e.getId(), EssProvisioningStatus.NOT_REQUESTED));
            }
        }
        return out;
    }

    private static EssProvisioningStatus mapSagaStatus(SagaState.Status s) {
        return switch (s) {
            case RUNNING   -> EssProvisioningStatus.RUNNING;
            case COMPLETED -> EssProvisioningStatus.COMPLETED;
            case FAILED    -> EssProvisioningStatus.FAILED;
        };
    }

    private Map<Long, Integer> countDirectReports(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Set<Long> distinct = new HashSet<>(ids);
        Map<Long, Integer> out = new HashMap<>();
        for (Long id : distinct) out.put(id, 0);
        for (Object[] row : employees.countDirectReportsByManagerIds(distinct)) {
            Long managerId = (Long) row[0];
            Long count = (Long) row[1];
            if (managerId != null) out.put(managerId, count.intValue());
        }
        return out;
    }

    private Map<Long, ActiveProbationDto> loadProbationsByEmployee(List<Long> ids) {
        Map<Long, ActiveProbationDto> out = new HashMap<>();
        for (EmploymentContract c : contracts.findActiveProbationsByEmployeeIds(ids)) {
            out.put(c.getEmployeeId(), toActive(c));
        }
        return out;
    }

    private static ActiveProbationDto toActive(EmploymentContract c) {
        LocalDate today = LocalDate.now();
        return ActiveProbationDto.of(c.getId(), c.getEndDate(), today, c.isExtended());
    }
}

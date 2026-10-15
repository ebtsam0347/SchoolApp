package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.AddEmployeeDisabilityRequest;
import com.forward.hrms.employee.dto.request.UpdateEmployeeDisabilityRequest;
import com.forward.hrms.employee.dto.response.EmployeeDisabilityDto;
import com.forward.hrms.employee.entity.Disability;
import com.forward.hrms.employee.entity.EmployeeDisability;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.DisabilityRepository;
import com.forward.hrms.employee.repository.EmployeeDisabilityRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmployeeDisabilityService {

    private final EmployeeDisabilityRepository repo;
    private final DisabilityRepository disabilities;
    private final EmployeeRepository employees;
    private final DisabilityService disabilityService;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<EmployeeDisabilityDto> list(long employeeId) {
        ensureEmployee(employeeId);
        List<EmployeeDisability> rows = repo.findAllByEmployeeIdOrderByIdAsc(employeeId);
        Map<Long, Disability> cats = catalogsFor(rows);
        return rows.stream()
                .map(r -> EmployeeDisabilityDto.of(r, cats.get(r.getDisabilityId())))
                .toList();
    }

    @Transactional
    public EmployeeDisabilityDto add(long employeeId, AddEmployeeDisabilityRequest req) {
        ensureEmployee(employeeId);
        Disability cat = disabilityService.requireActive(req.disabilityId());
        if (repo.existsByEmployeeIdAndDisabilityId(employeeId, req.disabilityId())) {
            throw new ConflictError(EmployeeErrorCode.EMPLOYEE_DISABILITY_DUPLICATE,
                    "this disability is already linked to the employee");
        }
        EmployeeDisability row = new EmployeeDisability();
        row.setEmployeeId(employeeId);
        row.setDisabilityId(req.disabilityId());
        row.setDescription(req.description());
        row.setSeverity(req.severity());
        row.setDocumentKey(req.documentKey());
        repo.save(row);
        audit.emit("employee_disability", String.valueOf(row.getId()), "added",
                null, Map.of("employeeId", employeeId, "disabilityId", req.disabilityId(),
                        "disabilityCode", cat.getCode()), false);
        return EmployeeDisabilityDto.of(row, cat);
    }

    @Transactional
    public EmployeeDisabilityDto update(long employeeId, long linkId, UpdateEmployeeDisabilityRequest req) {
        EmployeeDisability row = load(employeeId, linkId);
        if (req.description() != null) row.setDescription(req.description().isBlank() ? null : req.description());
        if (req.severity() != null) row.setSeverity(req.severity());
        if (req.documentKey() != null) row.setDocumentKey(req.documentKey().isBlank() ? null : req.documentKey());
        Disability cat = disabilities.findById(row.getDisabilityId()).orElse(null);
        return EmployeeDisabilityDto.of(row, cat);
    }

    @Transactional
    public void delete(long employeeId, long linkId) {
        EmployeeDisability row = load(employeeId, linkId);
        repo.delete(row);
        audit.emit("employee_disability", String.valueOf(linkId), "deleted",
                null, Map.of("employeeId", employeeId, "disabilityId", row.getDisabilityId()), false);
    }

    private EmployeeDisability load(long employeeId, long linkId) {
        return EmployeeOwnedRows.requireOwned(repo, EmployeeDisability::getEmployeeId,
                employeeId, linkId, EmployeeErrorCode.EMPLOYEE_DISABILITY_NOT_FOUND,
                "employee disability");
    }

    private Map<Long, Disability> catalogsFor(List<EmployeeDisability> rows) {
        if (rows.isEmpty()) return Map.of();
        List<Long> ids = rows.stream().map(EmployeeDisability::getDisabilityId).distinct().toList();
        return disabilities.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Disability::getId, d -> d));
    }

    private void ensureEmployee(long employeeId) {
        EmployeeOwnedRows.ensureEmployee(employees, employeeId);
    }
}

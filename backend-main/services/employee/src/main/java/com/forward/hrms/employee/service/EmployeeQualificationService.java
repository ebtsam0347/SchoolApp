package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.AddEmployeeQualificationRequest;
import com.forward.hrms.employee.dto.request.UpdateEmployeeQualificationRequest;
import com.forward.hrms.employee.dto.response.EmployeeQualificationDto;
import com.forward.hrms.employee.dto.response.QualificationEmployeeDto;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeQualification;
import com.forward.hrms.employee.entity.Qualification;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.EmployeeQualificationRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.QualificationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EmployeeQualificationService {

    private final EmployeeQualificationRepository repo;
    private final EmployeeRepository employees;
    private final QualificationRepository qualifications;
    private final StorageUrlService urls;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<EmployeeQualificationDto> list(long employeeId) {
        ensureEmployee(employeeId);
        List<EmployeeQualification> rows =
                repo.findAllByEmployeeIdOrderByYearOfCompletionDescIdAsc(employeeId);
        Map<Long, Qualification> byId = qualsByIds(rows.stream().map(EmployeeQualification::getQualificationId).toList());
        return rows.stream()
                .map(eq -> EmployeeQualificationDto.of(eq,
                        byId.get(eq.getQualificationId()), urls.signGet(eq.getDocumentKey())))
                .toList();
    }

    @Transactional
    public EmployeeQualificationDto add(long employeeId, AddEmployeeQualificationRequest req) {
        ensureEmployee(employeeId);
        Qualification q = qualifications.findById(req.qualificationId())
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.QUALIFICATION_NOT_FOUND, "qualification not found"));
        if (!q.isActive()) {
            throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "qualification is inactive");
        }

        EmployeeQualification eq = new EmployeeQualification();
        eq.setEmployeeId(employeeId);
        eq.setQualificationId(req.qualificationId());
        eq.setInstitution(req.institution());
        eq.setBoardOrUniversity(req.boardOrUniversity());
        eq.setYearOfCompletion(req.yearOfCompletion());
        eq.setGrade(req.grade());
        eq.setDocumentKey(req.documentKey());
        repo.save(eq);

        audit.emit("employee_qualification", String.valueOf(eq.getId()), "added",
                null,
                Map.of("employeeId", employeeId, "qualificationCode", q.getCode(),
                       "institution", req.institution()),
                false);
        return EmployeeQualificationDto.of(eq, q);
    }

    @Transactional
    public EmployeeQualificationDto update(long employeeId, long employeeQualId,
                                           UpdateEmployeeQualificationRequest req) {
        EmployeeQualification eq = load(employeeId, employeeQualId);
        if (req.institution() != null && !req.institution().isBlank()) eq.setInstitution(req.institution());
        if (req.boardOrUniversity() != null) {
            eq.setBoardOrUniversity(req.boardOrUniversity().isBlank() ? null : req.boardOrUniversity());
        }
        if (req.yearOfCompletion() != null) eq.setYearOfCompletion(req.yearOfCompletion());
        if (req.grade() != null) eq.setGrade(req.grade().isBlank() ? null : req.grade());
        if (req.documentKey() != null) eq.setDocumentKey(req.documentKey().isBlank() ? null : req.documentKey());
        Qualification q = qualifications.findById(eq.getQualificationId()).orElse(null);
        audit.emit("employee_qualification", String.valueOf(eq.getId()), "updated",
                null, Map.of("employeeId", employeeId), false);
        return EmployeeQualificationDto.of(eq, q);
    }

    @Transactional
    public void delete(long employeeId, long employeeQualId) {
        EmployeeQualification eq = load(employeeId, employeeQualId);
        repo.delete(eq);
        audit.emit("employee_qualification", String.valueOf(employeeQualId), "removed",
                null, Map.of("employeeId", employeeId, "qualificationId", eq.getQualificationId()), false);
    }

    @Transactional(readOnly = true)
    public List<QualificationEmployeeDto> employeesWithQualification(long qualificationId) {
        if (!qualifications.existsById(qualificationId)) {
            throw new NotFoundError(EmployeeErrorCode.QUALIFICATION_NOT_FOUND, "qualification not found");
        }
        List<EmployeeQualification> rows = repo.findAllByQualificationIdOrderByEmployeeIdAsc(qualificationId);
        Map<Long, Employee> byId = empsByIds(rows.stream().map(EmployeeQualification::getEmployeeId).toList());
        return rows.stream()
                .map(eq -> QualificationEmployeeDto.of(eq, byId.get(eq.getEmployeeId())))
                .toList();
    }

    private EmployeeQualification load(long employeeId, long id) {
        return EmployeeOwnedRows.requireOwned(repo, EmployeeQualification::getEmployeeId,
                employeeId, id, EmployeeErrorCode.EMPLOYEE_QUALIFICATION_NOT_FOUND,
                "employee qualification");
    }

    private Map<Long, Qualification> qualsByIds(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Set<Long> distinct = new HashSet<>(ids);
        Map<Long, Qualification> out = new HashMap<>();
        qualifications.findAllById(distinct).forEach(q -> out.put(q.getId(), q));
        return out;
    }

    private Map<Long, Employee> empsByIds(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Set<Long> distinct = new HashSet<>(ids);
        Map<Long, Employee> out = new HashMap<>();
        employees.findAllById(distinct).forEach(e -> out.put(e.getId(), e));
        return out;
    }

    private void ensureEmployee(long employeeId) {
        EmployeeOwnedRows.ensureEmployee(employees, employeeId);
    }
}

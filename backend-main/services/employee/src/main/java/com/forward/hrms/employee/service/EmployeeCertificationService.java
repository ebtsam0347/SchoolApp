package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.core.error.Violation;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.request.AddCertificationRequest;
import com.forward.hrms.employee.dto.request.UpdateCertificationRequest;
import com.forward.hrms.employee.dto.response.EmployeeCertificationDto;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeCertification;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.EmployeeCertificationRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EmployeeCertificationService {

    private final EmployeeCertificationRepository repo;
    private final EmployeeRepository employees;
    private final StorageUrlService urls;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<EmployeeCertificationDto> list(long employeeId) {
        ensureEmployee(employeeId);
        return repo.findAllByEmployeeIdOrderByIssueDateDescIdAsc(employeeId).stream()
                .map(c -> EmployeeCertificationDto.of(c, urls.signGet(c.getDocumentKey())))
                .toList();
    }

    @Transactional
    public EmployeeCertificationDto add(long employeeId, AddCertificationRequest req) {
        ensureEmployee(employeeId);
        EmployeeCertification c = new EmployeeCertification();
        c.setEmployeeId(employeeId);
        c.setName(req.name());
        c.setIssuingBody(req.issuingBody());
        c.setIssueDate(req.issueDate());
        c.setExpiryDate(req.expiryDate());
        c.setCredentialId(req.credentialId());
        c.setDocumentKey(req.documentKey());
        repo.save(c);
        audit.emit("employee_certification", String.valueOf(c.getId()), "added",
                null,
                Map.of("employeeId", employeeId, "name", c.getName(),
                       "expiryDate", c.getExpiryDate() == null ? "" : c.getExpiryDate().toString()),
                false);
        return EmployeeCertificationDto.of(c);
    }

    @Transactional
    public EmployeeCertificationDto update(long employeeId, long certId, UpdateCertificationRequest req) {
        EmployeeCertification c = load(employeeId, certId);
        if (req.name() != null && !req.name().isBlank()) c.setName(req.name());
        if (req.issuingBody() != null) c.setIssuingBody(req.issuingBody().isBlank() ? null : req.issuingBody());
        if (req.issueDate() != null) c.setIssueDate(req.issueDate());
        if (req.expiryDate() != null) c.setExpiryDate(req.expiryDate());
        if (req.credentialId() != null) c.setCredentialId(req.credentialId().isBlank() ? null : req.credentialId());
        if (req.documentKey() != null) c.setDocumentKey(req.documentKey().isBlank() ? null : req.documentKey());
        audit.emit("employee_certification", String.valueOf(c.getId()), "updated",
                null, Map.of("employeeId", employeeId), false);
        return EmployeeCertificationDto.of(c);
    }

    @Transactional
    public void delete(long employeeId, long certId) {
        EmployeeCertification c = load(employeeId, certId);
        repo.delete(c);
        audit.emit("employee_certification", String.valueOf(certId), "removed",
                null, Map.of("employeeId", employeeId), false);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeCertificationDto> expiringWithin(Integer days,
                                                                 Long departmentId,
                                                                 Long sectionId,
                                                                 Boolean expired,
                                                                 Pageable pageable) {
        LocalDate today = LocalDate.now();
        // expired=true   → only already-expired (expiry <= today); `days` is irrelevant
        // expired=false  → only future (expiry > today); `days` defines the upper bound
        // expired=null   → both; `days` defines the upper bound
        if (!Boolean.TRUE.equals(expired) && days == null) {
            throw new ValidationError("expiringWithin is required unless expired=true",
                    List.of(new Violation("expiringWithin", "required",
                            "must be provided when expired is null or false")));
        }
        LocalDate minExpiry = Boolean.FALSE.equals(expired) ? today.plusDays(1) : null;
        LocalDate hardCap = Boolean.TRUE.equals(expired) ? today : today.plusDays(days);

        var page = repo.findExpiringOnOrBefore(minExpiry, hardCap, departmentId, sectionId, pageable);
        Map<Long, Employee> byId = empsByIds(page.getContent().stream()
                .map(EmployeeCertification::getEmployeeId).toList());
        List<EmployeeCertificationDto> content = page.getContent().stream()
                .map(c -> EmployeeCertificationDto.of(c,
                        byId.get(c.getEmployeeId()), urls.signGet(c.getDocumentKey())))
                .toList();
        return PageResponse.from(page, content);
    }

    private EmployeeCertification load(long employeeId, long id) {
        return EmployeeOwnedRows.requireOwned(repo, EmployeeCertification::getEmployeeId,
                employeeId, id, EmployeeErrorCode.DOCUMENT_NOT_FOUND, "certification");
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

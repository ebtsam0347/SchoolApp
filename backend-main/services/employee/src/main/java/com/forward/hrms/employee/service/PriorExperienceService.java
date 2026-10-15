package com.forward.hrms.employee.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.AddPriorExperienceRequest;
import com.forward.hrms.employee.dto.request.UpdatePriorExperienceRequest;
import com.forward.hrms.employee.dto.response.PriorExperienceDto;
import com.forward.hrms.employee.entity.EmployeePriorExperience;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.EmployeePriorExperienceRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PriorExperienceService {

    private final EmployeePriorExperienceRepository repo;
    private final EmployeeRepository employees;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<PriorExperienceDto> list(long employeeId) {
        ensureEmployee(employeeId);
        return repo.findAllByEmployeeIdOrderByEndDateDescIdAsc(employeeId).stream()
                .map(PriorExperienceDto::of)
                .toList();
    }

    @Transactional
    public PriorExperienceDto add(long employeeId, AddPriorExperienceRequest req) {
        ensureEmployee(employeeId);
        validateDates(req.startDate(), req.endDate());
        EmployeePriorExperience p = new EmployeePriorExperience();
        p.setEmployeeId(employeeId);
        p.setEmployerName(req.employerName());
        p.setDesignation(req.designation());
        p.setStartDate(req.startDate());
        p.setEndDate(req.endDate());
        p.setResponsibilities(req.responsibilities());
        p.setReasonForLeaving(req.reasonForLeaving());
        repo.save(p);
        audit.emit("prior_experience", String.valueOf(p.getId()), "added",
                null, Map.of("employeeId", employeeId, "employer", p.getEmployerName()), false);
        return PriorExperienceDto.of(p);
    }

    @Transactional
    public PriorExperienceDto update(long employeeId, long expId, UpdatePriorExperienceRequest req) {
        EmployeePriorExperience p = load(employeeId, expId);
        if (req.employerName() != null && !req.employerName().isBlank()) p.setEmployerName(req.employerName());
        if (req.designation() != null) p.setDesignation(req.designation().isBlank() ? null : req.designation());
        if (req.startDate() != null) p.setStartDate(req.startDate());
        if (req.endDate() != null) p.setEndDate(req.endDate());
        if (req.responsibilities() != null) {
            p.setResponsibilities(req.responsibilities().isBlank() ? null : req.responsibilities());
        }
        if (req.reasonForLeaving() != null) {
            p.setReasonForLeaving(req.reasonForLeaving().isBlank() ? null : req.reasonForLeaving());
        }
        validateDates(p.getStartDate(), p.getEndDate());
        audit.emit("prior_experience", String.valueOf(p.getId()), "updated",
                null, Map.of("employeeId", employeeId), false);
        return PriorExperienceDto.of(p);
    }

    @Transactional
    public void delete(long employeeId, long expId) {
        EmployeePriorExperience p = load(employeeId, expId);
        repo.delete(p);
        audit.emit("prior_experience", String.valueOf(expId), "removed",
                null, Map.of("employeeId", employeeId), false);
    }

    private EmployeePriorExperience load(long employeeId, long id) {
        return EmployeeOwnedRows.requireOwned(repo, EmployeePriorExperience::getEmployeeId,
                employeeId, id, EmployeeErrorCode.PRIOR_EXPERIENCE_NOT_FOUND, "prior experience");
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED,
                    "endDate must not be before startDate");
        }
    }

    private void ensureEmployee(long employeeId) {
        EmployeeOwnedRows.ensureEmployee(employees, employeeId);
    }
}

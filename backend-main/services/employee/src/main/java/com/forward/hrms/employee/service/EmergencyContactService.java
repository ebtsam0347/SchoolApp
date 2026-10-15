package com.forward.hrms.employee.service;

import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.CreateEmergencyContactRequest;
import com.forward.hrms.employee.dto.request.UpdateEmergencyContactRequest;
import com.forward.hrms.employee.dto.response.EmergencyContactDto;
import com.forward.hrms.employee.entity.EmergencyContact;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.EmergencyContactRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmergencyContactService {

    private final EmergencyContactRepository repo;
    private final EmployeeRepository employees;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<EmergencyContactDto> list(long employeeId) {
        ensureEmployee(employeeId);
        return repo.findAllByEmployeeIdOrderByPrimaryDescIdAsc(employeeId).stream()
                .map(EmergencyContactDto::of)
                .toList();
    }

    @Transactional
    public EmergencyContactDto create(long employeeId, CreateEmergencyContactRequest req) {
        ensureEmployee(employeeId);
        EmergencyContact c = new EmergencyContact();
        c.setEmployeeId(employeeId);
        c.setName(req.name());
        c.setRelation(req.relation());
        c.setPhone(req.phone());
        c.setPrimary(req.primary());
        repo.save(c);
        if (c.isPrimary()) repo.demoteOtherPrimaries(employeeId, c.getId());
        audit.emit("emergency_contact", String.valueOf(c.getId()), "created",
                null,
                Map.of("employeeId", employeeId, "name", c.getName(), "primary", c.isPrimary()),
                false);
        return EmergencyContactDto.of(c);
    }

    @Transactional
    public EmergencyContactDto update(long employeeId, long contactId, UpdateEmergencyContactRequest req) {
        EmergencyContact c = load(employeeId, contactId);
        if (req.name() != null && !req.name().isBlank()) c.setName(req.name());
        if (req.relation() != null) c.setRelation(req.relation().isBlank() ? null : req.relation());
        if (req.phone() != null && !req.phone().isBlank()) c.setPhone(req.phone());
        if (req.primary() != null) {
            c.setPrimary(req.primary());
            if (req.primary()) repo.demoteOtherPrimaries(employeeId, c.getId());
        }
        audit.emit("emergency_contact", String.valueOf(c.getId()), "updated",
                null, Map.of("primary", c.isPrimary()), false);
        return EmergencyContactDto.of(c);
    }

    @Transactional
    public void delete(long employeeId, long contactId) {
        EmergencyContact c = load(employeeId, contactId);
        repo.delete(c);
        audit.emit("emergency_contact", String.valueOf(contactId), "deleted",
                null, Map.of("employeeId", employeeId), false);
    }

    private EmergencyContact load(long employeeId, long contactId) {
        return EmployeeOwnedRows.requireOwned(repo, EmergencyContact::getEmployeeId,
                employeeId, contactId, EmployeeErrorCode.EMERGENCY_CONTACT_NOT_FOUND,
                "emergency contact");
    }

    private void ensureEmployee(long employeeId) {
        EmployeeOwnedRows.ensureEmployee(employees, employeeId);
    }
}

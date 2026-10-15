package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.employee.dto.request.ConfirmEmployeeRequest;
import com.forward.hrms.employee.dto.request.ExtendProbationRequest;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.EmploymentContractDto;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmploymentContract;
import com.forward.hrms.employee.enums.ContractStatus;
import com.forward.hrms.employee.enums.ContractType;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.commons.employeeevents.EmployeeEvents;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.EmploymentContractRepository;
import com.forward.hrms.employee.dto.response.EmployeeDtoMapper;
import com.forward.hrms.employee.dto.response.EmploymentContractDtoMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContractService {

    private static final Set<Integer> EXTENSION_LENGTHS = Set.of(120, 180);

    private final EmployeeRepository employees;
    private final EmploymentContractRepository contracts;
    private final StorageUrlService urls;
    private final Outbox outbox;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<EmploymentContractDto> list(long employeeId) {
        EmployeeOwnedRows.ensureEmployee(employees, employeeId);
        return contracts.findAllByEmployeeIdOrderByStartDateDesc(employeeId).stream()
                .map(c -> EmploymentContractDtoMapper.of(c, urls.signGet(c.getDocumentKey())))
                .toList();
    }

    @Transactional
    public EmployeeDto confirm(long employeeId, ConfirmEmployeeRequest req) {
        Employee e = loadEmployee(employeeId);
        EmploymentContract probation = activeProbationOrThrow(employeeId);

        LocalDate today = LocalDate.now();
        probation.setStatus(ContractStatus.EXPIRED);

        EmploymentContract permanent = new EmploymentContract();
        permanent.setEmployeeId(employeeId);
        permanent.setContractType(ContractType.PERMANENT);
        permanent.setStartDate(today);
        permanent.setEndDate(null);
        permanent.setStatus(ContractStatus.ACTIVE);
        permanent.setProbationDays(null);
        permanent.setExtended(false);
        permanent.setDocumentKey(null);
        contracts.save(permanent);

        e.setConfirmationDate(today);

        outbox.publish(EmployeeEvents.TOPIC, "employee:" + employeeId,
                EmployeeEvents.EMPLOYEE_CONFIRMED,
                new EmployeeEvents.EmployeeConfirmed(
                        employeeId, e.getUserId(), today, probation.getProbationDays()));
        audit.emit("employee", String.valueOf(employeeId), "confirmed",
                Map.of("probationDays", String.valueOf(probation.getProbationDays())),
                Map.of("confirmationDate", today.toString(),
                       "notes", req.notes() == null ? "" : req.notes()),
                true);
        return EmployeeDtoMapper.of(e);
    }

    @Transactional
    public EmployeeDto extendProbation(long employeeId, ExtendProbationRequest req) {
        Employee e = loadEmployee(employeeId);
        EmploymentContract probation = activeProbationOrThrow(employeeId);

        if (probation.isExtended()) {
            throw new ConflictError(EmployeeErrorCode.PROBATION_ALREADY_EXTENDED,
                    "probation has already been extended once");
        }
        if (req.newProbationDays() == null || !EXTENSION_LENGTHS.contains(req.newProbationDays())) {
            throw new UnprocessableError(EmployeeErrorCode.PROBATION_DAYS_INVALID,
                    "newProbationDays must be 120 or 180");
        }
        Integer current = probation.getProbationDays();
        if (current != null && req.newProbationDays() <= current) {
            throw new UnprocessableError(EmployeeErrorCode.PROBATION_DAYS_INVALID,
                    "newProbationDays must be greater than current probation_days");
        }

        int from = current == null ? 0 : current;
        probation.setProbationDays(req.newProbationDays());
        probation.setEndDate(probation.getStartDate().plusDays(req.newProbationDays()));
        probation.setExtended(true);

        outbox.publish(EmployeeEvents.TOPIC, "employee:" + employeeId,
                EmployeeEvents.EMPLOYEE_PROBATION_EXTENDED,
                new EmployeeEvents.EmployeeProbationExtended(
                        employeeId, from, req.newProbationDays(), probation.getEndDate(), req.reason()));
        audit.emit("employee", String.valueOf(employeeId), "probation_extended",
                Map.of("probationDays", String.valueOf(from)),
                Map.of("probationDays", String.valueOf(req.newProbationDays()),
                       "newDueDate", probation.getEndDate().toString(),
                       "reason", req.reason()),
                true);
        return EmployeeDtoMapper.of(e);
    }

    private Employee loadEmployee(long id) {
        return employees.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found"));
    }

    private EmploymentContract activeProbationOrThrow(long employeeId) {
        return contracts.findFirstByEmployeeIdAndContractTypeAndStatus(
                        employeeId, ContractType.PROBATION, ContractStatus.ACTIVE)
                .orElseThrow(() -> new ConflictError(
                        EmployeeErrorCode.CONTRACT_NOT_PROBATION,
                        "no active probation contract — employee already permanent or terminated"));
    }
}

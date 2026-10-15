package com.forward.hrms.employee.service;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.employeeevents.EmployeeEvents;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.employee.dto.request.LiftSuspensionRequest;
import com.forward.hrms.employee.dto.request.SuspendRequest;
import com.forward.hrms.employee.dto.response.SuspensionDto;
import com.forward.hrms.employee.dto.response.SuspensionDtoMapper;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeSuspension;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.ExitStatus;
import com.forward.hrms.employee.enums.SuspensionStatus;
import com.forward.hrms.employee.repository.EmployeeExitRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.EmployeeSuspensionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SuspensionService {

    private static final Set<EmploymentStatus> SUSPENDABLE =
            EnumSet.of(EmploymentStatus.ACTIVE, EmploymentStatus.ON_NOTICE);

    private final EmployeeRepository employees;
    private final EmployeeSuspensionRepository suspensions;
    private final EmployeeExitRepository exits;
    private final Outbox outbox;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<SuspensionDto> list(long employeeId) {
        EmployeeOwnedRows.ensureEmployee(employees, employeeId);
        return suspensions.findAllByEmployeeIdOrderByStartDateDesc(employeeId).stream()
                .map(SuspensionDtoMapper::of)
                .toList();
    }

    @Transactional
    public SuspensionDto suspend(long employeeId, SuspendRequest req) {
        Employee e = load(employeeId);
        if (!SUSPENDABLE.contains(e.getEmploymentStatus())) {
            throw new ConflictError(EmployeeErrorCode.EMPLOYEE_NOT_ACTIVE,
                    "employee must be ACTIVE or ON_NOTICE to be suspended");
        }
        if (suspensions.existsByEmployeeIdAndStatus(employeeId, SuspensionStatus.ACTIVE)) {
            throw new ConflictError(EmployeeErrorCode.SUSPENSION_ALREADY_ACTIVE,
                    "employee already has an active suspension");
        }
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new ConflictError(EmployeeErrorCode.SUSPENSION_NOT_ACTIVE,
                    "endDate must not be before startDate");
        }

        LocalDate today = LocalDate.now();
        if (req.startDate() == null || req.startDate().isAfter(today)) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    EmployeeErrorCode.SUSPENSION_DATE_INVALID,
                    "startDate must be on or before today");
        }

        EmployeeSuspension s = new EmployeeSuspension();
        s.setEmployeeId(employeeId);
        s.setStartDate(req.startDate());
        s.setEndDate(req.endDate());
        s.setReason(req.reason());
        s.setWithPay(Boolean.TRUE.equals(req.withPay()));
        s.setStatus(SuspensionStatus.ACTIVE);
        suspensions.save(s);

        e.setEmploymentStatus(EmploymentStatus.SUSPENDED);

        outbox.publish(EmployeeEvents.TOPIC, "employee:" + employeeId,
                EmployeeEvents.EMPLOYEE_SUSPENDED,
                new EmployeeEvents.EmployeeSuspended(
                        employeeId, e.getUserId(), s.getId(),
                        s.getStartDate(), s.getEndDate(), s.getReason(), s.isWithPay()));
        // Field-level status sync (employee.updated convention) so caches /
        // mirrors that key off employmentStatus — e.g. attendance's nightly
        // close — track the transition without a dedicated consumer.
        outbox.publish(EmployeeEvents.TOPIC, "employee:" + employeeId,
                EmployeeEvents.EMPLOYEE_UPDATED,
                new EmployeeEvents.EmployeeUpdated(employeeId, e.getUserId(),
                        Map.of("employmentStatus", EmploymentStatus.SUSPENDED.name())));
        audit.emit("employee", String.valueOf(employeeId), "suspended",
                null,
                Map.of(
                        "suspensionId", s.getId(),
                        "startDate", s.getStartDate().toString(),
                        "endDate", s.getEndDate() == null ? "" : s.getEndDate().toString(),
                        "withPay", s.isWithPay(),
                        "reason", s.getReason()),
                true);
        return SuspensionDtoMapper.of(s);
    }

    @Transactional
    public SuspensionDto lift(long employeeId, LiftSuspensionRequest req) {
        Employee e = load(employeeId);
        EmployeeSuspension active = suspensions
                .findFirstByEmployeeIdAndStatus(employeeId, SuspensionStatus.ACTIVE)
                .orElseThrow(() -> new ConflictError(
                        EmployeeErrorCode.SUSPENSION_NOT_ACTIVE,
                        "no active suspension for this employee"));

        LocalDate liftedOn = req.liftedOn() == null ? LocalDate.now() : req.liftedOn();
        // §7.employee #35 — liftedOn must not be in the future.
        if (liftedOn.isAfter(LocalDate.now())) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    EmployeeErrorCode.SUSPENSION_DATE_INVALID,
                    "liftedOn cannot be in the future");
        }
        active.setStatus(SuspensionStatus.LIFTED);
        active.setLiftedOn(liftedOn);
        active.setLiftedReason(req.reason());

        // Restore employment status: ON_NOTICE if a PENDING exit row is open,
        // ACTIVE otherwise.
        boolean pendingExit = exits.existsByEmployeeIdAndStatus(employeeId, ExitStatus.PENDING);
        EmploymentStatus restored = pendingExit ? EmploymentStatus.ON_NOTICE : EmploymentStatus.ACTIVE;
        e.setEmploymentStatus(restored);

        outbox.publish(EmployeeEvents.TOPIC, "employee:" + employeeId,
                EmployeeEvents.EMPLOYEE_SUSPENSION_LIFTED,
                new EmployeeEvents.EmployeeSuspensionLifted(
                        employeeId, e.getUserId(), active.getId(),
                        liftedOn, req.reason()));
        // Field-level status sync — see suspend().
        outbox.publish(EmployeeEvents.TOPIC, "employee:" + employeeId,
                EmployeeEvents.EMPLOYEE_UPDATED,
                new EmployeeEvents.EmployeeUpdated(employeeId, e.getUserId(),
                        Map.of("employmentStatus", restored.name())));
        audit.emit("employee", String.valueOf(employeeId), "suspension_lifted",
                null,
                Map.of(
                        "suspensionId", active.getId(),
                        "liftedOn", liftedOn.toString(),
                        "reason", req.reason()),
                true);
        return SuspensionDtoMapper.of(active);
    }

    private Employee load(long id) {
        return employees.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found"));
    }
}

package com.forward.hrms.employee.service;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.time.PakistanFiscalYear;
import com.forward.hrms.commons.employeeevents.EmployeeEvents;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.request.DecideDisciplinaryRequest;
import com.forward.hrms.employee.dto.request.IssueDisciplinaryRequest;
import com.forward.hrms.employee.dto.response.DisciplinaryActionDto;
import com.forward.hrms.employee.dto.response.DisciplinaryActionDtoMapper;
import com.forward.hrms.employee.dto.response.DisciplinaryActionListResponse;
import com.forward.hrms.employee.entity.DisciplinaryAction;
import com.forward.hrms.employee.enums.DisciplinaryCategory;
import com.forward.hrms.employee.enums.DisciplinaryLevel;
import com.forward.hrms.employee.enums.DisciplinaryStatus;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.DisciplinaryActionRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DisciplinaryActionService {

    private final DisciplinaryActionRepository repo;
    private final EmployeeRepository employees;
    private final Outbox outbox;
    private final AuditEmitter audit;

    @Transactional
    public DisciplinaryActionDto issue(long employeeId, IssueDisciplinaryRequest req) {
        if (!employees.existsById(employeeId)) {
            throw new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found");
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        if (req.issuedOn() != null && req.issuedOn().isAfter(today)) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    EmployeeErrorCode.DISCIPLINARY_DATE_INVALID,
                    "issuedOn cannot be in the future");
        }
        if (req.incidentDate() != null && req.incidentDate().isAfter(today)) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    EmployeeErrorCode.DISCIPLINARY_DATE_INVALID,
                    "incidentDate cannot be in the future");
        }
        DisciplinaryAction d = new DisciplinaryAction();
        d.setEmployeeId(employeeId);
        d.setLevel(req.level());
        d.setCategory(req.category());
        d.setIncidentDate(req.incidentDate());
        d.setIssuedOn(req.issuedOn());
        d.setReason(req.reason());
        d.setStatus(DisciplinaryStatus.ISSUED);
        repo.save(d);

        outbox.publish(EmployeeEvents.TOPIC, "disciplinary:" + d.getId(),
                EmployeeEvents.EMPLOYEE_DISCIPLINARY_ISSUED,
                new EmployeeEvents.DisciplinaryIssued(
                        d.getId(), employeeId, req.level().name(), req.category().name(),
                        req.issuedOn(), req.reason()));
        audit.emit("employee", String.valueOf(employeeId), "disciplinary_issued",
                null,
                Map.of(
                        "actionId", d.getId(),
                        "level", req.level().name(),
                        "category", req.category().name(),
                        "issuedOn", req.issuedOn().toString()),
                true);
        return DisciplinaryActionDtoMapper.of(d);
    }

    @Transactional
    public DisciplinaryActionDto decide(long actionId, DecideDisciplinaryRequest req) {
        DisciplinaryAction d = repo.findById(actionId)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.DISCIPLINARY_NOT_FOUND, "disciplinary action not found"));
        if (d.getStatus() != DisciplinaryStatus.ISSUED) {
            throw new ConflictError(EmployeeErrorCode.DISCIPLINARY_ALREADY_DECIDED,
                    "disciplinary action already decided");
        }
        LocalDate closedOn = req.closedOn() == null ? LocalDate.now() : req.closedOn();
        d.setDecision(req.decision());
        d.setDecisionNotes(req.decisionNotes());
        d.setClosedOn(closedOn);
        d.setStatus(DisciplinaryStatus.CLOSED);

        outbox.publish(EmployeeEvents.TOPIC, "disciplinary:" + d.getId(),
                EmployeeEvents.EMPLOYEE_DISCIPLINARY_DECIDED,
                new EmployeeEvents.DisciplinaryDecided(
                        d.getId(), d.getEmployeeId(), d.getLevel().name(),
                        req.decision().name(), closedOn));
        audit.emit("employee", String.valueOf(d.getEmployeeId()), "disciplinary_decided",
                null,
                Map.of(
                        "actionId", d.getId(),
                        "decision", req.decision().name(),
                        "closedOn", closedOn.toString()),
                true);
        return DisciplinaryActionDtoMapper.of(d);
    }

    @Transactional(readOnly = true)
    public DisciplinaryActionListResponse listForEmployee(long employeeId,
                                                          DisciplinaryLevel level,
                                                          DisciplinaryStatus status,
                                                          Integer fyCode) {
        if (!employees.existsById(employeeId)) {
            throw new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found");
        }
        int fy = fyCode != null ? fyCode : PakistanFiscalYear.of(LocalDate.now());
        LocalDate fyStart = PakistanFiscalYear.startOf(fy);
        LocalDate fyEnd = PakistanFiscalYear.endOf(fy);

        List<DisciplinaryAction> rows = repo.listForEmployee(
                employeeId, level, status, null, null);

        Map<DisciplinaryLevel, Long> counts = new EnumMap<>(DisciplinaryLevel.class);
        for (DisciplinaryLevel l : DisciplinaryLevel.values()) counts.put(l, 0L);
        for (Object[] r : repo.countUpheldByLevel(employeeId, fyStart, fyEnd)) {
            counts.put((DisciplinaryLevel) r[0], (Long) r[1]);
        }

        return new DisciplinaryActionListResponse(
                rows.stream().map(DisciplinaryActionDtoMapper::of).toList(),
                fy,
                counts);
    }

    @Transactional(readOnly = true)
    public PageResponse<DisciplinaryActionDto> globalList(DisciplinaryLevel level,
                                                          DisciplinaryCategory category,
                                                          DisciplinaryStatus status,
                                                          LocalDate from,
                                                          LocalDate to,
                                                          Integer fyCode,
                                                          Long employeeId,
                                                          String q,
                                                          Pageable pageable) {
        // fyCode narrows the issuedOn window when the caller supplies it.
        // Explicit from/to win when both are passed.
        if (fyCode != null && from == null && to == null) {
            from = PakistanFiscalYear.startOf(fyCode);
            to = PakistanFiscalYear.endOf(fyCode);
        }
        String trimmed = (q == null || q.isBlank()) ? null : q.trim();
        return PageResponse.from(repo
                .search(level, category, status, from, to, employeeId, trimmed, pageable)
                .map(DisciplinaryActionDtoMapper::of));
    }
}

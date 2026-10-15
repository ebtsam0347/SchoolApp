package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.request.AssignShiftRequest;
import com.forward.hrms.attendance.dto.request.CreateShiftRequest;
import com.forward.hrms.attendance.dto.request.ReorderEntryRequest;
import com.forward.hrms.attendance.dto.response.EmployeeShiftDto;
import com.forward.hrms.attendance.dto.response.ShiftDto;
import com.forward.hrms.attendance.dto.response.ShiftDtoMapper;
import com.forward.hrms.attendance.entity.EmployeeShift;
import com.forward.hrms.attendance.entity.Shift;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.repository.EmployeeShiftRepository;
import com.forward.hrms.attendance.repository.ShiftRepository;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository shifts;
    private final EmployeeShiftRepository assignments;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<ShiftDto> list() {
        return shifts.findAllByActiveTrueOrderByCodeAsc().stream().map(ShiftDtoMapper::of).toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeShiftDto> assignmentsFor(long employeeId) {
        List<EmployeeShift> rows = assignments.findAllByEmployeeIdOrderByEffectiveFromDesc(employeeId);
        if (rows.isEmpty()) return List.of();
        // Bulk-load the shift catalog rows the assignments touch — single read,
        // index, then project. Avoids N+1 on the history list.
        java.util.Set<Long> shiftIds = new java.util.HashSet<>();
        for (EmployeeShift es : rows) shiftIds.add(es.getShiftId());
        java.util.Map<Long, Shift> shiftById = new java.util.HashMap<>();
        for (Shift s : shifts.findAllById(shiftIds)) shiftById.put(s.getId(), s);

        List<EmployeeShiftDto> out = new java.util.ArrayList<>(rows.size());
        for (EmployeeShift es : rows) {
            Shift s = shiftById.get(es.getShiftId());
            out.add(EmployeeShiftDto.of(es,
                    s == null ? null : s.getCode(),
                    s == null ? null : s.getName()));
        }
        return out;
    }

    @Transactional
    public ShiftDto create(CreateShiftRequest req) {
        if (shifts.existsByCode(req.code())) {
            throw new ConflictError(AttendanceErrorCode.SHIFT_CODE_TAKEN, "shift code taken");
        }
        Shift s = new Shift();
        s.setCode(req.code());
        s.setName(req.name());
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());
        s.setBreakStartTime(req.breakStartTime());
        s.setBreakEndTime(req.breakEndTime());
        if (req.graceMinutes() != null) s.setGraceMinutes(req.graceMinutes());
        s.setFridayExtraMinutes(req.fridayExtraMinutes());
        s.setPeriod(req.period());
        s.setActive(true);
        s = shifts.save(s);
        audit.emit("shift", String.valueOf(s.getId()), "created", null, snapshot(s), false);
        return ShiftDtoMapper.of(s);
    }

    @Transactional
    public List<ShiftDto> reorder(List<ReorderEntryRequest> entries) {
        if (entries == null || entries.isEmpty()) return list();
        Set<Long> ids = new HashSet<>();
        for (ReorderEntryRequest e : entries) ids.add(e.id());
        List<Shift> rows = shifts.findAllById(ids);
        if (rows.size() != ids.size()) {
            throw new NotFoundError(AttendanceErrorCode.SHIFT_NOT_FOUND, "one or more shifts not found");
        }
        Map<Long, Shift> byId = new HashMap<>();
        for (Shift s : rows) byId.put(s.getId(), s);
        for (ReorderEntryRequest e : entries) {
            Shift s = byId.get(e.id());
            if (s.getDisplayOrder() == e.displayOrder()) continue;
            Map<String, Object> before = snapshot(s);
            s.setDisplayOrder(e.displayOrder());
            audit.emit("shift", String.valueOf(s.getId()), "reordered", before, snapshot(s), false);
        }
        return list();
    }

    private static Map<String, Object> snapshot(Shift s) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", s.getCode());
        m.put("name", s.getName());
        m.put("startTime", s.getStartTime() == null ? null : s.getStartTime().toString());
        m.put("endTime", s.getEndTime() == null ? null : s.getEndTime().toString());
        m.put("active", s.isActive());
        m.put("displayOrder", s.getDisplayOrder());
        m.put("period", s.getPeriod().name());
        return m;
    }

    @Transactional
    public void assign(long employeeId, AssignShiftRequest req) {
        if (!shifts.existsById(req.shiftId())) {
            throw new NotFoundError(AttendanceErrorCode.SHIFT_NOT_FOUND, "shift not found");
        }
        // §7.attendance #20 — refuse a fixed-range assignment whose
        // effectiveTo is before effectiveFrom. Open-ended (null
        // effectiveTo) is still allowed.
        if (req.effectiveTo() != null && req.effectiveTo().isBefore(req.effectiveFrom())) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    AttendanceErrorCode.SHIFT_ASSIGNMENT_INVALID,
                    "effectiveTo must be on or after effectiveFrom");
        }
        // Refuse a same-day collision up front. DB has unique
        // (employee_id, effective_from); without this the close-and-save
        // below trips the constraint at flush as an opaque 500.
        for (EmployeeShift existing : assignments.findActiveOn(employeeId, req.effectiveFrom())) {
            if (existing.getEffectiveFrom().isEqual(req.effectiveFrom())) {
                throw new com.forward.hrms.commons.core.error.UnprocessableError(
                        AttendanceErrorCode.SHIFT_ASSIGNMENT_INVALID,
                        "an assignment already starts on " + req.effectiveFrom()
                                + "; close or amend it first");
            }
        }
        // Close out any open-ended assignment that overlaps from the same date.
        for (EmployeeShift open : assignments.findActiveOn(employeeId, req.effectiveFrom())) {
            if (open.getEffectiveTo() == null) {
                open.setEffectiveTo(req.effectiveFrom().minusDays(1));
            }
        }
        EmployeeShift es = new EmployeeShift();
        es.setEmployeeId(employeeId);
        es.setShiftId(req.shiftId());
        es.setEffectiveFrom(req.effectiveFrom());
        es.setEffectiveTo(req.effectiveTo());
        es = assignments.save(es);
        audit.emit("employee_shift", String.valueOf(es.getId()), "assigned", null,
                Map.of("employeeId", employeeId,
                        "shiftId", req.shiftId(),
                        "effectiveFrom", req.effectiveFrom().toString(),
                        "effectiveTo", req.effectiveTo() == null ? "" : req.effectiveTo().toString()),
                false);
    }

    // close employee shift method
    @Transactional
    public void closeEmployeeShift(long employeeId, LocalDate effectiveTo) {

        log.info("start Closing employee shift with effectiveTo: {}", effectiveTo);
        Optional<EmployeeShift> maybeAssignment = assignments.findFirstActiveOn(employeeId, effectiveTo);
        log.info("maybeAssignment is {}", maybeAssignment);

        if (maybeAssignment.isEmpty()) {
            // Not every terminated employee has a live shift assignment
            // covering their last working day (never assigned, or the
            // assignment already ended earlier). This must not throw —
            // handleTerminated() runs inside one @Transactional Kafka
            // listener alongside leave/overtime cancellation, device
            // sync, and the attendance-mirror status update. A thrown
            // NotFoundError here rolled back ALL of those too, not just
            // the shift close.
            log.info("closeEmployeeShift: no active shift assignment for employee {} on {} — nothing to close",
                    employeeId, effectiveTo);
            return;
        }

        log.info("maybeAssignment is not empty see it: {}", maybeAssignment.get());
        EmployeeShift assignment = maybeAssignment.get();

        // Prevent invalid range
        if (effectiveTo.isBefore(assignment.getEffectiveFrom())) {
            log.warn("closeEmployeeShift: employee {} effectiveTo {} is before assignment {} effectiveFrom {} — skipping",
                    employeeId, effectiveTo, assignment.getId(), assignment.getEffectiveFrom());
            return;
        }

        // Already closed with same date
        if (Objects.equals(assignment.getEffectiveTo(), effectiveTo)) {
            log.info("Employee shift already closed for same date employee {} on {}", employeeId, effectiveTo);
            return;
        }

        log.info("reached to  employee shift Map string object");
        // Map.of(...) throws NullPointerException on a null value/key —
        // an active assignment being closed is normally open-ended
        // (effectiveTo == null), so this must use a null-tolerant map.
        Map<String, Object> before = new HashMap<>();
        before.put("effectiveFrom", assignment.getEffectiveFrom().toString());
        before.put("effectiveTo", assignment.getEffectiveTo() == null
                ? null
                : assignment.getEffectiveTo().toString());

        assignment.setEffectiveTo(effectiveTo);
        assignment.setUpdatedAt(LocalDateTime.now());
        log.info("closeEmployeeShift: assignment {} effectiveTo set to {}", assignment.getId(), effectiveTo);

        audit.emit(
                "employee_shift",
                String.valueOf(assignment.getId()),
                "closed",
                before,
                Map.of(
                        "employeeId", employeeId,
                        "shiftId", assignment.getShiftId(),
                        "effectiveFrom", assignment.getEffectiveFrom().toString(),
                        "effectiveTo", effectiveTo.toString()),
                false
        );
    }

    /**
     * Reverses {@link #closeEmployeeShift}. Fired when HR rejects a
     * final settlement and {@code employee.reinstated} arrives —
     * reopens the assignment closed at {@code effectiveTo} so the
     * employee has a live shift assignment again. Idempotent: no-op if
     * nothing was closed on that exact date, or if the employee
     * already has an active assignment the day after (already
     * reopened, or a fresh shift was assigned in the meantime — don't
     * blindly reopen the old row and create an overlap).
     */
    @Transactional
    public void reopenEmployeeShift(long employeeId, LocalDate effectiveTo) {

        log.info("start Reopening employee shift closed at effectiveTo: {}", effectiveTo);

        List<EmployeeShift> closed = assignments.findByEmployeeIdAndEffectiveTo(employeeId, effectiveTo);
        if (closed.isEmpty()) {
            log.info("reopenEmployeeShift: no shift assignment closed on {} for employee {} — nothing to reopen",
                    effectiveTo, employeeId);
            return;
        }

        if (assignments.findFirstActiveOn(employeeId, effectiveTo.plusDays(1)).isPresent()) {
            log.info("reopenEmployeeShift: employee {} already has an active assignment after {} — skipping",
                    employeeId, effectiveTo);
            return;
        }

        EmployeeShift assignment = closed.get(0);

        Map<String, Object> before = new HashMap<>();
        before.put("effectiveFrom", assignment.getEffectiveFrom().toString());
        before.put("effectiveTo", effectiveTo.toString());

        assignment.setEffectiveTo(null);
        assignment.setUpdatedAt(LocalDateTime.now());
        log.info("reopenEmployeeShift: assignment {} effectiveTo cleared", assignment.getId());

        audit.emit(
                "employee_shift",
                String.valueOf(assignment.getId()),
                "reopened",
                before,
                Map.of(
                        "employeeId", employeeId,
                        "shiftId", assignment.getShiftId(),
                        "effectiveFrom", assignment.getEffectiveFrom().toString()),
                false
        );
    }
}

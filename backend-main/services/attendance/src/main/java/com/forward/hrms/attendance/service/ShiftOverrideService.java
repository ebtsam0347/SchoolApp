package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.request.CreateShiftOverrideRequest;
import com.forward.hrms.attendance.dto.request.UpdateShiftOverrideRequest;
import com.forward.hrms.attendance.dto.response.ShiftOverrideDto;
import com.forward.hrms.attendance.entity.AttendanceShiftOverride;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.events.AttendanceEvents;
import com.forward.hrms.attendance.repository.AttendanceShiftOverrideRepository;
import com.forward.hrms.attendance.dto.response.ShiftOverrideDtoMapper;
import com.forward.hrms.commons.batch.BatchOutcomes;
import com.forward.hrms.commons.batch.BatchRunner;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operator-driven shift overrides. The service is the single
 * recompute-of-truth on insert / update / delete: it rewrites the
 * {@code attendance_records.shift_*} snapshot for every date in the
 * affected range and re-derives late / early / worked / OT minutes
 * against the new snapshot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftOverrideService {

    private final AttendanceShiftOverrideRepository overrides;
    private final AttendanceRecomputeService recompute;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final BatchRunner batch;

    @Transactional(readOnly = true)
    public List<ShiftOverrideDto> listForDate(LocalDate date) {
        return overrides.findAllCovering(date).stream().map(ShiftOverrideDtoMapper::of).toList();
    }

    @Transactional(readOnly = true)
    public List<ShiftOverrideDto> listForEmployee(long employeeId, LocalDate from, LocalDate to) {
        return overrides.findInRange(employeeId, from, to).stream().map(ShiftOverrideDtoMapper::of).toList();
    }

    /**
     * §7.attendance #17 — per-employee partial. Each employee runs in
     * its own {@code REQUIRES_NEW} transaction via {@link BatchRunner};
     * a single overlap (or any other per-row error) leaves the rest of
     * the batch committed.
     */
    public List<ShiftOverrideDto> create(CreateShiftOverrideRequest req) {
        if (req.toDate().isBefore(req.fromDate())) {
            throw new UnprocessableError(AttendanceErrorCode.OVERRIDE_RANGE_INVALID,
                    "to_date must be on or after from_date");
        }
        // §7.attendance #18 — empty employeeIds used to silently return [].
        // That hides operator typos. Reject with 422.
        if (req.employeeIds() == null || req.employeeIds().isEmpty()) {
            throw new UnprocessableError(AttendanceErrorCode.EMPLOYEES_REQUIRED,
                    "employeeIds is required and cannot be empty");
        }
        // §7.attendance #19 — shiftStartTime == shiftEndTime is a 0-minute
        // shift, which silently breaks recompute. shiftStartTime > shiftEndTime
        // is the explicit cross-midnight intent (see expectedMinutes in
        // AttendanceRecomputeService) and is allowed.
        if (req.shiftStartTime() != null && req.shiftEndTime() != null
                && req.shiftStartTime().equals(req.shiftEndTime())) {
            throw new UnprocessableError(AttendanceErrorCode.SHIFT_TIMES_INVALID,
                    "shiftStartTime and shiftEndTime cannot be equal");
        }
        BatchOutcomes<Long, AttendanceShiftOverride> outcomes = batch.runWithPartial(req.employeeIds(), employeeId -> {
            if (!overrides.findOverlapping(employeeId, req.fromDate(), req.toDate(), null).isEmpty()) {
                throw new ConflictError(AttendanceErrorCode.OVERRIDE_OVERLAP,
                        "override overlaps existing one for employee " + employeeId);
            }
            AttendanceShiftOverride o = new AttendanceShiftOverride();
            o.setEmployeeId(employeeId);
            o.setFromDate(req.fromDate());
            o.setToDate(req.toDate());
            o.setShiftStartTime(req.shiftStartTime());
            o.setShiftEndTime(req.shiftEndTime());
            o.setShiftBreakStartTime(req.shiftBreakStartTime());
            o.setShiftBreakEndTime(req.shiftBreakEndTime());
            o.setShiftGraceMinutes(req.shiftGraceMinutes());
            o.setReason(req.reason());
            AttendanceShiftOverride saved = overrides.save(o);

            recompute.rewriteSnapshotForRange(employeeId, req.fromDate(), req.toDate());
            outbox.publish(AttendanceEvents.TOPIC, "employee:" + employeeId,
                    AttendanceEvents.SHIFT_OVERRIDDEN,
                    new AttendanceEvents.ShiftOverridden(
                            employeeId, req.fromDate(), req.toDate(),
                            req.shiftStartTime(), req.shiftEndTime(),
                            req.shiftBreakStartTime(), req.shiftBreakEndTime(),
                            req.shiftGraceMinutes(),
                            req.reason(), "APPLIED"));
            audit.emit("shift_override", String.valueOf(saved.getId()), "applied",
                    null, snapshot(saved), true);
            return saved;
        });
        for (var f : outcomes.failed()) {
            log.warn("shift override create: employee {} failed code={} msg={}",
                    f.item(), f.errorCode(), f.message());
        }
        return outcomes.succeeded().stream().map(s -> ShiftOverrideDtoMapper.of(s.result())).toList();
    }

    @Transactional
    public ShiftOverrideDto update(long id, UpdateShiftOverrideRequest req) {
        AttendanceShiftOverride existing = overrides.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.OVERRIDE_NOT_FOUND, "override not found"));
        Map<String, Object> before = snapshot(existing);
        LocalDate oldFrom = existing.getFromDate();
        LocalDate oldTo = existing.getToDate();
        LocalDate newFrom = req.fromDate() != null ? req.fromDate() : oldFrom;
        LocalDate newTo = req.toDate() != null ? req.toDate() : oldTo;

        if (newTo.isBefore(newFrom)) {
            throw new UnprocessableError(AttendanceErrorCode.OVERRIDE_RANGE_INVALID,
                    "to_date must be on or after from_date");
        }
        if ((req.fromDate() != null || req.toDate() != null)
                && !overrides.findOverlapping(existing.getEmployeeId(), newFrom, newTo, id).isEmpty()) {
            throw new ConflictError(AttendanceErrorCode.OVERRIDE_OVERLAP,
                    "amended range overlaps another override");
        }

        existing.setFromDate(newFrom);
        existing.setToDate(newTo);
        if (req.shiftStartTime() != null) existing.setShiftStartTime(req.shiftStartTime());
        if (req.shiftEndTime() != null) existing.setShiftEndTime(req.shiftEndTime());
        if (req.shiftBreakStartTime() != null) existing.setShiftBreakStartTime(req.shiftBreakStartTime());
        if (req.shiftBreakEndTime() != null) existing.setShiftBreakEndTime(req.shiftBreakEndTime());
        if (req.shiftGraceMinutes() != null) existing.setShiftGraceMinutes(req.shiftGraceMinutes());
        if (req.reason() != null) existing.setReason(req.reason());

        // Recompute over the union: dates dropping out revert; new dates pick up.
        LocalDate unionFrom = oldFrom.isBefore(newFrom) ? oldFrom : newFrom;
        LocalDate unionTo = oldTo.isAfter(newTo) ? oldTo : newTo;
        recompute.rewriteSnapshotForRange(existing.getEmployeeId(), unionFrom, unionTo);

        outbox.publish(AttendanceEvents.TOPIC, "employee:" + existing.getEmployeeId(),
                AttendanceEvents.SHIFT_OVERRIDDEN,
                new AttendanceEvents.ShiftOverridden(
                        existing.getEmployeeId(), newFrom, newTo,
                        existing.getShiftStartTime(), existing.getShiftEndTime(),
                        existing.getShiftBreakStartTime(), existing.getShiftBreakEndTime(),
                        existing.getShiftGraceMinutes(),
                        existing.getReason(), "AMENDED"));
        audit.emit("shift_override", String.valueOf(id), "amended",
                before, snapshot(existing), true);
        return ShiftOverrideDtoMapper.of(existing);
    }

    @Transactional
    public void delete(long id) {
        AttendanceShiftOverride existing = overrides.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.OVERRIDE_NOT_FOUND, "override not found"));
        Map<String, Object> before = snapshot(existing);
        overrides.delete(existing);
        recompute.rewriteSnapshotForRange(existing.getEmployeeId(),
                existing.getFromDate(), existing.getToDate());
        outbox.publish(AttendanceEvents.TOPIC, "employee:" + existing.getEmployeeId(),
                AttendanceEvents.SHIFT_OVERRIDDEN,
                new AttendanceEvents.ShiftOverridden(
                        existing.getEmployeeId(), existing.getFromDate(), existing.getToDate(),
                        existing.getShiftStartTime(), existing.getShiftEndTime(),
                        existing.getShiftBreakStartTime(), existing.getShiftBreakEndTime(),
                        existing.getShiftGraceMinutes(),
                        existing.getReason(), "REVERTED"));
        audit.emit("shift_override", String.valueOf(id), "deleted", before, null, true);
    }

    private static Map<String, Object> snapshot(AttendanceShiftOverride o) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", o.getId());
        out.put("employeeId", o.getEmployeeId());
        out.put("fromDate", o.getFromDate().toString());
        out.put("toDate", o.getToDate().toString());
        out.put("shiftStartTime", o.getShiftStartTime() == null ? null : o.getShiftStartTime().toString());
        out.put("shiftEndTime", o.getShiftEndTime() == null ? null : o.getShiftEndTime().toString());
        out.put("shiftBreakStartTime", o.getShiftBreakStartTime() == null ? null : o.getShiftBreakStartTime().toString());
        out.put("shiftBreakEndTime", o.getShiftBreakEndTime() == null ? null : o.getShiftBreakEndTime().toString());
        out.put("shiftGraceMinutes", o.getShiftGraceMinutes());
        out.put("reason", o.getReason());
        return out;
    }
}

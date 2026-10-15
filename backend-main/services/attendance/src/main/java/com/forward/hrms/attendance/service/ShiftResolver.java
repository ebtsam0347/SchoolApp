package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.entity.AttendanceShiftOverride;
import com.forward.hrms.attendance.entity.EmployeeShift;
import com.forward.hrms.attendance.entity.Shift;
import com.forward.hrms.attendance.repository.AttendanceShiftOverrideRepository;
import com.forward.hrms.attendance.repository.EmployeeShiftRepository;
import com.forward.hrms.attendance.repository.ShiftRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Effective-shift resolution for one (employee, date). Precedence:
 * <ol>
 *   <li>{@link AttendanceShiftOverride} covering the date — wins.</li>
 *   <li>{@link EmployeeShift} active on the date, with Friday extra
 *       minutes applied uniformly to {@code endTime} and
 *       {@code breakEndTime} (so break duration and shift duration
 *       both grow by {@code extra}, leaving worked time unchanged).</li>
 *   <li>None — caller treats it as weekly off / holiday / unassigned.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ShiftResolver {

    private final AttendanceShiftOverrideRepository overrides;
    private final EmployeeShiftRepository assignments;
    private final ShiftRepository shifts;

    @Transactional(readOnly = true)
    public Optional<ShiftResolution> resolve(long employeeId, LocalDate on) {
        Optional<AttendanceShiftOverride> override = overrides.findCovering(employeeId, on);
        if (override.isPresent()) {
            AttendanceShiftOverride o = override.get();
            return Optional.of(new ShiftResolution(
                    null, o.getShiftStartTime(), o.getShiftEndTime(),
                    o.getShiftBreakStartTime(), o.getShiftBreakEndTime(),
                    o.getShiftGraceMinutes()));
        }
        return assignments.findFirstActiveOn(employeeId, on)
                .flatMap(es -> shifts.findById(es.getShiftId()))
                .map(s -> applyFriday(s, on));
    }

    /**
     * Resolution that doesn't consult overrides — used when re-stamping
     * the snapshot on an override delete to find the assignment-derived
     * baseline values for that date.
     */
    @Transactional(readOnly = true)
    public Optional<ShiftResolution> resolveAssigned(long employeeId, LocalDate on) {
        return assignments.findFirstActiveOn(employeeId, on)
                .flatMap(es -> shifts.findById(es.getShiftId()))
                .map(s -> applyFriday(s, on));
    }

    private static ShiftResolution applyFriday(Shift s, LocalDate on) {
        Integer extra = s.getFridayExtraMinutes();
        if (on.getDayOfWeek() == DayOfWeek.FRIDAY && extra != null && extra > 0) {
            LocalTime adjustedEnd = s.getEndTime().plusMinutes(extra);
            LocalTime adjustedBreakEnd = s.getBreakEndTime() == null
                    ? null : s.getBreakEndTime().plusMinutes(extra);
            return new ShiftResolution(s.getId(), s.getStartTime(), adjustedEnd,
                    s.getBreakStartTime(), adjustedBreakEnd, s.getGraceMinutes());
        }
        return new ShiftResolution(s.getId(), s.getStartTime(), s.getEndTime(),
                s.getBreakStartTime(), s.getBreakEndTime(), s.getGraceMinutes());
    }
}

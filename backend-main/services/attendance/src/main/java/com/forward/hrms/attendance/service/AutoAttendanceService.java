package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.dto.request.AutoAttendanceRequest;
import com.forward.hrms.attendance.dto.response.AutoAttendanceResponse;
import com.forward.hrms.attendance.entity.AttendancePunch;
import com.forward.hrms.attendance.entity.AttendanceRecord;
import com.forward.hrms.attendance.entity.Holiday;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.enums.PunchDirection;
import com.forward.hrms.attendance.repository.AttendancePunchRepository;
import com.forward.hrms.attendance.repository.HolidayRepository;
import com.forward.hrms.attendance.repository.LeaveRequestDayRepository;
import com.forward.hrms.attendance.repository.projection.ApprovedLeaveDayProjection;
import com.forward.hrms.commons.core.error.UnprocessableError;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Operator auto attendance: fills one employee's attendance over a date
 * range with an IN and an OUT punch per day, in one transaction.
 *
 * <p>Each day's punches are OPERATOR-sourced and pinned IN / OUT (so they
 * become the record's check-in / check-out whatever else is in the log),
 * the day is recomputed once, and the narration lands on the record's
 * notes and on both punches' reason. Lookups are batched over the range —
 * holidays, approved leave and existing punches are one query each.
 *
 * <p>Days are skipped, not failed, when they shouldn't be filled: Sundays
 * and holidays (unless {@code includeOffDays}), full-day approved leave,
 * a locked payroll period, or a day that already has punches (unless
 * {@code replaceExisting}). The response lists each with its reason. Future
 * days are filled too — an operator can enter attendance ahead of time
 * (e.g. outside duty); they read as present before the day arrives.
 *
 * <p>Shift-aware, day by day: each day's in / out default to that day's
 * resolved shift (assignment or override) when the request leaves them out,
 * and a given time is placed on the calendar date the device puller would
 * attribute to that work date — the shift window of
 * {@link PunchService#resolveWorkDate} (opens 2h before the shift start,
 * runs until the next day's opens). So an 18:00-02:00 shift records
 * check-in 9 Oct 18:00 and check-out 10 Oct 02:00 on the 9 Oct record, and
 * a 00:30 shift's 23:50 arrival lands on the evening before. Days without a
 * shift keep the old rule: out at / before in is the next morning.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoAttendanceService {

    /** Keeps one call inside a couple of months — one transaction, ~2 rows per day. */
    static final int MAX_DAYS = 62;

    static final String NARRATION = "Auto attendance applied through operator";

    private final PunchService punchService;
    private final AttendancePunchRepository punches;
    private final AttendanceRecomputeService recompute;
    private final HolidayRepository holidays;
    private final LeaveRequestDayRepository leaveDays;
    private final AttendancePeriodLockService periodLocks;
    private final EmployeeClient employees;
    private final CallerContext caller;
    private final ShiftResolver shifts;

    @Transactional
    public AutoAttendanceResponse apply(AutoAttendanceRequest req) {
        LocalDate from = req.from();
        LocalDate to = req.to();
        if (to.isBefore(from)) {
            throw new UnprocessableError(AttendanceErrorCode.AUTO_ATTENDANCE_RANGE_INVALID,
                    "end date is before start date");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) {
            throw new UnprocessableError(AttendanceErrorCode.AUTO_ATTENDANCE_RANGE_INVALID,
                    "at most " + MAX_DAYS + " days per run; got " + days);
        }
        if (req.inTime() != null && req.inTime().equals(req.outTime())) {
            throw new UnprocessableError(AttendanceErrorCode.AUTO_ATTENDANCE_RANGE_INVALID,
                    "in time and out time are the same");
        }
        boolean includeOffDays = Boolean.TRUE.equals(req.includeOffDays());
        boolean replaceExisting = Boolean.TRUE.equals(req.replaceExisting());

        long employeeId = employees.getById(req.employeeId()).id();
        String operator = caller.requireUser().username();
        String remarks = req.remarks() == null ? "" : req.remarks().trim();
        String narration = NARRATION + " (" + operator + ")" + (remarks.isEmpty() ? "" : " — " + remarks);

        Set<LocalDate> holidayDates = holidays.findAllByHolidayDateBetweenOrderByHolidayDateAsc(from, to)
                .stream().map(Holiday::getHolidayDate).collect(Collectors.toSet());
        Set<LocalDate> leaveDates = leaveDays.approvedDaysForEmployees(List.of(employeeId), from, to)
                .stream().filter(d -> !Boolean.TRUE.equals(d.getHalfDay()))
                .map(ApprovedLeaveDayProjection::getAttendanceDate).collect(Collectors.toSet());
        Set<LocalDate> punchedDates = punches
                .findAllByEmployeeIdAndAttendanceDateBetweenOrderByPunchedAtAsc(employeeId, from, to)
                .stream().map(AttendancePunch::getAttendanceDate).collect(Collectors.toSet());
        Set<YearMonth> lockedMonths = new HashSet<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            if (periodLocks.isLocked(m.getYear(), m.getMonthValue())) lockedMonths.add(m);
        }

        // Each day's shift, plus the day after the range (its window closes the last day's).
        Map<LocalDate, ShiftResolution> shiftByDay = new HashMap<>();
        for (LocalDate d = from; !d.isAfter(to.plusDays(1)); d = d.plusDays(1)) {
            LocalDate day = d;
            shifts.resolve(employeeId, day).ifPresent(s -> shiftByDay.put(day, s));
        }

        List<AutoAttendanceResponse.Applied> applied = new ArrayList<>();
        List<AutoAttendanceResponse.Skipped> skipped = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String reason = null;
            if (lockedMonths.contains(YearMonth.from(d))) reason = "LOCKED";
            else if (leaveDates.contains(d)) reason = "LEAVE";
            else if (!includeOffDays && holidayDates.contains(d)) reason = "HOLIDAY";
            else if (!includeOffDays && d.getDayOfWeek() == DayOfWeek.SUNDAY) reason = "WEEKEND";
            else if (!replaceExisting && punchedDates.contains(d)) reason = "HAS_PUNCHES";
            LocalDateTime[] inOut = reason == null ? place(d, req, shiftByDay) : null;
            if (reason == null && inOut == null) {
                reason = shiftByDay.containsKey(d) ? "OUTSIDE_SHIFT_WINDOW" : "NO_SHIFT";
            }
            if (reason != null) {
                skipped.add(new AutoAttendanceResponse.Skipped(d, reason));
                continue;
            }

            punchService.savePinnedOperatorPunch(employeeId, inOut[0], d, narration, PunchDirection.IN);
            punchService.savePinnedOperatorPunch(employeeId, inOut[1], d, narration, PunchDirection.OUT);
            AttendanceRecord rec = recompute.recompute(employeeId, d);
            rec.setNotes(narration);
            applied.add(new AutoAttendanceResponse.Applied(d, inOut[0], inOut[1]));
        }
        log.info("auto attendance employee={} {}..{} applied={} skipped={} by={}",
                employeeId, from, to, applied.size(), skipped.size(), operator);
        return new AutoAttendanceResponse(applied, skipped, narration);
    }

    /**
     * Work date {@code d}'s check-in / check-out instants, or null when they
     * can't be placed: no time given and no shift to default from, a time
     * outside the day's shift window, or an out not after the in.
     */
    static LocalDateTime[] place(LocalDate d, AutoAttendanceRequest req,
                                 Map<LocalDate, ShiftResolution> shiftByDay) {
        ShiftResolution shift = shiftByDay.get(d);
        LocalTime inTime = req.inTime() != null ? req.inTime() : shift == null ? null : shift.startTime();
        LocalTime outTime = req.outTime() != null ? req.outTime() : shift == null ? null : shift.endTime();
        if (inTime == null || outTime == null) return null;

        LocalDateTime in;
        LocalDateTime out;
        if (shift == null) {
            in = d.atTime(inTime);
            out = (outTime.isAfter(inTime) ? d : d.plusDays(1)).atTime(outTime);
        } else {
            LocalDateTime opens = PunchService.opensAt(d, shift);
            ShiftResolution next = shiftByDay.get(d.plusDays(1));
            LocalDateTime closes = next == null ? opens.plusDays(1) : PunchService.opensAt(d.plusDays(1), next);
            if (!closes.isAfter(opens)) closes = opens.plusDays(1);
            in = PunchService.instantInWindow(d, inTime, opens, closes);
            out = PunchService.instantInWindow(d, outTime, opens, closes);
        }
        if (in == null || out == null || !out.isAfter(in)) return null;
        return new LocalDateTime[] {in, out};
    }
}

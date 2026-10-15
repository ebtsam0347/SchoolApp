package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.entity.AttendancePunch;
import com.forward.hrms.attendance.entity.AttendanceRecord;
import com.forward.hrms.attendance.entity.OvertimeRequest;
import com.forward.hrms.attendance.enums.PunchDirection;
import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.enums.OvertimeStatus;
import com.forward.hrms.attendance.enums.OvertimeType;
import com.forward.hrms.attendance.repository.AttendancePunchRepository;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;
import com.forward.hrms.attendance.repository.HolidayRepository;
import com.forward.hrms.attendance.repository.LeaveRequestDayRepository;
import com.forward.hrms.attendance.repository.OvertimeRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Computes / re-computes the {@link AttendanceRecord} for one
 * (employee, date). Pulled out of {@code PunchService} so it can be
 * reused from punch ingest, override edits, regularization approvals,
 * and the nightly job.
 *
 * <p>Stateful contract:
 * <ul>
 *   <li>Reads the day's punches, derives check_in (min, null when the first
 *       punch is within 2h of shift end) / check_out (max, null with single
 *       punch or a double tap at check-in).</li>
 *   <li>Stamps the shift snapshot when the row is first created — never
 *       again, except on operator override changes (callers re-stamp by
 *       passing {@code rewriteSnapshot=true}).</li>
 *   <li>Computes late / early / worked minutes against the snapshot.</li>
 *   <li>Computes the OT minute buckets, gated on an APPROVED
 *       {@code overtime_requests} row.</li>
 *   <li>Resolves the day's status (PRESENT / ABSENT / WEEKEND / HOLIDAY /
 *       LEAVE / HALF_DAY).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceRecomputeService {

    /** NORMAL overtime is the first two hours past shift end; anything later is EXTRA. */
    private static final int NORMAL_OT_MAX_MINUTES = 120;

    /**
     * A device punch this close after the check-in is the same tap repeated
     * (the employee re-scanned to be sure), not a check-out.
     */
    static final Duration DOUBLE_TAP_GAP = Duration.ofMinutes(10);

    /**
     * A day whose first punch lands this close to (or after) shift end has no
     * check-in — the employee forgot to scan in — only a check-out.
     */
    static final Duration CHECK_OUT_ONLY_WINDOW = Duration.ofHours(2);

    /** The shift break is deducted only from a stay at least this long. */
    static final int BREAK_DEDUCTION_MIN_STAY_MINUTES = 300;

    private final AttendancePunchRepository punches;
    private final AttendanceRecordRepository records;
    private final HolidayRepository holidays;
    private final LeaveRequestDayRepository leaveDays;
    private final OvertimeRequestRepository overtimes;
    private final ShiftResolver shifts;

    @Transactional(propagation = Propagation.MANDATORY)
    public AttendanceRecord recompute(long employeeId, LocalDate date) {
        return recompute(employeeId, date, false);
    }

    /** Recompute and (optionally) rewrite the shift snapshot. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AttendanceRecord recompute(long employeeId, LocalDate date, boolean rewriteSnapshot) {
        AttendanceRecord rec = records.findByEmployeeIdAndAttendanceDate(employeeId, date)
                .orElseGet(() -> {
                    AttendanceRecord fresh = new AttendanceRecord();
                    fresh.setEmployeeId(employeeId);
                    fresh.setAttendanceDate(date);
                    return fresh;
                });

        boolean fresh = rec.getId() == null;
        List<AttendancePunch> dayPunches = punches.findAllByEmployeeIdAndAttendanceDateOrderByPunchedAtAsc(
                employeeId, date);

        // Snapshot first: the check-in derivation needs the shift end.
        if (fresh || rewriteSnapshot) {
            shifts.resolve(employeeId, date).ifPresentOrElse(r -> {
                rec.setEffectiveShift(r.shiftId());
                rec.setShiftStartTime(r.startTime());
                rec.setShiftEndTime(r.endTime());
                rec.setShiftBreakStartTime(r.breakStartTime());
                rec.setShiftBreakEndTime(r.breakEndTime());
                rec.setShiftGraceMinutes(r.graceMinutes());
            }, () -> {
                rec.setEffectiveShift(null);
                rec.setShiftStartTime(null);
                rec.setShiftEndTime(null);
                rec.setShiftBreakStartTime(null);
                rec.setShiftBreakEndTime(null);
                rec.setShiftGraceMinutes(null);
            });
        }

        if (!dayPunches.isEmpty()) {
            rec.setCheckIn(checkIn(dayPunches, checkOutOnlyFrom(rec)));
            rec.setCheckOut(checkOut(dayPunches, rec.getCheckIn()));
            if (fresh) rec.setSource(dayPunches.get(0).getSource());
        } else if (fresh) {
            rec.setSource(com.forward.hrms.attendance.enums.PunchSource.SYSTEM);
        }

        AttendanceStatus status = resolveStatus(employeeId, date, !dayPunches.isEmpty());
        rec.setStatus(status);

        recomputeMinutes(rec, dayPunches);
        recomputeOvertime(rec, employeeId, date);

        return records.save(rec);
    }

    /**
     * Re-stamps the shift snapshot from the override (or back to assignment
     * if no override exists). Used by ShiftOverrideService.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void rewriteSnapshotForRange(long employeeId, LocalDate from, LocalDate to) {
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Optional<AttendanceRecord> existing = records.findByEmployeeIdAndAttendanceDate(employeeId, d);
            if (existing.isPresent()) {
                recompute(employeeId, d, true);
            }
            // else: row doesn't exist yet; it'll pick up the override via
            // the standard resolution rule when the day's first punch
            // arrives.
        }
    }

    private AttendanceStatus resolveStatus(long employeeId, LocalDate date, boolean hasPunches) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) return AttendanceStatus.WEEKEND;
        if (holidays.existsByHolidayDate(date)) return AttendanceStatus.HOLIDAY;
        if (!leaveDays.findApprovedDayForEmployee(date, employeeId).isEmpty()) {
            return AttendanceStatus.LEAVE;
        }
        return hasPunches ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;
    }

    /**
     * The day's check-in: the latest-added punch an operator pinned as IN,
     * else the earliest unpinned punch — unless that lands at or after
     * {@code checkOutOnlyFrom}: a first scan that close to shift end is a
     * forgotten check-in, so the day has a check-out only. {@code dayPunches}
     * is in punched_at order; {@code checkOutOnlyFrom} is null without a shift.
     */
    static LocalDateTime checkIn(List<AttendancePunch> dayPunches, LocalDateTime checkOutOnlyFrom) {
        AttendancePunch pinned = latestPinned(dayPunches, PunchDirection.IN);
        if (pinned != null) return pinned.getPunchedAt();
        return dayPunches.stream().filter(p -> p.getDirection() == null)
                .findFirst().map(AttendancePunch::getPunchedAt)
                .filter(at -> checkOutOnlyFrom == null || at.isBefore(checkOutOnlyFrom))
                .orElse(null);
    }

    /** Shift end less {@link #CHECK_OUT_ONLY_WINDOW}; null when the day has no shift. */
    private static LocalDateTime checkOutOnlyFrom(AttendanceRecord rec) {
        if (rec.getShiftStartTime() == null || rec.getShiftEndTime() == null) return null;
        return endOfShiftAt(rec).minus(CHECK_OUT_ONLY_WINDOW);
    }

    /**
     * The day's check-out: the latest-added punch pinned as OUT, else the
     * latest unpinned punch — unless that is the check-in itself (a lone
     * punch is only a check-in) or within {@link #DOUBLE_TAP_GAP} after it
     * (a double tap at arrival is still only a check-in).
     */
    static LocalDateTime checkOut(List<AttendancePunch> dayPunches, LocalDateTime checkIn) {
        AttendancePunch pinned = latestPinned(dayPunches, PunchDirection.OUT);
        if (pinned != null) return pinned.getPunchedAt();
        for (int i = dayPunches.size() - 1; i >= 0; i--) {
            AttendancePunch p = dayPunches.get(i);
            if (p.getDirection() != null) continue;
            return isDoubleTap(checkIn, p.getPunchedAt()) ? null : p.getPunchedAt();
        }
        return null;
    }

    private static boolean isDoubleTap(LocalDateTime checkIn, LocalDateTime at) {
        return checkIn != null && !at.isAfter(checkIn.plus(DOUBLE_TAP_GAP));
    }

    private static AttendancePunch latestPinned(List<AttendancePunch> dayPunches, PunchDirection direction) {
        AttendancePunch latest = null;
        for (AttendancePunch p : dayPunches) {
            if (p.getDirection() == direction && (latest == null || p.getId() > latest.getId())) latest = p;
        }
        return latest;
    }

    private void recomputeMinutes(AttendanceRecord rec, List<AttendancePunch> dayPunches) {
        if (rec.getShiftStartTime() == null || rec.getShiftEndTime() == null) {
            rec.setLateMinutes(0);
            rec.setEarlyMinutes(0);
            rec.setWorkedMinutes(0);
            return;
        }
        if (rec.getCheckIn() == null) {
            rec.setLateMinutes(0);
            rec.setEarlyMinutes(0);
            rec.setWorkedMinutes(0);
            return;
        }

        // §0.5.B: trust-server TZ doctrine — LocalDateTime values are
        // already in PKT, so Duration.between works directly without any
        // ZonedDateTime conversion.
        LocalDateTime startOfShift = startOfShiftAt(rec);
        LocalDateTime endOfShift = endOfShiftAt(rec);
        LocalDateTime checkIn = rec.getCheckIn();
        LocalDateTime checkOut = rec.getCheckOut();

        long graceMs = Duration.ofMinutes(rec.getShiftGraceMinutes() == null ? 0 : rec.getShiftGraceMinutes()).toMillis();
        Duration sinceStart = Duration.between(startOfShift, checkIn);
        // §7.attendance #25 — Math.max clamp legitimately handles "early
        // arrival → late=0", but it also silently swallows a checkIn that
        // landed before startOfShift on a non-cross-midnight shift (i.e.
        // resolveWorkDate routed a punch to the wrong day). Log warn so
        // late-minutes-zero is distinguishable from late-minutes-misrouted.
        boolean crossMidnight = !rec.getShiftEndTime().isAfter(rec.getShiftStartTime());
        if (sinceStart.isNegative() && !crossMidnight) {
            log.warn("attendance check-in before shift start (likely wrong-day routing) "
                            + "employeeId={} date={} startOfShift={} checkIn={}",
                    rec.getEmployeeId(), rec.getAttendanceDate(), startOfShift, checkIn);
        }
        long lateMs = Math.max(0, sinceStart.toMillis() - graceMs);
        rec.setLateMinutes((int) Duration.ofMillis(lateMs).toMinutes());

        if (checkOut != null && checkOut.isBefore(endOfShift)) {
            rec.setEarlyMinutes((int) Duration.between(checkOut, endOfShift).toMinutes());
        } else {
            rec.setEarlyMinutes(0);
        }

        if (checkOut != null) {
            int worked = (int) Duration.between(checkIn, checkOut).toMinutes();
            // §7.attendance #25 — checkOut < checkIn means an operator
            // amendment (or device clock skew) inverted the pair. We clamp
            // worked to 0 — but log a warn so the inversion is visible
            // somewhere other than "the daily total was 0 today".
            if (worked < 0) {
                log.warn("attendance inverted punch pair employeeId={} date={} checkIn={} checkOut={}",
                        rec.getEmployeeId(), rec.getAttendanceDate(), checkIn, checkOut);
            }
            // A short stay (half day) never reached the break, so it isn't deducted.
            int breakMinutes = worked >= BREAK_DEDUCTION_MIN_STAY_MINUTES ? rec.getShiftBreakMinutes() : 0;
            rec.setWorkedMinutes(Math.max(0, worked - breakMinutes));
        } else {
            rec.setWorkedMinutes(0);
        }
    }

    private void recomputeOvertime(AttendanceRecord rec, long employeeId, LocalDate date) {
        Set<OvertimeType> types = EnumSet.noneOf(OvertimeType.class);
        overtimes.findAllByEmployeeIdAndAttendanceDateAndStatus(employeeId, date, OvertimeStatus.APPROVED)
                .forEach(o -> types.add(o.getOvertimeType()));
        // Sunday / holiday: the whole worked day is SPECIAL. Working day: the
        // first two hours past shift end are NORMAL, the rest EXTRA. Each
        // bucket is credited only when its type has an approved request.
        int normal = 0, extra = 0, special = 0;
        if (isSundayOrHoliday(date)) {
            if (types.contains(OvertimeType.SPECIAL)) special = rec.getWorkedMinutes();
        } else if (!types.isEmpty() && rec.getShiftStartTime() != null && rec.getShiftEndTime() != null) {
            int otMinutes = Math.max(0, rec.getWorkedMinutes() - expectedMinutes(rec));
            int normalWindow = Math.min(otMinutes, NORMAL_OT_MAX_MINUTES);
            if (types.contains(OvertimeType.NORMAL)) normal = normalWindow;
            if (types.contains(OvertimeType.EXTRA)) extra = otMinutes - normalWindow;
        }
        rec.setOtNormalMinutes(normal);
        rec.setOtExtraMinutes(extra);
        rec.setOtSpecialMinutes(special);
    }

    /**
     * OT minutes recognized for the day, independent of the chosen type: the
     * whole worked day on a Sunday / holiday (no shift to work against),
     * otherwise minutes worked beyond the expected shift. Also what list rows
     * show as the projected OT of a request that isn't approved yet.
     */
    public int overtimeMinutes(AttendanceRecord rec, LocalDate date) {
        if (isSundayOrHoliday(date)) return rec.getWorkedMinutes();
        if (rec.getShiftStartTime() == null || rec.getShiftEndTime() == null) return 0;
        return Math.max(0, rec.getWorkedMinutes() - expectedMinutes(rec));
    }

    /** SPECIAL overtime days; NORMAL / EXTRA apply on every other day. */
    public boolean isSundayOrHoliday(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SUNDAY || holidays.existsByHolidayDate(date);
    }

    private static int expectedMinutes(AttendanceRecord rec) {
        int startMin = rec.getShiftStartTime().toSecondOfDay() / 60;
        int endMin = rec.getShiftEndTime().toSecondOfDay() / 60;
        int span = endMin > startMin ? endMin - startMin : (24 * 60) - (startMin - endMin);
        return Math.max(0, span - rec.getShiftBreakMinutes());
    }

    private static LocalDateTime startOfShiftAt(AttendanceRecord rec) {
        return LocalDateTime.of(rec.getAttendanceDate(), rec.getShiftStartTime());
    }

    private static LocalDateTime endOfShiftAt(AttendanceRecord rec) {
        LocalDateTime end = LocalDateTime.of(rec.getAttendanceDate(), rec.getShiftEndTime());
        if (!rec.getShiftEndTime().isAfter(rec.getShiftStartTime())) {
            return end.plusDays(1);
        }
        return end;
    }

    /** Stamp ABSENT for missing rows on {@code date}. Used by the nightly job. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AttendanceRecord ensureAbsentRow(long employeeId, LocalDate date) {
        AttendanceRecord existing = records.findByEmployeeIdAndAttendanceDate(employeeId, date).orElse(null);
        if (existing != null) return existing;
        final AttendanceRecord rec = new AttendanceRecord();
        rec.setEmployeeId(employeeId);
        rec.setAttendanceDate(date);
        rec.setSource(com.forward.hrms.attendance.enums.PunchSource.SYSTEM);
        AttendanceStatus status = resolveStatus(employeeId, date, false);
        rec.setStatus(status);
        shifts.resolve(employeeId, date).ifPresent(r -> {
            rec.setEffectiveShift(r.shiftId());
            rec.setShiftStartTime(r.startTime());
            rec.setShiftEndTime(r.endTime());
            rec.setShiftBreakStartTime(r.breakStartTime());
            rec.setShiftBreakEndTime(r.breakEndTime());
            rec.setShiftGraceMinutes(r.graceMinutes());
        });
        return records.save(rec);
    }

    public Optional<AttendanceRecord> get(long employeeId, LocalDate date) {
        return records.findByEmployeeIdAndAttendanceDate(employeeId, date);
    }

    /** Used by the regularization handler — punches were just inserted; recompute. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AttendanceRecord recomputeAfterPunchInsert(long employeeId, LocalDateTime punchedAt) {
        return recompute(employeeId, punchedAt.toLocalDate());
    }
}

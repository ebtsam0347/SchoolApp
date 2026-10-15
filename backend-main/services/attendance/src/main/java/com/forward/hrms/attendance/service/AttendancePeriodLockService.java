package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.response.AttendancePeriodLockDto;
import com.forward.hrms.attendance.entity.AttendancePeriodLock;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.events.AttendanceEvents;
import com.forward.hrms.attendance.repository.AttendancePeriodLockRepository;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Period-level lock used by the operator close-out and by payroll's
 * lock-attendance step. The presence of an {@link AttendancePeriodLock}
 * row IS the lock — there is no boolean to toggle. Lock and unlock are
 * idempotent / 404 respectively.
 *
 * <p>A period can only be locked once its month has fully elapsed — April
 * is lockable from May 1, not before (see {@link #lock}). Locking emits
 * {@code attendance.period_locked}; payroll consumes it to freeze the
 * matching run.
 *
 * <p>Write enforcement lives on {@link #assertOpen}: the punch / leave /
 * overtime / short-leave / regularization mutation entry points call it so
 * a locked month is frozen end-to-end, not just at the lock row.
 */
@Service
@RequiredArgsConstructor
public class AttendancePeriodLockService {

    private final AttendancePeriodLockRepository locks;
    private final AuditEmitter audit;
    private final Outbox outbox;
    private final CallerContext caller;

    @Transactional(readOnly = true)
    public List<AttendancePeriodLockDto> listForYear(int year) {
        return locks.findByPeriodYearOrderByPeriodMonthAsc(year).stream()
                .map(AttendancePeriodLockDto::of).toList();
    }

    @Transactional(readOnly = true)
    public boolean isLocked(int year, int month) {
        return locks.existsByPeriodYearAndPeriodMonth(year, month);
    }

    /**
     * Refuse a write that targets a locked period. Called from the
     * attendance mutation entry points (punch / leave / overtime /
     * short-leave / regularization create + approve). No own transaction —
     * joins the caller's write tx so the {@code exists} check sees the
     * same snapshot.
     */
    public void assertOpen(LocalDate date) {
        if (date != null && locks.existsByPeriodYearAndPeriodMonth(date.getYear(), date.getMonthValue())) {
            throw new UnprocessableError(AttendanceErrorCode.PERIOD_LOCKED,
                    "attendance period %d-%02d is locked — no changes allowed"
                            .formatted(date.getYear(), date.getMonthValue()));
        }
    }

    /** Range variant — refuses if ANY month the [from, to] range touches is locked. */
    public void assertOpen(LocalDate from, LocalDate to) {
        YearMonth end = YearMonth.from(to);
        for (YearMonth ym = YearMonth.from(from); !ym.isAfter(end); ym = ym.plusMonths(1)) {
            assertOpen(ym.atDay(1));
        }
    }

    @Transactional
    public AttendancePeriodLockDto lock(int year, int month) {
        validate(year, month);
        // A period is lockable only once its month is fully behind us: April
        // (2026-04) becomes lockable on 2026-05-01. Locking the current or a
        // future month would freeze attendance still being recorded.
        YearMonth period = YearMonth.of(year, month);
        if (!period.isBefore(YearMonth.now())) {
            throw new UnprocessableError(AttendanceErrorCode.PERIOD_NOT_ELAPSED,
                    "period %d-%02d cannot be locked until it has fully elapsed (lockable from %s)"
                            .formatted(year, month, period.plusMonths(1).atDay(1)));
        }
        CurrentUser actor = caller.requireUser();
        return locks.findByPeriodYearAndPeriodMonth(year, month)
                .map(AttendancePeriodLockDto::of)
                .orElseGet(() -> {
                    AttendancePeriodLock l = new AttendancePeriodLock();
                    l.setPeriodYear(year);
                    l.setPeriodMonth(month);
                    l.setLockedAt(LocalDateTime.now());
                    l.setLockedBy(actor.username());
                    l = locks.save(l);
                    outbox.publish(AttendanceEvents.TOPIC, periodKey(year, month),
                            AttendanceEvents.PERIOD_LOCKED,
                            new AttendanceEvents.PeriodLocked(year, month, l.getLockedBy(), l.getLockedAt()));
                    audit.emit("attendance_period", periodKey(year, month), "locked",
                            null, snapshot(l), true);
                    return AttendancePeriodLockDto.of(l);
                });
    }

    @Transactional
    public void unlock(int year, int month) {
        validate(year, month);
        CurrentUser actor = caller.requireUser();
        AttendancePeriodLock l = locks.findByPeriodYearAndPeriodMonth(year, month)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.PERIOD_NOT_LOCKED,
                        "period %d-%02d is not locked".formatted(year, month)));
        Map<String, Object> before = snapshot(l);
        locks.delete(l);
        outbox.publish(AttendanceEvents.TOPIC, periodKey(year, month),
                AttendanceEvents.PERIOD_UNLOCKED,
                new AttendanceEvents.PeriodUnlocked(year, month, actor.username(), LocalDateTime.now()));
        audit.emit("attendance_period", periodKey(year, month), "unlocked", before, null, true);
    }

    private static void validate(int year, int month) {
        if (month < 1 || month > 12) {
            throw new UnprocessableError(AttendanceErrorCode.PERIOD_INVALID,
                    "month must be 1-12 (got %d)".formatted(month));
        }
        if (year < 2000 || year > 2100) {
            throw new UnprocessableError(AttendanceErrorCode.PERIOD_INVALID,
                    "year out of range: %d".formatted(year));
        }
    }

    private static String periodKey(int year, int month) {
        return "%d-%02d".formatted(year, month);
    }

    private static Map<String, Object> snapshot(AttendancePeriodLock l) {
        return Map.of(
                "periodYear",  l.getPeriodYear(),
                "periodMonth", l.getPeriodMonth(),
                "lockedAt",    l.getLockedAt().toString(),
                "lockedBy",    l.getLockedBy());
    }
}

package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.entity.AttendanceEmployee;
import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.events.AttendanceEvents;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.commons.batch.BatchOutcomes;
import com.forward.hrms.commons.batch.BatchRunner;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Nightly close. Runs at 02:00 Asia/Karachi for the previous calendar
 * day and stamps an ABSENT row for every active employee that doesn't
 * already have one (or for whom the WEEKEND / HOLIDAY / LEAVE
 * resolver yields a non-ABSENT status).
 *
 * <p>Active-employee discovery reads the local {@code attendance_employees}
 * mirror ({@code employment_status = 'ACTIVE'}), kept current by the
 * employee.* event consumer. This replaces a 02:00 HTTP fan-out to
 * employee-service (paginated, with a service-account bearer token): the
 * data is already here, and the close no longer fails silently when
 * employee-service happens to be restarting. The {@code ACTIVE} filter
 * mirrors the old {@code GET /employees?status=ACTIVE} exactly.
 *
 * <p>§7.attendance #42 — each employee runs in its own
 * {@code REQUIRES_NEW} transaction via {@link BatchRunner}; one bad
 * row no longer aborts the close. The terminal {@code daily.closed}
 * outbox emit runs in a separate {@code REQUIRES_NEW} so it commits
 * with the totals even when some rows failed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NightlyCloseJob {

    private final AttendanceRecomputeService recompute;
    private final Outbox outbox;
    private final BatchRunner batch;
    private final AttendanceEmployeeRepository attendanceEmployees;

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Karachi")
    public void close() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<AttendanceEmployee> active = attendanceEmployees.findAllByEmploymentStatus("ACTIVE");
        log.info("Running nightly attendance close for {} over {} active employees",
                yesterday, active.size());

        BatchOutcomes<AttendanceEmployee, AttendanceStatus> outcomes =
                batch.runWithPartial(active,
                        row -> recompute.ensureAbsentRow(row.getEmployeeId(), yesterday).getStatus());

        int rowsTouched = 0;
        int absentMarked = 0;
        for (var s : outcomes.succeeded()) {
            rowsTouched++;
            if (s.result() == AttendanceStatus.ABSENT) absentMarked++;
        }
        for (var f : outcomes.failed()) {
            log.warn("nightly close: employee {} failed code={} msg={}",
                    f.item().getEmployeeId(), f.errorCode(), f.message(), f.cause());
        }

        final int finalRows = rowsTouched;
        final int finalAbsent = absentMarked;
        batch.runInNewTxVoid(() -> outbox.publish(AttendanceEvents.TOPIC, "daily:" + yesterday,
                AttendanceEvents.DAILY_CLOSED,
                new AttendanceEvents.DailyClosed(yesterday, finalAbsent, finalRows)));
        log.info("Nightly close complete: {} rows touched, {} marked ABSENT, {} failed",
                rowsTouched, absentMarked, outcomes.failedCount());
    }
}

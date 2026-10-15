package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.entity.AttendanceEmployee;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.commons.batch.BatchOutcomes;
import com.forward.hrms.commons.batch.BatchRunner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily leave-cycle renewal. Runs at 01:00 Asia/Karachi over active
 * employees (read from the local {@code attendance_employees} mirror, same
 * as {@link NightlyCloseJob}). For each employee, {@link LeaveBalanceService}
 * opens the next cycle of any anchored leave type whose boundary is today:
 *
 * <ul>
 *   <li>JOINING types renew on the employee's joining anniversary — unused
 *       balance on the closing cycle is encashed (annual leave) via a
 *       {@code leave.encashed} event.</li>
 *   <li>FISCAL types renew on Jul 1 — unused balance lapses.</li>
 * </ul>
 *
 * <p>Idempotent: the renew is a no-op once the new cycle row exists, so a
 * re-run on the same day (or a missed day picked up later — the boundary
 * test is exact-day, so a missed anniversary is NOT retroactively renewed;
 * the request flow self-heals the cycle on first use) does no harm. Each
 * employee runs in its own {@code REQUIRES_NEW} transaction via
 * {@link BatchRunner}; one bad row doesn't abort the batch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveRenewalJob {

    private final LeaveBalanceService balances;
    private final BatchRunner batch;
    private final AttendanceEmployeeRepository attendanceEmployees;

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Karachi")
    public void renew() {
        LocalDate today = LocalDate.now();
        List<AttendanceEmployee> active = attendanceEmployees.findAllByEmploymentStatus("ACTIVE");
        log.info("Running leave-cycle renewal for {} over {} active employees", today, active.size());

        BatchOutcomes<AttendanceEmployee, Void> outcomes = batch.runWithPartialVoid(active,
                row -> balances.renewCycles(row.getEmployeeId(), row.getJoiningDate(), today));

        for (var f : outcomes.failed()) {
            log.warn("leave renewal: employee {} failed code={} msg={}",
                    f.item().getEmployeeId(), f.errorCode(), f.message(), f.cause());
        }
        log.info("Leave-cycle renewal complete: {} employees processed, {} failed",
                outcomes.succeeded().size(), outcomes.failedCount());
    }
}

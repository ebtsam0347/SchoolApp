package com.forward.hrms.attendance.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AttendanceInitializationScheduler {

    private final AttendanceInitializationService initializationService;

    /**
     * Executes every 30 minutes.
     * <p>
     * 00:00
     * 00:30
     * 01:00
     * 01:30
     * ...
     */

    @Scheduled(cron = "0 */30 * * * *")
    public void initializeAttendance() {

        log.info("Attendance initialization scheduler started.");

        try {

            initializationService.initializeAttendance();
            log.debug("Attendance initialization scheduler successfully initialized.");

        } catch (Exception ex) {

            log.error("Attendance initialization scheduler failed.", ex);
        }

        log.info("Attendance initialization scheduler finished successfully.");
    }
}
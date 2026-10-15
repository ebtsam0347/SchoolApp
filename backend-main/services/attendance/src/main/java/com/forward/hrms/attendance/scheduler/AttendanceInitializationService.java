package com.forward.hrms.attendance.scheduler;

import com.forward.hrms.attendance.entity.EmployeeShift;
import com.forward.hrms.attendance.entity.Shift;
import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;
import com.forward.hrms.attendance.repository.EmployeeShiftRepository;
import com.forward.hrms.attendance.repository.ShiftRepository;
import com.forward.hrms.attendance.service.AttendanceRecomputeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceInitializationService {

    /**
     * Initialize attendance 1 hour before shift starts.
     */
    private static final int PRE_SHIFT_WINDOW_MINUTES = 60;

    /**
     * Scheduler frequency.
     */
    private static final int SCHEDULER_INTERVAL_MINUTES = 30;

    /**
     * Employment statuses that still get a daily attendance row.
     * Resigned employees are ON_NOTICE until their last working day.
     */
    private static final Set<String> WORKING_STATUSES = Set.of("ACTIVE", "RESIGNED", "ON_NOTICE", "ON_LEAVE");

    private final EmployeeClient employeeClient;
    private final ShiftRepository shiftRepository;
    private final EmployeeShiftRepository employeeShiftRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceRecomputeService attendanceRecomputeService;

    /**
     * Opens the day's record for every employee whose shift starts within
     * the next hour.
     *
     * <p>Date-aware: a shift's work date is the day it starts, and its
     * initialization instant is that start less an hour — which for a shift
     * starting just after midnight (00:00–00:59) falls on the evening before.
     * So each run checks today's and tomorrow's work dates, and initializes
     * the record for the work date whose instant fell in
     * {@code [previousRun, now]} (e.g. at 23:30 on 9 Oct it opens the 10 Oct
     * record of a 00:30 shift, not a 9 Oct one).
     */
    @Transactional
    public void initializeAttendance() {

        LocalDateTime now = LocalDateTime.now();

        /*
         * Previous scheduler execution time.
         *
         * Example:
         *
         * Scheduler runs at
         *
         * 12:00
         * 12:30
         * 13:00
         *
         * If now = 12:30
         *
         * previousRun = 12:00
         */
        LocalDateTime previousRun =
                now.minusMinutes(SCHEDULER_INTERVAL_MINUTES);

        List<Shift> activeShifts =
                shiftRepository.findAllByActiveTrueOrderByCodeAsc();

        if (activeShifts.isEmpty()) {
            log.info("No active shifts found.");
            return;
        }

        log.info(
                "Attendance initialization started. Current={}, Previous={}, ActiveShifts={}",
                now,
                previousRun,
                activeShifts.size());

        // Per work date, loaded only when some shift is due on it.
        Map<LocalDate, WorkDateRoster> rosters = new HashMap<>();

        for (Shift shift : activeShifts) {
            for (LocalDate workDate : List.of(now.toLocalDate(), now.toLocalDate().plusDays(1))) {

                /*
                 * Example
                 *
                 * Shift Start = 13:30 → Initialization = 12:30 the same day
                 * Shift Start = 00:30 → Initialization = 23:30 the day before
                 */
                LocalDateTime initializationTime =
                        workDate.atTime(shift.getStartTime())
                                .minusMinutes(PRE_SHIFT_WINDOW_MINUTES);

                /*
                 * Was initialization time reached
                 * between previous scheduler execution
                 * and current scheduler execution?
                 */
                boolean shouldRun =
                        !initializationTime.isBefore(previousRun)
                                && initializationTime.isBefore(now.plusSeconds(1));

                if (!shouldRun) {
                    continue;
                }

                WorkDateRoster roster = rosters.computeIfAbsent(workDate, this::loadRoster);

                List<EmployeeShift> employees =
                        roster.employeesByShift().getOrDefault(
                                shift.getId(),
                                List.of());

                log.info(
                        "Processing Shift={}, WorkDate={}, Initialization={}, Employees={}",
                        shift.getCode(),
                        workDate,
                        initializationTime,
                        employees.size());

                int initialized = 0;
                int skipped = 0;

                for (EmployeeShift assignment : employees) {

                    Long employeeId = assignment.getEmployeeId();

                    if (roster.initializedEmployees().contains(employeeId)) {
                        skipped++;
                        continue;
                    }

                    attendanceRecomputeService.recompute(employeeId, workDate);

                    roster.initializedEmployees().add(employeeId);

                    initialized++;
                }

                log.info(
                        "Shift {} for {} completed. Initialized={}, Skipped={}",
                        shift.getCode(),
                        workDate,
                        initialized,
                        skipped);
            }
        }

        log.info("Attendance initialization completed.");
    }

    /** Who is assigned on {@code workDate} (still on the rolls) and who already has a record. */
    private WorkDateRoster loadRoster(LocalDate workDate) {

        Set<Long> initializedEmployees =
                attendanceRecordRepository.findEmployeeIdsByAttendanceDate(workDate);

        List<EmployeeShift> assignments = employeeShiftRepository.findAllActiveOn(workDate);

        /*
         * Skip employees no longer on the rolls. Status is read live from
         * employee.employees via employee-service (GET /employees/employment-statuses).
         */
        Map<Long, String> statuses = employeeClient.employmentStatusesAsSystem(
                assignments.stream().map(EmployeeShift::getEmployeeId).toList());

        List<EmployeeShift> activeAssignments = assignments.stream()
                .filter(a -> WORKING_STATUSES.contains(statuses.get(a.getEmployeeId())))
                .toList();

        if (activeAssignments.size() < assignments.size()) {
            log.info("Skipped {} assignment(s) on {} for employee employment_status not in {}",
                    assignments.size() - activeAssignments.size(), workDate, WORKING_STATUSES);
        }

        return new WorkDateRoster(
                new java.util.HashSet<>(initializedEmployees),
                activeAssignments.stream().collect(Collectors.groupingBy(EmployeeShift::getShiftId)));
    }

    private record WorkDateRoster(Set<Long> initializedEmployees,
                                  Map<Long, List<EmployeeShift>> employeesByShift) {}
}

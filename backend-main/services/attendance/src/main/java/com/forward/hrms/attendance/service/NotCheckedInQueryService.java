package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.response.NotCheckedInDto;
import com.forward.hrms.attendance.entity.AttendanceEmployee;
import com.forward.hrms.attendance.entity.AttendanceRecord;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;
import com.forward.hrms.attendance.repository.HolidayRepository;
import com.forward.hrms.attendance.repository.LeaveRequestDayRepository;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Live "who hasn't checked in yet" roster for HR. Derived at read time —
 * nothing is persisted. An active employee appears once their shift-start +
 * grace has elapsed today and no check-in punch exists; they drop off the
 * moment a punch lands (the punch creates a PRESENT record). This is
 * deliberately distinct from the settled ABSENT status the nightly close
 * writes, which feeds payroll LOP — see {@code docs/simplifications.md}.
 *
 * <p>The "expected today" filter mirrors {@link AttendanceRecomputeService}'s
 * status rules exactly: Sunday is a company-wide weekend, holidays are
 * non-working, approved-leave days are excused, and an employee with no
 * resolvable shift (weekly-off / unassigned) carries no expectation.
 */
@Service
@RequiredArgsConstructor
public class NotCheckedInQueryService {

    private final AttendanceEmployeeRepository employees;
    private final AttendanceRecordRepository records;
    private final HolidayRepository holidays;
    private final LeaveRequestDayRepository leaveDays;
    private final ShiftResolver shifts;

    @Transactional(readOnly = true)
    public PageResponse<NotCheckedInDto> notCheckedIn(LocalDateTime now, String employeeCode,
                                                      Pageable pageable) {
        // The roster is derived and sorted by a computed field (minutesOverdue),
        // so we can't push paging to SQL — enumerating every active employee is
        // inherent to "who hasn't checked in". We compute the full sorted list
        // and slice it here so the FE never receives thousands of rows at once.
        Pageable page = PageableDefaults.apply(pageable, "minutesOverdue");

        List<NotCheckedInDto> all = computeRoster(now, employeeCode);

        int from = (int) Math.min(page.getOffset(), all.size());
        int to = Math.min(from + page.getPageSize(), all.size());
        return PageResponse.from(new PageImpl<>(all.subList(from, to), page, all.size()));
    }

    private List<NotCheckedInDto> computeRoster(LocalDateTime now, String employeeCode) {
        LocalDate today = now.toLocalDate();
        // Whole day is non-working for everyone → no expectation.
        if (today.getDayOfWeek() == DayOfWeek.SUNDAY || holidays.existsByHolidayDate(today)) {
            return List.of();
        }

        List<AttendanceEmployee> active = employees.findAllByEmploymentStatus("ACTIVE");
        // Optional employeeCode filter: case-insensitive substring, applied
        // before the per-employee shift resolve so a search stays cheap.
        if (employeeCode != null && !employeeCode.isBlank()) {
            String needle = employeeCode.trim().toLowerCase();
            active = active.stream()
                    .filter(e -> e.getEmployeeCode() != null
                            && e.getEmployeeCode().toLowerCase().contains(needle))
                    .toList();
        }
        if (active.isEmpty()) return List.of();
        List<Long> activeIds = active.stream().map(AttendanceEmployee::getEmployeeId).toList();

        // One batch query each for "already checked in" and "on approved
        // leave" — keeps the per-employee work below to a shift resolve.
        Set<Long> checkedIn = new HashSet<>();
        for (AttendanceRecord r : records.findAllByEmployeeIdInAndAttendanceDate(activeIds, today)) {
            if (r.getCheckIn() != null) checkedIn.add(r.getEmployeeId());
        }
        Set<Long> onLeave = new HashSet<>(leaveDays.findEmployeeIdsApprovedOnDate(today));

        List<NotCheckedInDto> out = new ArrayList<>();
        for (AttendanceEmployee e : active) {
            long id = e.getEmployeeId();
            if (checkedIn.contains(id) || onLeave.contains(id)) continue;

            // No resolvable shift = weekly-off / unassigned → not expected.
            ShiftResolution shift = shifts.resolve(id, today).orElse(null);
            if (shift == null || shift.startTime() == null) continue;

            // Build the cutoff as a datetime so grace that crosses midnight
            // (a late-evening shift) rolls to the next day correctly.
            LocalDateTime cutoffAt = LocalDateTime.of(today, shift.startTime())
                    .plusMinutes(shift.graceMinutes());
            if (!now.isAfter(cutoffAt)) continue; // shift+grace hasn't elapsed yet

            // Upper bound: once the shift's working window is over, a missing
            // check-in is no longer "running late" — it's a settled absence the
            // nightly close owns. Without this the list piles up all-day no-shows
            // (a 09:00 absentee still flagged at 23:00), defeating the live nudge.
            if (shift.endTime() != null) {
                LocalDateTime shiftEndAt = LocalDateTime.of(today, shift.endTime());
                if (shift.crossesMidnight()) shiftEndAt = shiftEndAt.plusDays(1);
                if (now.isAfter(shiftEndAt)) continue;
            }

            long overdue = Duration.between(cutoffAt, now).toMinutes();
            out.add(new NotCheckedInDto(id, e.getEmployeeCode(), e.getFullName(),
                    shift.shiftId(), shift.startTime(), shift.graceMinutes(),
                    cutoffAt.toLocalTime(), overdue));
        }
        out.sort(Comparator.comparingLong(NotCheckedInDto::minutesOverdue).reversed());
        return out;
    }
}

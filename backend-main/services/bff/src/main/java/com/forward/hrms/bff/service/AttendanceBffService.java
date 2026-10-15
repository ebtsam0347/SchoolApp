package com.forward.hrms.bff.service;

import com.forward.hrms.bff.client.AttendanceClient;
import com.forward.hrms.bff.client.AuditClient;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.audit.dto.response.TimelinePage;
import com.forward.hrms.attendance.dto.response.AttendanceMonth;
import com.forward.hrms.bff.dto.response.TeamAttendanceToday;
import com.forward.hrms.attendance.dto.response.AttendanceRecordDto;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.commons.cache.EmployeeSummaryMapper;
import com.forward.hrms.commons.cache.CatalogCache;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.web.async.RequestPropagatingExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Cross-service attendance composites that survive the
 * single-service-composite move (Step 5 of docs/caching.md).
 *
 * <p>{@code requestTimeline} pairs attendance with audit-service.
 * {@code teamToday} and {@code teamMonth} resolve coverage via
 * employee-service ({@link ManagerCoverageResolver}) before joining
 * attendance rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceBffService {

    static final int TEAM_TODAY_ROW_CAP = 500;

    private final AttendanceClient attendance;
    private final AuditClient audit;
    private final CatalogCache catalogCache;
    private final ManagerCoverageResolver coverageResolver;

    private final RequestPropagatingExecutor propagating;

    // ===== /bff/attendance/team/today =====

    /**
     * Roll-call view across the caller's coverage set on the given date.
     * Coverage = direct reports + reports of any manager who has delegated
     * approval authority to the caller for that date. Beyond {@link #TEAM_TODAY_ROW_CAP}
     * rows the response is truncated and {@code truncated=true}.
     */
    public TeamAttendanceToday teamToday(LocalDate dateArg) {
        LocalDate date = dateArg == null ? LocalDate.now() : dateArg;
        ManagerCoverageResolver.Coverage coverage = coverageResolver.resolve(date);
        if (coverage.isEmpty()) {
            // Match the manager dashboard / teamMonth contract: a caller who
            // covers nobody is not a manager. 403 (not empty-200) so the FE has
            // one consistent signal to gate the "My Team" nav on.
            throw new ForbiddenError(CommonErrorCode.FORBIDDEN,
                    "team attendance requires direct reports (or an active delegation)");
        }

        List<EmployeeDto> covered = new ArrayList<>(coverage.byId().values());
        boolean truncated = covered.size() > TEAM_TODAY_ROW_CAP;
        if (truncated) covered = covered.subList(0, TEAM_TODAY_ROW_CAP);

        boolean isToday = date.equals(LocalDate.now());
        List<EmployeeDto> targets = covered;
        List<CompletableFuture<AttendanceRecordDto>> recordFutures =
                new ArrayList<>(targets.size());
        for (EmployeeDto e : targets) {
            recordFutures.add(supplyWithRequest(() -> isToday
                    ? attendance.employeeTodayOrNull(e.id())
                    : firstOrNull(attendance.employeeRange(e.id(), date, date))));
        }

        try {
            CompletableFuture.allOf(recordFutures.toArray(new CompletableFuture[0])).join();

            CatalogCache.CatalogIndex idx = catalogCache.snapshot();
            Map<Long, EmployeeSummary> summaries = new HashMap<>(targets.size());
            for (EmployeeDto e : targets) summaries.put(e.id(), EmployeeSummaryMapper.toSummary(e, idx));

            List<TeamAttendanceToday.Row> rows = new ArrayList<>(targets.size());
            for (int i = 0; i < targets.size(); i++) {
                EmployeeDto e = targets.get(i);
                AttendanceRecordDto rec = recordFutures.get(i).get();
                String status = rec == null ? "PENDING_PUNCH" : rec.status();
                rows.add(new TeamAttendanceToday.Row(
                        summaries.get(e.id()),
                        status,
                        rec == null ? null : rec.checkIn(),
                        rec == null ? null : rec.checkOut(),
                        rec == null ? 0 : rec.lateMinutes(),
                        // activeLeave overlay needs an employee-scoped APPROVED-leave list
                        // upstream; not wired yet. Status==LEAVE is enough for the chip.
                        null));
            }
            return new TeamAttendanceToday(date, rows, truncated);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while assembling /bff/attendance/team/today", ie);
        } catch (ExecutionException ee) {
            throw unwrap(ee);
        }
    }

    private static <T> T firstOrNull(List<T> xs) {
        return xs == null || xs.isEmpty() ? null : xs.get(0);
    }

    // ===== /bff/attendance/team/{employeeId}/month =====

    /**
     * Same shape as the ESS month view, scoped to a single covered employee.
     * Coverage check (manager OR active delegation on month-start) lives on
     * {@link ManagerCoverageResolver} — 403 when the caller has no
     * authority over the target.
     */
    public AttendanceMonth teamMonth(long employeeId, String monthArg) {
        YearMonth ym = monthArg == null || monthArg.isBlank()
                ? YearMonth.now()
                : YearMonth.parse(monthArg);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        coverageResolver.requireCoverage(employeeId, from);

        CompletableFuture<List<AttendanceRecordDto>> recordsF =
                supplyWithRequest(() -> attendance.employeeRange(employeeId, from, to));
        CompletableFuture<List<com.forward.hrms.attendance.dto.response.HolidayDto>> holidaysF =
                supplyWithRequest(() -> attendance.holidays(ym.getYear()));

        try {
            CompletableFuture.allOf(recordsF, holidaysF).join();
            return buildMonth(ym, recordsF.get(), holidaysF.get());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while assembling /bff/attendance/team/{id}/month", ie);
        } catch (ExecutionException ee) {
            throw unwrap(ee);
        }
    }

    /** Same shape as attendance-service's composite month builder, minus the leave overlay
     *  (the per-employee approved-leaves list isn't exposed upstream yet). */
    private static AttendanceMonth buildMonth(YearMonth ym,
                                              List<AttendanceRecordDto> records,
                                              List<com.forward.hrms.attendance.dto.response.HolidayDto> holidays) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        Map<LocalDate, AttendanceRecordDto> recordByDate = new HashMap<>();
        for (AttendanceRecordDto r : records) recordByDate.put(r.workDate(), r);
        Map<LocalDate, com.forward.hrms.attendance.dto.response.HolidayDto> holidayByDate = new HashMap<>();
        for (com.forward.hrms.attendance.dto.response.HolidayDto h : holidays) {
            if (!h.holidayDate().isBefore(from) && !h.holidayDate().isAfter(to)) {
                holidayByDate.put(h.holidayDate(), h);
            }
        }

        List<AttendanceMonth.Day> days = new ArrayList<>(ym.lengthOfMonth());
        int present = 0, absent = 0, leaveCnt = 0, holiday = 0, halfDay = 0, weekend = 0;
        int totalWorked = 0, totalOt = 0, totalLate = 0;

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            AttendanceRecordDto rec = recordByDate.get(d);
            com.forward.hrms.attendance.dto.response.HolidayDto hol = holidayByDate.get(d);

            String kind = hol != null
                    ? "HOLIDAY"
                    : rec != null ? rec.status()
                    : d.getDayOfWeek() == DayOfWeek.SUNDAY ? "WEEKEND" : "ABSENT";
            switch (kind) {
                case "PRESENT" -> present++;
                case "ABSENT" -> absent++;
                case "LEAVE" -> leaveCnt++;
                case "HOLIDAY" -> holiday++;
                case "HALF_DAY" -> halfDay++;
                case "WEEKEND" -> weekend++;
                default -> { /* unreachable */ }
            }
            if (rec != null) {
                totalWorked += rec.workedMinutes();
                totalOt += rec.otNormalMinutes() + rec.otExtraMinutes() + rec.otSpecialMinutes();
                totalLate += rec.lateMinutes();
            }
            days.add(new AttendanceMonth.Day(d, kind, rec, hol, null));
        }

        return new AttendanceMonth(days,
                new AttendanceMonth.Summary(present, absent, leaveCnt, holiday,
                        halfDay, weekend, totalWorked, totalOt, totalLate));
    }

    // ===== T17: /bff/timeline/{requestType}/{id} =====

    /**
     * Approval-request timeline for the drawer Activity tab. The BFF is the
     * gatekeeper: probe the upstream attendance request first (which enforces
     * caller visibility) and only then read the audit feed.
     */
    public TimelinePage requestTimeline(String requestType, long id, Integer limit, String cursor) {
        String entityType = switch (requestType) {
            case "leave" -> {
                attendance.requireLeaveVisible(id);
                yield "leave_request";
            }
            case "short-leave" -> {
                attendance.requireShortLeaveVisible(id);
                yield "short_leave_request";
            }
            case "overtime" -> {
                attendance.requireOvertimeVisible(id);
                yield "overtime_request";
            }
            case "regularization" -> {
                attendance.requireRegularizationVisible(id);
                yield "regularization_request";
            }
            default -> throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    com.forward.hrms.commons.core.error.CommonErrorCode.UNPROCESSABLE,
                    "requestType must be one of: leave, short-leave, overtime, regularization");
        };
        int n = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
        return audit.entityTimeline(entityType, String.valueOf(id), n, cursor);
    }

    // ===== shared =====

    <T> CompletableFuture<T> supplyWithRequest(Supplier<T> supplier) {
        return propagating.async(supplier);
    }

    /** §7.bff #25 — delegates to {@link com.forward.hrms.bff.service.dashboard.DashboardSupport#unwrap}. */
    static RuntimeException unwrap(ExecutionException ee) {
        return com.forward.hrms.bff.service.dashboard.DashboardSupport.unwrap(ee);
    }
}

package com.forward.hrms.attendance.service.composite;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.dto.response.ApprovalsInbox;
import com.forward.hrms.attendance.dto.response.AttendanceMonth;
import com.forward.hrms.attendance.dto.response.AttendancePunchesPage;
import com.forward.hrms.attendance.dto.response.AttendanceRecordDto;
import com.forward.hrms.attendance.dto.response.AttendanceRecordsPage;
import com.forward.hrms.attendance.dto.response.AttendanceToday;
import com.forward.hrms.attendance.dto.response.HolidayDto;
import com.forward.hrms.attendance.dto.response.LeaveApplyContext;
import com.forward.hrms.attendance.dto.response.LeaveBalanceDto;
import com.forward.hrms.attendance.dto.response.LeaveRequestDto;
import com.forward.hrms.attendance.dto.response.LeaveTypeDto;
import com.forward.hrms.attendance.dto.response.LeaveTypeDtoMapper;
import com.forward.hrms.attendance.dto.response.OperatorDashboard;
import com.forward.hrms.attendance.dto.response.OrgPulseDto;
import com.forward.hrms.attendance.dto.response.OvertimeMineRow;
import com.forward.hrms.attendance.dto.response.OvertimeRequestDto;
import com.forward.hrms.attendance.dto.response.PunchDto;
import com.forward.hrms.attendance.dto.response.RegularizationRequestDto;
import com.forward.hrms.attendance.dto.response.ShiftOverrideDto;
import com.forward.hrms.attendance.dto.response.ShortLeaveRequestDto;
import com.forward.hrms.attendance.enums.LeaveRequestStatus;
import com.forward.hrms.attendance.repository.LeaveTypeRepository;
import com.forward.hrms.attendance.service.AttendanceListingService;
import com.forward.hrms.attendance.service.AttendanceQueryService;
import com.forward.hrms.attendance.service.CallerContext;
import com.forward.hrms.attendance.service.HolidayService;
import com.forward.hrms.attendance.service.LeaveBalanceService;
import com.forward.hrms.attendance.service.LeaveService;
import com.forward.hrms.attendance.service.OrgAttendanceQueryService;
import com.forward.hrms.attendance.service.OvertimeService;
import com.forward.hrms.attendance.service.RegularizationService;
import com.forward.hrms.attendance.service.ShiftOverrideService;
import com.forward.hrms.attendance.service.ShortLeaveService;
import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.cache.EmployeeSummaryMapper;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.employee.dto.response.EmployeeSummary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only composite service for FE-facing attendance surfaces
 * previously assembled in the BFF. All single-service composites land
 * here; the cross-service ones (manager team-today / -month using the
 * cross-svc {@code ManagerCoverageResolver}, and request-timeline that
 * pairs with audit) stay in BFF.
 *
 * <p>Uses {@link EmployeeDisplayCache} for employee summary
 * resolution — the cache lives in commons-cache and is populated via
 * {@code employee.events} from employee-service. Soft-fails when
 * Redis is down: the cache layer falls through to HTTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceCompositesService {

    private static final int APPLY_CONTEXT_HOLIDAY_DAYS = 90;
    private static final int OPERATOR_LIST_CAP = 200;
    private static final int OPERATOR_RECORDS_FETCH_SIZE = 500;
    private static final int APPROVALS_PAGE_SIZE = 200;
    private static final int OVERTIME_MINE_FETCH_SIZE = 500;

    private final CallerContext caller;
    private final AttendanceQueryService query;
    private final AttendanceListingService listing;
    private final HolidayService holidays;
    private final LeaveService leaves;
    private final LeaveBalanceService leaveBalances;
    private final LeaveTypeRepository leaveTypes;
    private final OvertimeService overtimes;
    private final RegularizationService regularizations;
    private final OrgAttendanceQueryService orgQuery;
    private final ShiftOverrideService shiftOverrides;
    private final ShortLeaveService shortLeaves;
    private final EmployeeDisplayCache displayCache;
    private final com.forward.hrms.attendance.service.PunchService punchService;

    // ===== /attendance/me/today/composite =====

    @Transactional(readOnly = true)
    public AttendanceToday today() {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        // The work date the caller is in right now, by the same shift window as
        // the device puller — a night-shift worker at 02:00 is still on last
        // night's record, a day-shift worker in OT past midnight too.
        LocalDate today = punchService.resolveWorkDate(me.id(), java.time.LocalDateTime.now());
        YearMonth ym = YearMonth.from(today);
        int year = today.getYear();

        List<AttendanceRecordDto> month = query.month(me.id(), ym);
        List<PunchDto> punches = query.punchesOn(me.id(), today);
        List<LeaveRequestDto> approvedLeaves = leavesForMe(me.id(), LeaveRequestStatus.APPROVED);
        List<HolidayDto> holidayList = holidays.listForYear(year);
        List<LeaveBalanceDto> balances = leaveBalances.myBalances(me.id(), null); // active cycle per type

        AttendanceRecordDto todayRec = month.stream()
                .filter(r -> today.equals(r.workDate()))
                .findFirst().orElse(null);

        LeaveRequestDto active = approvedLeaves.stream()
                .filter(l -> !today.isBefore(l.fromDate()) && !today.isAfter(l.toDate()))
                .findFirst().orElse(null);

        HolidayDto todayHoliday = holidayList.stream()
                .filter(h -> today.equals(h.holidayDate()))
                .findFirst().orElse(null);

        HolidayDto nextHoliday = holidayList.stream()
                .filter(h -> h.holidayDate().isAfter(today))
                .min(Comparator.comparing(HolidayDto::holidayDate))
                .orElse(null);

        AttendanceToday.NextLeave nextLeave = approvedLeaves.stream()
                .filter(l -> l.fromDate().isAfter(today))
                .min(Comparator.comparing(LeaveRequestDto::fromDate))
                .map(l -> new AttendanceToday.NextLeave(l.fromDate(), l.toDate(), l.leaveTypeCode()))
                .orElse(null);

        return new AttendanceToday(
                toTodayRecord(todayRec),
                punches,
                active,
                todayHoliday,
                new AttendanceToday.Upcoming(nextHoliday, nextLeave),
                summarize(balances));
    }

    private static AttendanceToday.TodayRecord toTodayRecord(AttendanceRecordDto r) {
        if (r == null) return null;
        return new AttendanceToday.TodayRecord(
                r.workDate(), r.status(), r.checkIn(), r.checkOut(),
                r.lateMinutes(), r.workedMinutes(),
                r.otNormalMinutes(), r.otExtraMinutes(), r.otSpecialMinutes(),
                null, null, null);
    }

    private static AttendanceToday.BalanceSummary summarize(List<LeaveBalanceDto> balances) {
        BigDecimal annual = BigDecimal.ZERO;
        BigDecimal sick = BigDecimal.ZERO;
        BigDecimal casual = BigDecimal.ZERO;
        for (LeaveBalanceDto b : balances) {
            if (b.available() == null) continue;
            String code = b.leaveTypeCode() == null ? "" : b.leaveTypeCode().toUpperCase();
            if (code.contains("ANNUAL")) annual = annual.add(b.available());
            else if (code.contains("SICK")) sick = sick.add(b.available());
            else if (code.contains("CASUAL")) casual = casual.add(b.available());
        }
        return new AttendanceToday.BalanceSummary(annual, sick, casual);
    }

    // ===== /attendance/me/month/composite =====

    @Transactional(readOnly = true)
    public AttendanceMonth month(String monthArg) {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        YearMonth ym = parseMonth(monthArg);

        List<AttendanceRecordDto> records = query.month(me.id(), ym);
        List<HolidayDto> holidayList = holidays.listForYear(ym.getYear());
        List<LeaveRequestDto> approvedLeaves = leavesForMe(me.id(), LeaveRequestStatus.APPROVED);

        return buildMonth(ym, records, holidayList, approvedLeaves);
    }

    private static YearMonth parseMonth(String monthArg) {
        return monthArg == null || monthArg.isBlank()
                ? YearMonth.now()
                : YearMonth.parse(monthArg);
    }

    private static AttendanceMonth buildMonth(YearMonth ym,
                                              List<AttendanceRecordDto> records,
                                              List<HolidayDto> holidays,
                                              List<LeaveRequestDto> leaves) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        Map<LocalDate, AttendanceRecordDto> recordByDate = new HashMap<>();
        for (AttendanceRecordDto r : records) recordByDate.put(r.workDate(), r);

        Map<LocalDate, HolidayDto> holidayByDate = new HashMap<>();
        for (HolidayDto h : holidays) {
            if (!h.holidayDate().isBefore(from) && !h.holidayDate().isAfter(to)) {
                holidayByDate.put(h.holidayDate(), h);
            }
        }
        Map<LocalDate, LeaveRequestDto> leaveByDate = new HashMap<>();
        for (LeaveRequestDto l : leaves) {
            if (l.toDate().isBefore(from) || l.fromDate().isAfter(to)) continue;
            LocalDate cur = l.fromDate().isBefore(from) ? from : l.fromDate();
            LocalDate end = l.toDate().isAfter(to) ? to : l.toDate();
            while (!cur.isAfter(end)) {
                leaveByDate.putIfAbsent(cur, l);
                cur = cur.plusDays(1);
            }
        }

        List<AttendanceMonth.Day> days = new ArrayList<>(ym.lengthOfMonth());
        int present = 0, absent = 0, leaveCnt = 0, holiday = 0, halfDay = 0, weekend = 0;
        int totalWorked = 0, totalOt = 0, totalLate = 0;

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            AttendanceRecordDto rec = recordByDate.get(d);
            HolidayDto hol = holidayByDate.get(d);
            LeaveRequestDto lev = leaveByDate.get(d);

            AttendanceMonth.DayLeave leaveOverlay = lev == null ? null
                    : new AttendanceMonth.DayLeave(lev.id(), lev.leaveTypeCode(),
                            lev.halfDay(), lev.halfDayPart());

            String kind = pickKind(d, rec, hol, lev);
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
            days.add(new AttendanceMonth.Day(d, kind, rec, hol, leaveOverlay));
        }

        return new AttendanceMonth(days,
                new AttendanceMonth.Summary(present, absent, leaveCnt, holiday,
                        halfDay, weekend, totalWorked, totalOt, totalLate));
    }

    private static String pickKind(LocalDate d, AttendanceRecordDto rec,
                                   HolidayDto hol, LeaveRequestDto lev) {
        if (hol != null) return "HOLIDAY";
        if (lev != null) return "LEAVE";
        if (rec != null) return rec.status();
        return d.getDayOfWeek() == DayOfWeek.SUNDAY ? "WEEKEND" : "ABSENT";
    }

    // ===== /overtime/me/with-records =====

    @Transactional(readOnly = true)
    public List<OvertimeMineRow> overtimeMine() {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        PageResponse<OvertimeRequestDto> page = overtimes.searchForEmployee(
                me.id(), null, null, null,
                PageRequest.of(0, OVERTIME_MINE_FETCH_SIZE, Sort.by("attendanceDate").descending()));
        List<OvertimeRequestDto> requests = page.content();
        if (requests.isEmpty()) return List.of();

        // Pull every distinct month covered by the requests and index
        // attendance records by date for the join.
        Set<YearMonth> months = new LinkedHashSet<>();
        for (OvertimeRequestDto ot : requests) months.add(YearMonth.from(ot.workDate()));

        Map<LocalDate, AttendanceRecordDto> byDate = new HashMap<>();
        for (YearMonth m : months) {
            for (AttendanceRecordDto r : query.month(me.id(), m)) {
                byDate.put(r.workDate(), r);
            }
        }

        List<OvertimeMineRow> out = new ArrayList<>(requests.size());
        for (OvertimeRequestDto ot : requests) {
            AttendanceRecordDto r = byDate.get(ot.workDate());
            if (r == null) {
                out.add(new OvertimeMineRow(ot, 0, 0, 0, 0));
            } else {
                out.add(new OvertimeMineRow(ot,
                        r.otNormalMinutes(), r.otExtraMinutes(), r.otSpecialMinutes(),
                        r.workedMinutes()));
            }
        }
        return out;
    }

    // ===== /attendance/records/with-employee =====

    @Transactional(readOnly = true)
    public AttendanceRecordsPage recordsWithEmployee(AttendanceListingService.RecordFilter f,
                                                      Pageable pageable) {
        PageResponse<AttendanceRecordDto> page = listing.listRecords(f,
                PageableDefaults.apply(pageable, "attendanceDate"));
        Map<Long, EmployeeSummary> summaries = resolveSummariesForRecords(page.content());
        List<AttendanceRecordsPage.Row> rows = new ArrayList<>(page.content().size());
        for (AttendanceRecordDto r : page.content()) {
            rows.add(new AttendanceRecordsPage.Row(r, summaries.get(r.employeeId())));
        }
        return new AttendanceRecordsPage(rows, page.page(), page.size(),
                page.totalElements(), page.totalPages(), page.sort());
    }

    // ===== /attendance/punches/with-employee =====

    @Transactional(readOnly = true)
    public AttendancePunchesPage punchesWithEmployee(AttendanceListingService.PunchFilter f,
                                                      Pageable pageable) {
        PageResponse<PunchDto> page = listing.listPunches(f,
                PageableDefaults.apply(pageable, "punchedAt"));
        Map<Long, EmployeeSummary> summaries = resolveSummariesForPunches(page.content());
        List<AttendancePunchesPage.Row> rows = new ArrayList<>(page.content().size());
        for (PunchDto p : page.content()) {
            rows.add(new AttendancePunchesPage.Row(p, summaries.get(p.employeeId())));
        }
        return new AttendancePunchesPage(rows, page.page(), page.size(),
                page.totalElements(), page.totalPages(), page.sort());
    }

    // ===== /attendance/operator/dashboard =====

    @Transactional(readOnly = true)
    public OperatorDashboard operatorDashboard(LocalDate dateArg,
                                               com.forward.hrms.attendance.enums.ShiftPeriod period) {
        LocalDate date = dateArg == null ? LocalDate.now() : dateArg;

        OrgPulseDto pulse = orgQuery.pulse(date, period);

        AttendanceListingService.RecordFilter recFilter = new AttendanceListingService.RecordFilter(
                date, null, null, null, null, null, null, null, null, null, period);
        PageResponse<AttendanceRecordDto> records = listing.listRecords(recFilter,
                PageRequest.of(0, OPERATOR_RECORDS_FETCH_SIZE,
                        Sort.by("attendanceDate").descending()));

        AttendanceListingService.PunchFilter punchFilter = new AttendanceListingService.PunchFilter(
                date, null, null, null, null, null, null, null, null, period);
        long totalPunches = listing.listPunches(punchFilter, PageRequest.of(0, 1))
                .totalElements();

        List<ShiftOverrideDto> overrides = shiftOverrides.listForDate(date);
        if (period != null) {
            Set<Long> onPeriod = new java.util.HashSet<>(orgQuery.employeeIdsOnShiftPeriod(date, period));
            overrides = overrides.stream().filter(o -> onPeriod.contains(o.employeeId())).toList();
        }
        // Pending regularizations: caller-scoped inbox (same shape as old BFF).
        PageResponse<RegularizationRequestDto> regs = regularizations.pendingApprovals(
                null, null, PageRequest.of(0, APPROVALS_PAGE_SIZE));

        List<AttendanceRecordDto> missingCheckout = new ArrayList<>();
        List<AttendanceRecordDto> late = new ArrayList<>();
        for (AttendanceRecordDto r : records.content()) {
            if (r.checkIn() != null && r.checkOut() == null
                    && missingCheckout.size() < OPERATOR_LIST_CAP) {
                missingCheckout.add(r);
            }
            if (r.lateMinutes() > 0 && late.size() < OPERATOR_LIST_CAP) {
                late.add(r);
            }
        }

        Set<Long> ids = new LinkedHashSet<>();
        for (AttendanceRecordDto r : missingCheckout) ids.add(r.employeeId());
        for (AttendanceRecordDto r : late) ids.add(r.employeeId());
        Map<Long, EmployeeSummary> summaries = resolveSummaries(ids);

        List<OperatorDashboard.AttendanceRow> missingRows = new ArrayList<>(missingCheckout.size());
        for (AttendanceRecordDto r : missingCheckout) {
            missingRows.add(new OperatorDashboard.AttendanceRow(r, summaries.get(r.employeeId())));
        }
        List<OperatorDashboard.AttendanceRow> lateRows = new ArrayList<>(late.size());
        for (AttendanceRecordDto r : late) {
            lateRows.add(new OperatorDashboard.AttendanceRow(r, summaries.get(r.employeeId())));
        }

        return new OperatorDashboard(
                date, pulse,
                totalPunches,
                overrides.size(),
                (int) regs.totalElements(),
                missingRows,
                lateRows);
    }

    // ===== /leaves/apply-context =====

    @Transactional(readOnly = true)
    public LeaveApplyContext leaveApplyContext() {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        int nextYear = today.plusDays(APPLY_CONTEXT_HOLIDAY_DAYS).getYear();
        LocalDate windowEnd = today.plusDays(APPLY_CONTEXT_HOLIDAY_DAYS);

        List<LeaveTypeDto> types = leaveTypes.findAllByActiveTrueOrderByCodeAsc().stream()
                .map(LeaveTypeDtoMapper::of).toList();
        List<LeaveBalanceDto> balances = leaveBalances.myBalances(me.id(), null); // active cycle per type

        List<HolidayDto> upcoming = new ArrayList<>();
        for (HolidayDto h : holidays.listForYear(currentYear)) {
            if (!h.holidayDate().isBefore(today) && !h.holidayDate().isAfter(windowEnd)) {
                upcoming.add(h);
            }
        }
        if (nextYear != currentYear) {
            for (HolidayDto h : holidays.listForYear(nextYear)) {
                if (!h.holidayDate().isBefore(today) && !h.holidayDate().isAfter(windowEnd)) {
                    upcoming.add(h);
                }
            }
        }
        upcoming.sort(Comparator.comparing(HolidayDto::holidayDate));
        return new LeaveApplyContext(types, balances, upcoming);
    }

    // ===== /approvals/inbox =====

    @Transactional(readOnly = true)
    public ApprovalsInbox approvalsInbox(String leaveQ, Long leaveTypeId, String shortLeaveQ,
                                         String overtimeQ,
                                         com.forward.hrms.attendance.enums.OvertimeType overtimeType,
                                         String regularizationQ) {
        // Each upstream pendingApprovals call enforces the caller's stage-aware
        // visibility. We pull a generous page (200) and let the FE paginate
        // within buckets. Counts come from the totalElements of each page.
        PageResponse<LeaveRequestDto> leavePage = leaves.pendingApprovals(
                null, null, leaveQ, leaveTypeId, PageRequest.of(0, APPROVALS_PAGE_SIZE));
        PageResponse<OvertimeRequestDto> otPage = overtimes.pendingApprovals(
                null, null, overtimeQ, overtimeType, PageRequest.of(0, APPROVALS_PAGE_SIZE));
        PageResponse<RegularizationRequestDto> regPage = regularizations.pendingApprovals(
                null, null, regularizationQ, PageRequest.of(0, APPROVALS_PAGE_SIZE));
        PageResponse<ShortLeaveRequestDto> shortLeavePage = shortLeaves.pendingApprovals(
                null, null, shortLeaveQ, PageRequest.of(0, APPROVALS_PAGE_SIZE));

        Set<Long> ids = new LinkedHashSet<>();
        for (LeaveRequestDto l : leavePage.content()) ids.add(l.employeeId());
        for (OvertimeRequestDto o : otPage.content()) ids.add(o.employeeId());
        for (RegularizationRequestDto r : regPage.content()) ids.add(r.employeeId());
        for (ShortLeaveRequestDto s : shortLeavePage.content()) ids.add(s.employeeId());
        Map<Long, EmployeeSummary> summaries = resolveSummaries(ids);

        List<ApprovalsInbox.LeaveRow> leaveRows = new ArrayList<>(leavePage.content().size());
        for (LeaveRequestDto l : leavePage.content()) {
            leaveRows.add(new ApprovalsInbox.LeaveRow(l, summaries.get(l.employeeId())));
        }
        // Pending OT carries no paid minutes yet — the row's projectedOtMinutes
        // is what approving it would credit.
        Map<String, ApprovalsInbox.OvertimeMinutes> otMinutes = overtimes.minutesByEmployeeDate(
                otPage.content().stream().map(OvertimeRequestDto::employeeId).distinct().toList(),
                otPage.content().stream().map(OvertimeRequestDto::workDate).toList());
        List<ApprovalsInbox.OvertimeRow> otRows = new ArrayList<>(otPage.content().size());
        for (OvertimeRequestDto o : otPage.content()) {
            otRows.add(new ApprovalsInbox.OvertimeRow(o, summaries.get(o.employeeId()),
                    otMinutes.getOrDefault(OvertimeService.minutesKey(o.employeeId(), o.workDate()),
                            ApprovalsInbox.OvertimeMinutes.NONE)));
        }
        List<ApprovalsInbox.RegularizationRow> regRows = new ArrayList<>(regPage.content().size());
        for (RegularizationRequestDto r : regPage.content()) {
            regRows.add(new ApprovalsInbox.RegularizationRow(r, summaries.get(r.employeeId())));
        }
        List<ApprovalsInbox.ShortLeaveRow> shortLeaveRows = new ArrayList<>(shortLeavePage.content().size());
        for (ShortLeaveRequestDto s : shortLeavePage.content()) {
            shortLeaveRows.add(new ApprovalsInbox.ShortLeaveRow(s, summaries.get(s.employeeId())));
        }
        // Counts use the total visible to the caller, not just this page.
        int total = (int) (leavePage.totalElements() + otPage.totalElements()
                + regPage.totalElements() + shortLeavePage.totalElements());
        return new ApprovalsInbox(leaveRows, otRows, regRows, shortLeaveRows,
                new ApprovalsInbox.Counts(
                        (int) leavePage.totalElements(),
                        (int) otPage.totalElements(),
                        (int) regPage.totalElements(),
                        (int) shortLeavePage.totalElements(),
                        total));
    }

    // ===== shared helpers =====

    private List<LeaveRequestDto> leavesForMe(long employeeId, LeaveRequestStatus status) {
        // searchForEmployee paginates; we ask for a generous slice. The same
        // bound the BFF used (PAGED_FETCH_SIZE = 500 in BFF AttendanceClient).
        return leaves.searchForEmployee(employeeId, status, null, null, null,
                PageRequest.of(0, 500, Sort.by("fromDate").descending())).content();
    }

    private Map<Long, EmployeeSummary> resolveSummariesForRecords(List<AttendanceRecordDto> rows) {
        if (rows.isEmpty()) return Map.of();
        Set<Long> ids = new LinkedHashSet<>();
        for (AttendanceRecordDto r : rows) ids.add(r.employeeId());
        return resolveSummaries(ids);
    }

    private Map<Long, EmployeeSummary> resolveSummariesForPunches(List<PunchDto> rows) {
        if (rows.isEmpty()) return Map.of();
        Set<Long> ids = new LinkedHashSet<>();
        for (PunchDto p : rows) ids.add(p.employeeId());
        return resolveSummaries(ids);
    }

    private Map<Long, EmployeeSummary> resolveSummaries(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<Long, EmployeeDisplay> hits = displayCache.getMany(ids);
        Map<Long, EmployeeSummary> out = new HashMap<>(hits.size());
        for (Map.Entry<Long, EmployeeDisplay> en : hits.entrySet()) {
            out.put(en.getKey(), EmployeeSummaryMapper.toSummary(en.getValue()));
        }
        return out;
    }
}

package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.dto.response.AttendanceRecordDto;
import com.forward.hrms.attendance.dto.response.PunchDto;
import com.forward.hrms.attendance.entity.AttendancePunch;
import com.forward.hrms.attendance.entity.AttendanceRecord;
import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.enums.PunchSource;
import com.forward.hrms.attendance.enums.ShiftPeriod;
import com.forward.hrms.attendance.repository.AttendancePunchRepository;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;
import com.forward.hrms.attendance.dto.response.AttendanceRecordDtoMapper;
import com.forward.hrms.attendance.dto.response.PunchDtoMapper;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Org-wide listing for HR / admin / attendance-operator dashboards. Filters
 * are layered: the caller-supplied dept/section/employeeCode are pre-resolved
 * to a set of employee ids (via employee-service), then the date + status +
 * source filters run as paged JPQL on attendance-service tables.
 *
 * <p>Defaults are intentional: when no date is supplied we scope to today —
 * the operator's everyday case is "what happened today". This keeps any
 * accidentally-unscoped query small.
 */
@Service
@RequiredArgsConstructor
public class AttendanceListingService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 500;

    private final AttendanceRecordRepository records;
    private final AttendancePunchRepository punches;
    private final EmployeeClient employees;

    @Transactional(readOnly = true)
    public PageResponse<AttendanceRecordDto> listRecords(RecordFilter f, Pageable pageable) {
        DateRange range = resolveRange(f.date(), f.from(), f.to());
        Pageable effective = PageableDefaults.apply(pageable, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE,
                Sort.by(Sort.Order.desc("attendanceDate"), Sort.Order.asc("employeeId")));

        Optional<List<Long>> ids = resolveEmployeeIds(f.employeeId(), f.employeeCode(),
                f.departmentId(), f.sectionId(), range, f.period());
        if (ids.isPresent() && ids.get().isEmpty()) {
            return PageResponse.from(empty(effective));
        }
        boolean lateOnly = Boolean.TRUE.equals(f.lateOnly());
        boolean missingCheckout = Boolean.TRUE.equals(f.missingCheckout());

        Page<AttendanceRecord> page = ids.isPresent()
                ? records.searchInIds(range.from(), range.to(), ids.get(),
                        f.status(), lateOnly, missingCheckout, effective)
                : records.search(range.from(), range.to(), f.employeeId(),
                        f.status(), lateOnly, missingCheckout, effective);
        return PageResponse.from(page.map(AttendanceRecordDtoMapper::of));
    }

    @Transactional(readOnly = true)
    public PageResponse<PunchDto> listPunches(PunchFilter f, Pageable pageable) {
        DateRange range = resolveRange(f.date(), f.from(), f.to());
        Pageable effective = PageableDefaults.apply(pageable, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE,
                Sort.by(Sort.Order.desc("punchedAt")));

        Optional<List<Long>> ids = resolveEmployeeIds(f.employeeId(), f.employeeCode(),
                f.departmentId(), f.sectionId(), range, f.period());
        if (ids.isPresent() && ids.get().isEmpty()) {
            return PageResponse.from(empty(effective));
        }

        Page<AttendancePunch> page = ids.isPresent()
                ? punches.searchInIds(range.from(), range.to(), ids.get(),
                        f.source(), f.deviceId(), effective)
                : punches.search(range.from(), range.to(), f.employeeId(),
                        f.source(), f.deviceId(), effective);
        return PageResponse.from(page.map(PunchDtoMapper::of));
    }

    /**
     * Resolves the optional org/employee filters into a concrete id set.
     * <ul>
     *   <li>{@code employeeId} wins outright — short-circuits the lookup.</li>
     *   <li>{@code employeeCode} resolves via employee-service.</li>
     *   <li>{@code departmentId}/{@code sectionId} resolve to the matching
     *       employee ids via employee-service.</li>
     *   <li>None supplied → empty optional (caller uses the un-scoped repo
     *       method).</li>
     *   <li>{@code period} narrows any of the above to employees whose
     *       record in the range sits on a shift of that period.</li>
     * </ul>
     */
    private Optional<List<Long>> resolveEmployeeIds(Long employeeId, String employeeCode,
                                                    Long departmentId, Long sectionId,
                                                    DateRange range, ShiftPeriod period) {
        Optional<List<Long>> ids = resolveEmployeeIds(employeeId, employeeCode, departmentId, sectionId);
        if (period == null) return ids;
        List<Long> onPeriod = records.findEmployeeIdsOnShiftPeriod(range.from(), range.to(), period);
        if (employeeId != null) {
            return Optional.of(onPeriod.contains(employeeId) ? List.of(employeeId) : List.of());
        }
        if (ids.isEmpty()) return Optional.of(onPeriod);
        java.util.Set<Long> keep = new java.util.HashSet<>(onPeriod);
        return Optional.of(ids.get().stream().filter(keep::contains).toList());
    }

    private Optional<List<Long>> resolveEmployeeIds(Long employeeId, String employeeCode,
                                                    Long departmentId, Long sectionId) {
        if (employeeId != null) {
            // Pushed straight through as the single-employee filter; no upstream call needed.
            return Optional.empty();
        }
        if (employeeCode != null && !employeeCode.isBlank()) {
            return Optional.of(employees.findByEmployeeCode(employeeCode.trim())
                    .map(e -> List.of(e.id()))
                    .orElseGet(List::of));
        }
        if (departmentId != null || sectionId != null) {
            return Optional.of(employees.findIdsByOrg(departmentId, sectionId));
        }
        return Optional.empty();
    }

    private static DateRange resolveRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date != null) return new DateRange(date, date);
        if (from == null && to == null) {
            LocalDate today = LocalDate.now();
            return new DateRange(today, today);
        }
        LocalDate lo = from != null ? from : to;
        LocalDate hi = to != null ? to : from;
        return new DateRange(lo, hi);
    }

    private static <T> Page<T> empty(Pageable pageable) {
        return new PageImpl<>(List.of(), pageable, 0);
    }

    private record DateRange(LocalDate from, LocalDate to) {}

    public record RecordFilter(
            LocalDate date,
            LocalDate from,
            LocalDate to,
            Long employeeId,
            String employeeCode,
            Long departmentId,
            Long sectionId,
            AttendanceStatus status,
            Boolean lateOnly,
            Boolean missingCheckout,
            ShiftPeriod period
    ) {}

    public record PunchFilter(
            LocalDate date,
            LocalDate from,
            LocalDate to,
            Long employeeId,
            String employeeCode,
            Long departmentId,
            Long sectionId,
            PunchSource source,
            Long deviceId,
            ShiftPeriod period
    ) {}
}

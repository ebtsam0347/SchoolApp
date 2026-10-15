package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.request.CanteenAttendanceExcelUploadByEmployeeCode;
import com.forward.hrms.attendance.dto.request.CreateCanteenAttendanceRequest;
import com.forward.hrms.attendance.dto.request.UpdateCanteenAttendanceRequest;
import com.forward.hrms.attendance.dto.response.CanteenAttendanceDto;
import com.forward.hrms.attendance.dto.response.CanteenAttendanceRangeCountDto;
import com.forward.hrms.attendance.entity.AttendanceEmployee;
import com.forward.hrms.attendance.entity.CanteenAttendance;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.attendance.repository.CanteenAttendanceRepository;
import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.cache.EmployeeSummaryMapper;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.employee.dto.response.EmployeeSummary;

import com.forward.hrms.employee.enums.EmploymentStatus;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canteen meal register — the editable record of who ate and how often,
 * successor to legacy {@code PayRoll_meal}.
 *
 * <p>Entries are manual today: the canteen clerk types the employee id
 * at the counter. The employee must exist in the local
 * {@code attendance_employees} mirror and be ACTIVE, so a terminated
 * employee cannot be fed onto the register. {@code device_ip} stays null
 * on manual rows — it is there for the canteen device poller.
 *
 * <p>Employee display fields ride the shared {@link EmployeeDisplayCache}
 * and are mapped with {@link EmployeeSummaryMapper}, same as the
 * attendance composites — no denormalized columns, no per-row HTTP.
 */
@Service
@RequiredArgsConstructor
public class CanteenAttendanceService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int MIN_CANTEEN_COUNT = 1;
    private static final int MAX_CANTEEN_COUNT = 5;
    /** Guards an accidental multi-year scan from the date-range picker. */
    private static final long MAX_RANGE_DAYS = 366;
    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.desc("attendanceDate"), Sort.Order.desc("attendanceTime"));

    private final CanteenAttendanceRepository canteenAttendanceRepository;
    private final AttendanceEmployeeRepository attendanceEmployees;
    private final EmployeeDisplayCache displayCache;
    private final AuditEmitter audit;

    /**
     * Register listing: a date range, optionally narrowed to one
     * employee. Defaults to today when the caller sends no range —
     * the canteen home screen.
     */
    @Transactional(readOnly = true)
    public PageResponse<CanteenAttendanceDto> list(Filter f, Pageable pageable) {
        DateRange range = resolveRange(f.from(), f.to());
        Pageable effective = PageableDefaults.apply(pageable, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE, DEFAULT_SORT);

        Page<CanteenAttendance> page = canteenAttendanceRepository.search(range.from(), range.to(), f.employeeId(), effective);
        Map<Long, EmployeeSummary> employees = resolveSummaries(
                page.getContent().stream().map(CanteenAttendance::getEmployeeId).toList());
        return PageResponse.from(page.map(e -> toDto(e, employees)));
    }

    @Transactional(readOnly = true)
    public CanteenAttendanceDto get(long id) {
        CanteenAttendance e = require(id);
        return toDto(e, resolveSummaries(List.of(e.getEmployeeId())));
    }

    @Transactional(readOnly = true)
    public CanteenAttendanceRangeCountDto getEmployeeAttendanceRangeCount(long employeeId,
                                                                          LocalDate from,
                                                                          LocalDate to) {
        DateRange range = resolveRange(from, to);
        AttendanceEmployee employee = attendanceEmployees.findByEmployeeId(employeeId)
                .orElseThrow(() -> new NotFoundError(
                        AttendanceErrorCode.EMPLOYEE_NOT_FOUND, "employee not found: " + employeeId));
        long attendanceEntryCount = canteenAttendanceRepository.countByEmployeeIdAndAttendanceDateBetween(
                employeeId, range.from(), range.to());
        Long meals = canteenAttendanceRepository.sumCountByEmployeeIdAndAttendanceDateBetween(employeeId, range.from(), range.to());
        long canteenMealCount = meals == null ? 0 : meals;
        return new CanteenAttendanceRangeCountDto(
                employeeId,
                employee.getEmployeeCode(),
                attendanceEntryCount,
                canteenMealCount);
    }

    @Transactional
    public CanteenAttendanceDto create(CreateCanteenAttendanceRequest req) {
        AttendanceEmployee employee = requireActiveEmployee(req.employeeId());

        LocalDateTime now = LocalDateTime.now();
        LocalDate date = req.attendanceDate() != null ? req.attendanceDate() : now.toLocalDate();
        LocalTime time = req.attendanceTime() != null
                ? req.attendanceTime().truncatedTo(ChronoUnit.SECONDS)
                : now.toLocalTime().truncatedTo(ChronoUnit.SECONDS);
        rejectFutureDate(date);
        int count = requireValidCount(req.count() != null ? req.count() : 1);
        if (canteenAttendanceRepository.existsByEmployeeIdAndAttendanceDate(
                employee.getEmployeeId(), date)) {
            throw new ConflictError(AttendanceErrorCode.CANTEEN_ENTRY_DUPLICATE,
                    "canteen entry already recorded for this employee at " + date);
        }

        CanteenAttendance e = new CanteenAttendance();
        e.setEmployeeId(employee.getEmployeeId());
        e.setAttendanceDate(date);
        e.setAttendanceTime(time);
        e.setCount(count);
        e = canteenAttendanceRepository.save(e);

        audit.emit("attendance_canteen", String.valueOf(e.getId()), "created", null, snapshot(e), false);
        return toDto(e, resolveSummaries(List.of(e.getEmployeeId())));
    }

    @Transactional
    public CanteenAttendanceDto update(long id, UpdateCanteenAttendanceRequest req) {
        CanteenAttendance e = require(id);
        Map<String, Object> before = snapshot(e);

        LocalTime time = req.attendanceTime() != null
                ? req.attendanceTime().truncatedTo(ChronoUnit.SECONDS)
                : e.getAttendanceTime();

        e.setAttendanceTime(time);
        if (req.count() != null) e.setCount(requireValidCount(req.count()));

        audit.emit("attendance_canteen", String.valueOf(e.getId()), "updated", before, snapshot(e), false);
        return toDto(e, resolveSummaries(List.of(e.getEmployeeId())));
    }

    @Transactional
    public void delete(long id) {
        CanteenAttendance e = require(id);
        Map<String, Object> before = snapshot(e);
        canteenAttendanceRepository.delete(e);
        audit.emit("attendance_canteen", String.valueOf(id), "deleted", before, null, false);
    }

    @Transactional
    public Map<String, Map<String, Object>> uploadCanteenAttendanceExcelByEmployeeCode(CanteenAttendanceExcelUploadByEmployeeCode request) {
        if (request == null || request.excelUploadByEmployeeCode() == null || request.excelUploadByEmployeeCode().isEmpty()) {
            throw new UnprocessableError(AttendanceErrorCode.CANTEEN_UPLOAD_INVALID, "Employee code list is empty");
        }

        rejectFutureDate(request.attendanceDate());
        LocalTime attendanceTime = request.attendanceTime().truncatedTo(ChronoUnit.SECONDS);
        Map<String, Object> success = new HashMap<>();
        Map<String, Object> error = new HashMap<>();
        Set<String> seenEmployeeCodes = new LinkedHashSet<>();

        for (String rawEmployeeCode : request.excelUploadByEmployeeCode().keySet()) {
            String employeeCode = rawEmployeeCode == null ? "" : rawEmployeeCode.trim();
            if (employeeCode.isEmpty()) {
                error.put(String.valueOf(rawEmployeeCode), "Employee code is empty");
                continue;
            }
            if (!seenEmployeeCodes.add(employeeCode)) {
                error.put(employeeCode, "Duplicate entry in the request");
                continue;
            }

            AttendanceEmployee employee = attendanceEmployees.findByEmployeeCode(employeeCode).orElse(null);
            if (employee == null) {
                error.put(employeeCode, "Employee not found");
                continue;
            }
            if (!EmploymentStatus.ACTIVE.name().equals(employee.getEmploymentStatus())) {
                error.put(employeeCode, "Employee is: " + employee.getEmploymentStatus());
                continue;
            }
            boolean existsByEmployeeIdAndAttendanceDate = canteenAttendanceRepository.existsByEmployeeIdAndAttendanceDate(
                    employee.getEmployeeId(), request.attendanceDate());
            if (existsByEmployeeIdAndAttendanceDate) {
                error.put(employeeCode, "Canteen Attendance already exists for date: " + request.attendanceDate());
                continue;
            }
            Integer requestedCount = request.excelUploadByEmployeeCode().get(rawEmployeeCode);
            if (requestedCount == null) {
                requestedCount = request.excelUploadByEmployeeCode().get(employeeCode);
            }
            int count = requireValidCount(requestedCount == null ? 1 : requestedCount);

            CanteenAttendance e = new CanteenAttendance();
            e.setEmployeeId(employee.getEmployeeId());
            e.setAttendanceDate(request.attendanceDate());
            e.setAttendanceTime(attendanceTime);
            e.setCount(count);
            e = canteenAttendanceRepository.save(e);
            success.put(employeeCode, "Canteen Attendance recorded for date: " + request.attendanceDate());
            audit.emit("attendance_canteen", String.valueOf(e.getId()), "created", null, snapshot(e), false);
        }

        Map<String, Map<String, Object>> responseMap = new HashMap<>();
        if (!success.isEmpty()) {
            responseMap.put("success", success);
        }
        if (!error.isEmpty()) {
            responseMap.put("error", error);
        }
        return responseMap;
    }


    private CanteenAttendance require(long id) {
        return canteenAttendanceRepository.findById(id).orElseThrow(() -> new NotFoundError(
                AttendanceErrorCode.CANTEEN_ENTRY_NOT_FOUND, "canteen entry not found"));
    }

    /**
     * The employee must be known to the local mirror and ACTIVE — a
     * terminated or suspended employee does not draw meals.
     */
    private AttendanceEmployee requireActiveEmployee(long employeeId) {
        AttendanceEmployee employee = attendanceEmployees.findByEmployeeId(employeeId)
                .orElseThrow(() -> new NotFoundError(
                        AttendanceErrorCode.EMPLOYEE_NOT_FOUND, "employee not found: " + employeeId));
        if (!"ACTIVE".equals(employee.getEmploymentStatus())) {
            throw new UnprocessableError(AttendanceErrorCode.EMPLOYEE_NOT_ACTIVE,
                    "employee " + employee.getEmployeeCode() + " is " + employee.getEmploymentStatus());
        }
        return employee;
    }

    /** Same batch-resolve shape the attendance composites use. */
    private Map<Long, EmployeeSummary> resolveSummaries(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Set<Long> distinct = new LinkedHashSet<>(ids);
        Map<Long, EmployeeDisplay> hits = displayCache.getMany(distinct);
        Map<Long, EmployeeSummary> out = new HashMap<>(hits.size());
        for (Map.Entry<Long, EmployeeDisplay> en : hits.entrySet()) {
            out.put(en.getKey(), EmployeeSummaryMapper.toSummary(en.getValue()));
        }
        return out;
    }

    private static void rejectFutureDate(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            throw new UnprocessableError(AttendanceErrorCode.CANTEEN_RANGE_INVALID,
                    "canteen entry cannot be dated in the future");
        }
    }

    private static int requireValidCount(Integer count) {
        if (count < MIN_CANTEEN_COUNT || count > MAX_CANTEEN_COUNT) {
            throw new UnprocessableError(AttendanceErrorCode.CANTEEN_UPLOAD_INVALID,
                    "canteen count must be between " + MIN_CANTEEN_COUNT + " and " + MAX_CANTEEN_COUNT);
        }
        return count;
    }

    private static DateRange resolveRange(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            LocalDate today = LocalDate.now();
            return new DateRange(today, today);
        }
        LocalDate lo = from != null ? from : to;
        LocalDate hi = to != null ? to : from;
        if (hi.isBefore(lo)) {
            throw new UnprocessableError(AttendanceErrorCode.CANTEEN_RANGE_INVALID, "`to` is before `from`");
        }
        if (ChronoUnit.DAYS.between(lo, hi) > MAX_RANGE_DAYS) {
            throw new UnprocessableError(AttendanceErrorCode.CANTEEN_RANGE_INVALID,
                    "date range capped at " + MAX_RANGE_DAYS + " days");
        }
        return new DateRange(lo, hi);
    }

    private static CanteenAttendanceDto toDto(CanteenAttendance e, Map<Long, EmployeeSummary> employees) {
        return new CanteenAttendanceDto(
                e.getId(),
                e.getEmployeeId(),
                employees.get(e.getEmployeeId()),
                e.getAttendanceDate(),
                e.getAttendanceTime(),
                e.getDeviceIp(),
                e.getCount() == null ? 1 : e.getCount(),
                e.getCreatedAt(),
                e.getCreatedBy(),
                e.getUpdatedAt(),
                e.getUpdatedBy());
    }

    private static Map<String, Object> snapshot(CanteenAttendance e) {
        Map<String, Object> m = new HashMap<>();
        m.put("employeeId", e.getEmployeeId());
        m.put("attendanceDate", e.getAttendanceDate().toString());
        m.put("attendanceTime", e.getAttendanceTime().toString());
        m.put("deviceIp", e.getDeviceIp());
        m.put("count", e.getCount());
        return m;
    }
    private record DateRange(LocalDate from, LocalDate to) {}

    /** Listing filters: date range (defaults to today) plus an optional employee. */
    public record Filter(LocalDate from, LocalDate to, Long employeeId) {}
}

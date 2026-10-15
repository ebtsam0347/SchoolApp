package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.dto.request.CanteenAttendanceExcelUploadByEmployeeCode;
import com.forward.hrms.attendance.dto.request.CreateCanteenAttendanceRequest;
import com.forward.hrms.attendance.dto.request.UpdateCanteenAttendanceRequest;
import com.forward.hrms.attendance.dto.response.AttendanceDeviceDto;
import com.forward.hrms.attendance.dto.response.CanteenAttendanceDto;
import com.forward.hrms.attendance.dto.response.CanteenAttendanceRangeCountDto;
import com.forward.hrms.attendance.service.AttendanceDeviceService;
import com.forward.hrms.attendance.service.CanteenAttendanceService;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.SortWhitelist;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Canteen", description = "Canteen (meal) register — date-range listing, manual entry, " +
        "edit, delete. Gated on the canteen permissions (`canteen.read` / `canteen.create` / " +
        "`canteen.write` / `canteen.delete`), held by the CANTEEN role and ADMIN.")
@RestController
@RequestMapping("/canteen/attendance")
@RequiredArgsConstructor
public class CanteenController {

    private static final SortWhitelist SORTABLE =
            SortWhitelist.of("attendanceDate", "attendanceTime", "employeeId", "count", "createdAt");

    private final CanteenAttendanceService canteen;
    private final AttendanceDeviceService attendanceDeviceService;

    @Operation(summary = "List canteen attendance",
            description = "Paginated meal register over a date range, optionally narrowed to one " +
                    "employee. With no range supplied the response is scoped to today " +
                    "(server-local) — the canteen home screen. Range capped at 366 days. " +
                    "Default sort `attendanceDate,desc;attendanceTime,desc`; default page size 50 " +
                    "(max 500). Requires `canteen.read`.")
    @ApiResponse(responseCode = "200", description = "Page of canteen entries.")
    @ApiResponse(responseCode = "422", description = "Inverted or oversized date range.")
    @GetMapping
    @RequirePermission("canteen.read")
    public PageResponse<CanteenAttendanceDto> list(
            @Parameter(description = "Inclusive lower bound. Defaults to today when from/to are both omitted.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Inclusive upper bound. Defaults to `from` when omitted.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Filter by employee id.")
            @RequestParam(required = false) Long employeeId,
            @Parameter(hidden = true) Pageable pageable) {
        SORTABLE.validate(pageable.getSort());
        return canteen.list(new CanteenAttendanceService.Filter(from, to, employeeId), pageable);
    }

    @Operation(summary = "Get one canteen entry",
            description = "Requires `canteen.read`.")
    @ApiResponse(responseCode = "404", description = "No such entry.")
    @GetMapping("/{id}")
    @RequirePermission("canteen.read")
    public CanteenAttendanceDto get(@PathVariable long id) {
        return canteen.get(id);
    }

    @Operation(summary = "Record a canteen entry",
            description = "The counter flow: the clerk enters the employee id as the meal is " +
                    "served. Employee must exist and be ACTIVE. Date/time default to now; the " +
                    "entry cannot be dated in the future. `count` defaults to 1. " +
                    "Requires `canteen.create`.")
    @ApiResponse(responseCode = "201", description = "Entry recorded.")
    @ApiResponse(responseCode = "404", description = "Unknown employee id.")
    @ApiResponse(responseCode = "409", description = "Entry already recorded for this employee at that date + time.")
    @ApiResponse(responseCode = "422", description = "Employee is not ACTIVE, or the date is in the future.")
    @PostMapping
    @RequirePermission("canteen.create")
    @ResponseStatus(HttpStatus.CREATED)
    public CanteenAttendanceDto create(@Valid @RequestBody CreateCanteenAttendanceRequest req) {
        return canteen.create(req);
    }

    @Operation(summary = "Edit a canteen entry",
            description = "Patch — null fields stay unchanged. The employee on an entry is " +
                    "immutable; delete and re-add if the wrong employee was recorded. " +
                    "Requires `canteen.write`.")
    @ApiResponse(responseCode = "404", description = "No such entry.")
    @ApiResponse(responseCode = "409", description = "Target date + time already has an entry for this employee.")
    @ApiResponse(responseCode = "422", description = "Date is in the future.")
    @PatchMapping("/{id}")
    @RequirePermission("canteen.write")
    public CanteenAttendanceDto update(@PathVariable long id,
                                       @Valid @RequestBody UpdateCanteenAttendanceRequest req) {
        return canteen.update(id, req);
    }

    @Operation(summary = "Delete a canteen entry",
            description = "Requires `canteen.delete`.")
    @ApiResponse(responseCode = "204", description = "Deleted.")
    @ApiResponse(responseCode = "404", description = "No such entry.")
    @DeleteMapping("/{id}")
    @RequirePermission("canteen.delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        canteen.delete(id);
    }

    @Operation(summary = "List of Devices for canteen attendance", description = "Requires `canteen.write`.")
    @RequirePermission("canteen.write")
    @GetMapping("/devices")
    public List<AttendanceDeviceDto> canteenDevices(){
        return attendanceDeviceService.getCanteenDevices();
    }

    @Operation(summary = "Canteen usage summary", description = "Date range count of canteen attendance entries")
    @GetMapping("/employee/{employeeId}/summary")
    @RequirePermission("canteen.read")
    public CanteenAttendanceRangeCountDto getEmployeeAttendanceRangeCount(
            @PathVariable long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return canteen.getEmployeeAttendanceRangeCount(employeeId, from, to);
    }

    @Operation(summary = "Upload canteen attendance excel (type is byEmployeeCode)", description = "Requires `canteen.create`.")
    @PostMapping("/upload-excel/byEmployeeCode")
    @RequirePermission("canteen.create")
    public Map<String, Map<String, Object>> uploadCanteenAttendanceExcelByEmployeeCode(@RequestBody CanteenAttendanceExcelUploadByEmployeeCode request) {
        return canteen.uploadCanteenAttendanceExcelByEmployeeCode(request);
    }
}

package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.dto.request.CreateHolidayRequest;
import com.forward.hrms.attendance.dto.request.UpdateHolidayRequest;
import com.forward.hrms.attendance.dto.response.HolidayDto;
import com.forward.hrms.attendance.service.HolidayService;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

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

@Tag(name = "Holidays", description = "Holiday calendar — listing for everyone, CRUD for HR. Requires `attendance.read` / `attendance.admin`.")
@RestController
@RequestMapping("/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidays;

    @Operation(summary = "List holidays for a year",
            description = "Requires `attendance.read`.")
    @GetMapping
    @RequirePermission("attendance.read")
    public List<HolidayDto> list(@RequestParam(required = false) Integer year) {
        return holidays.listForYear(year == null ? LocalDate.now().getYear() : year);
    }

    @Operation(summary = "Create a holiday (HR)",
            description = "Requires `attendance.admin`.")
    @PostMapping
    @RequirePermission("attendance.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayDto create(@Valid @RequestBody CreateHolidayRequest req) {
        return holidays.create(req);
    }

    @Operation(summary = "Update a holiday (HR)",
            description = "Patch — null fields stay unchanged. The holiday date is immutable; " +
                    "delete and recreate if the date itself is wrong. Requires `attendance.admin`.")
    @PatchMapping("/{id}")
    @RequirePermission("attendance.admin")
    public HolidayDto update(@PathVariable long id, @Valid @RequestBody UpdateHolidayRequest req) {
        return holidays.update(id, req);
    }

    @Operation(summary = "Delete a holiday (HR)",
            description = "Requires `attendance.admin`.")
    @DeleteMapping("/{id}")
    @RequirePermission("attendance.admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        holidays.delete(id);
    }
}

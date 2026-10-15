package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.response.AggregateDto;
import com.forward.hrms.employee.dto.response.HeadcountTrendDto;
import com.forward.hrms.employee.service.AggregateService;
import com.forward.hrms.employee.service.HeadcountTrendService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Employees — Aggregates",
        description = "Grouped headcount for the People-overview charts. One groupBy per call; " +
                "the UI fans out for multiple breakdowns. Requires `employee.read`.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/employees")
public class AggregateController {

    private final AggregateService service;
    private final HeadcountTrendService trends;

    @Operation(summary = "Headcount grouped by a single dimension",
            description = "Returns one bucket per group key with optional human-readable label. " +
                    "All non-status groupings exclude TERMINATED rows; the `status` grouping includes them. " +
                    "Requires `employee.read`.")
    @ApiResponse(responseCode = "422", description = "Unsupported groupBy.")
    @GetMapping("/aggregate")
    @RequirePermission("employee.read")
    public AggregateDto aggregate(
            @Parameter(description = "One of: status, employmentType, gender, " +
                    "department, section, grade, designation.", required = true)
            @RequestParam String groupBy) {
        return service.aggregate(groupBy);
    }

    @Operation(summary = "Joiners / leavers / end-of-month headcount over a trailing window",
            description = "Three SQL reads (joiner counts by month, leaver counts by month, one " +
                    "anchor headcount-as-of) walked forward to produce the per-month series. " +
                    "Window is 1–24 months; defaults to 12. Requires `employee.read`.")
    @GetMapping("/headcount-trend")
    @RequirePermission("employee.read")
    public HeadcountTrendDto headcountTrend(
            @Parameter(description = "Number of months in the trailing window. Clamped to [1, 24].")
            @RequestParam(defaultValue = "12") int months) {
        return trends.trend(months);
    }
}

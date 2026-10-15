package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.response.HeadcountTrendDto;
import com.forward.hrms.employee.service.HeadcountTrendService;
import com.forward.hrms.employee.service.PeopleAnalyticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * People-analytics JSON endpoints — one round-trip per chart on the
 * dashboard. Headcount + joiners/leavers trend reuses the existing
 * {@link HeadcountTrendService}; attrition + tenure bands come from
 * {@link PeopleAnalyticsService}.
 */
@Tag(name = "People analytics",
        description = "JSON aggregations for the /analytics dashboards. " +
                "Requires `employee.analytics.read`.")
@RestController
@RequestMapping("/analytics/employees")
@RequiredArgsConstructor
public class AnalyticsController {

    private final HeadcountTrendService headcountTrend;
    private final PeopleAnalyticsService analytics;

    @Operation(summary = "Headcount + joiners/leavers trend",
            description = "One row per month in the trailing window with joiners, leavers, " +
                    "and end-of-month headcount. Window 1..24 months; defaults to 12.")
    @GetMapping("/headcount-trend")
    @RequirePermission("employee.analytics.read")
    public HeadcountTrendDto headcountTrend(
            @Parameter(description = "Trailing window in months. Clamped to [1, 24].")
            @RequestParam(defaultValue = "12") int months) {
        return headcountTrend.trend(months);
    }

    @Operation(summary = "Monthly attrition + rolling-12m rate",
            description = "Per-month leavers, start/end headcount, and rate% (= leavers / avg-hc). " +
                    "Plus a rolling-12m rate aggregating the last 12 months.")
    @GetMapping("/attrition")
    @RequirePermission("employee.analytics.read")
    public PeopleAnalyticsService.AttritionTrendDto attrition(
            @RequestParam(defaultValue = "12") int months) {
        return analytics.attrition(months);
    }

    @Operation(summary = "Tenure-band snapshot",
            description = "Active employees bucketed into <1y / 1–3y / 3–5y / 5–10y / 10y+. " +
                    "Optional {@code splitBy=dept|grade} returns a per-bucket breakdown.")
    @GetMapping("/tenure-bands")
    @RequirePermission("employee.analytics.read")
    public PeopleAnalyticsService.TenureBandsDto tenureBands(
            @Parameter(description = "Optional grouping: dept | grade.")
            @RequestParam(required = false) String splitBy) {
        return analytics.tenureBands(splitBy);
    }
}

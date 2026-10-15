package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.service.PayrollAnalyticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payroll analytics",
        description = "JSON aggregations for the /analytics dashboards. " +
                "Requires `payroll.analytics.read`.")
@RestController
@RequestMapping("/analytics/payroll")
@RequiredArgsConstructor
public class AnalyticsController {

    private final PayrollAnalyticsService analytics;

    @Operation(summary = "Monthly payroll cost trend",
            description = "Per month: employer cost (gross + employer-PESSI) + headcount + " +
                    "cost-per-head + breakdown (gross / EOBI / PESSI / tax / net). " +
                    "Window 1..36 months; defaults to 24.")
    @GetMapping("/cost-trend")
    @RequirePermission("payroll.analytics.read")
    public PayrollAnalyticsService.CostTrendDto costTrend(
            @RequestParam(defaultValue = "24") int months) {
        return analytics.costTrend(months);
    }

    @Operation(summary = "Salary distribution snapshot",
            description = "Min / p25 / median / p75 / max of the current basic across every " +
                    "employee. Includes the raw sorted list when ≤5000 employees so the FE can " +
                    "re-bucket by grade with an employee-service join.")
    @GetMapping("/salary-distribution")
    @RequirePermission("payroll.analytics.read")
    public PayrollAnalyticsService.SalaryDistributionDto salaryDistribution() {
        return analytics.salaryDistribution();
    }
}

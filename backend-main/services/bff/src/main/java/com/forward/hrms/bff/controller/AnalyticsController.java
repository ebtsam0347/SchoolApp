package com.forward.hrms.bff.controller;

import com.forward.hrms.bff.service.ExecutiveDashboardPdfService;
import com.forward.hrms.bff.service.ExecutiveOverviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Cross-service analytics composites. Per-service analytics live on
 * their owning service ({@code /analytics/employees}, etc.); this
 * controller hosts only the multi-service rollups.
 */
@Tag(name = "Analytics composites",
        description = "Cross-service analytics roll-ups for the management dashboard.")
@RestController
@RequestMapping("/bff/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final ExecutiveOverviewService overview;
    private final ExecutiveDashboardPdfService dashboardPdf;

    @Operation(summary = "Executive overview — headline numbers",
            description = "Single payload combining the latest values from all four service-side " +
                    "analytics endpoints: headcount, attrition rolling-12m, on-time%, " +
                    "absenteeism%, OT hours, payroll cost. Each field falls back to null if its " +
                    "downstream is unreachable so the dashboard card stays renderable.")
    @ApiResponse(responseCode = "200", description = "Composite overview.")
    @GetMapping("/executive-overview")
    @PreAuthorize("isAuthenticated()")
    public ExecutiveOverviewService.ExecutiveOverviewDto executiveOverview() {
        return overview.overview();
    }

    @Operation(summary = "Executive dashboard — printable PDF",
            description = "Single-page PDF combining the headline KPIs and 12-month trends " +
                    "(headcount, attrition, on-time/absenteeism, payroll cost) plus a current " +
                    "salary-distribution table. Same fan-out as /executive-overview; if a " +
                    "downstream is unreachable, that section renders empty rather than 502'ing.")
    @ApiResponse(responseCode = "200", description = "PDF bytes.")
    @GetMapping(value = "/executive-dashboard/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> executiveDashboardPdf() {
        byte[] pdf = dashboardPdf.render();
        String filename = "executive-dashboard-" + LocalDate.now() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}

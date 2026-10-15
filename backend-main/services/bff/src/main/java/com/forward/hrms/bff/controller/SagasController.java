package com.forward.hrms.bff.controller;

import com.forward.hrms.bff.client.SagaClient;
import com.forward.hrms.bff.client.dto.PageView;
import com.forward.hrms.bff.dto.response.SagasOverview;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Tag(name = "Sagas — operator console",
        description = "Cross-service saga rollup for the /admin/sagas page. "
                + "Requires `employee.read` (gateway-enforced); the per-service "
                + "reads pass through identity so a caller without payroll.read "
                + "sees the payroll slice degrade to empty rather than failing the whole call.")
@RestController
@RequestMapping("/bff/sagas")
@RequiredArgsConstructor
public class SagasController {

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 200;

    private final SagaClient sagas;

    @Operation(summary = "List sagas across employee + payroll",
            description = "Fans out to employee-service and payroll-service in parallel, merges " +
                    "rows, and sorts by updatedAt desc. Use `service=employee|payroll` to restrict " +
                    "to one side; default `all` queries both. Per-service slice degrades to empty " +
                    "if that service is down — degraded flags surface that in the response.")
    @ApiResponse(responseCode = "200", description = "Merged saga rows.")
    @GetMapping
    @RequirePermission("employee.read")
    public SagasOverview list(
            @Parameter(description = "Which service(s) to query.",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                            allowableValues = {"employee", "payroll", "all"}, defaultValue = "all"))
            @RequestParam(required = false, defaultValue = "all") String service,
            @Parameter(description = "Filter by status (RUNNING / COMPLETED / FAILED). Omit for all.")
            @RequestParam(required = false) String status,
            @Parameter(description = "Max rows fetched from each upstream slice (cap " + MAX_SIZE + ").")
            @RequestParam(required = false, defaultValue = "" + DEFAULT_SIZE) int size) {
        int capped = Math.max(1, Math.min(size, MAX_SIZE));

        boolean wantEmployee = "all".equalsIgnoreCase(service) || "employee".equalsIgnoreCase(service);
        boolean wantPayroll  = "all".equalsIgnoreCase(service) || "payroll".equalsIgnoreCase(service);

        PageView<SagaClient.SagaView> employeeRows = empty(capped);
        PageView<SagaClient.SagaView> payrollRows  = empty(capped);
        boolean employeeDegraded = false;
        boolean payrollDegraded  = false;

        if (wantEmployee) {
            try {
                employeeRows = sagas.employeeSagas(status, capped);
            } catch (RuntimeException ex) {
                log.warn("employee saga slice degraded: {}", ex.getMessage());
                employeeDegraded = true;
            }
        }
        if (wantPayroll) {
            try {
                payrollRows = sagas.payrollSagas(status, capped);
            } catch (RuntimeException ex) {
                log.warn("payroll saga slice degraded: {}", ex.getMessage());
                payrollDegraded = true;
            }
        }

        List<SagasOverview.Row> merged = new ArrayList<>(
                employeeRows.contentOrEmpty().size() + payrollRows.contentOrEmpty().size());
        for (SagaClient.SagaView v : employeeRows.contentOrEmpty()) merged.add(toRow("employee", v));
        for (SagaClient.SagaView v : payrollRows.contentOrEmpty())  merged.add(toRow("payroll",  v));

        merged.sort(Comparator.comparing(
                (SagasOverview.Row r) -> r.updatedAt() == null ? LocalDateTime.MIN : r.updatedAt())
                .reversed());

        if (merged.size() > capped) merged = merged.subList(0, capped);

        return new SagasOverview(
                merged,
                employeeRows.contentOrEmpty().size(),
                payrollRows.contentOrEmpty().size(),
                employeeDegraded,
                payrollDegraded);
    }

    private static PageView<SagaClient.SagaView> empty(int size) {
        return new PageView<>(List.of(), 0, size, 0L, 0, null);
    }

    private static SagasOverview.Row toRow(String service, SagaClient.SagaView v) {
        return new SagasOverview.Row(
                service, v.id(), v.sagaName(), v.correlationKey(), v.status(),
                v.currentStep(), v.stepIndex(), v.attemptCount(),
                v.nextAttemptAt(), v.lastError(),
                v.createdAt(), v.updatedAt(), v.createdBy());
    }
}

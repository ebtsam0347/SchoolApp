package com.forward.hrms.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ReportCatalogEntry",
        description = "One report the FE can offer. Combines the gateway path, "
                + "required permission, output format, sync/async mode and a "
                + "parameter manifest the FE renders into the request form. "
                + "/bff/reports/catalog returns the list pre-filtered by the "
                + "caller's permissions.")
public record ReportCatalogEntry(
        @Schema(description = "Stable id, '<service>.<slug>'. Use as React key + telemetry.",
                example = "payroll.pay-register")
        String id,

        @Schema(description = "Owning service.", example = "payroll",
                allowableValues = {"employee", "attendance", "leaves", "payroll"})
        String service,

        @Schema(description = "Human-readable title for the card.",
                example = "Pay register (per run)")
        String title,

        @Schema(description = "One-sentence summary shown under the title.",
                example = "Async — every payslip × every component, footed.")
        String description,

        @Schema(description = "Output file format.",
                allowableValues = {"csv", "pdf", "xlsx", "zip", "json"},
                example = "xlsx")
        String format,

        @Schema(description = "sync = browser receives the file inline; async = enqueue then poll /reports/jobs/{id}.",
                allowableValues = {"sync", "async"}, example = "async")
        String mode,

        @Schema(description = "HTTP method to invoke.",
                allowableValues = {"GET", "POST"}, example = "POST")
        String method,

        @Schema(description = "Gateway-relative path. Prepend the gateway base URL.",
                example = "/reports/payroll/pay-register/jobs")
        String path,

        @Schema(description = "Permission the caller must hold. Already enforced — this field "
                + "is informational so the FE can show why a row is hidden.",
                example = "payroll.reports.run")
        String permission,

        @Schema(description = "Statutory reports (EOBI / PESSI / tax) emit a user-visible audit "
                + "row so HR can demonstrate filings to auditors.")
        boolean userVisibleAudit,

        @Schema(description = "Parameter manifest. Render one form field per entry.")
        List<Parameter> parameters
) {

        @Schema(name = "ReportCatalogParameter",
                description = "A single parameter the FE needs to collect before submitting.")
        public record Parameter(
                @Schema(description = "Parameter name in the URL / body.", example = "from")
                String name,

                @Schema(description = "Where to put the value when calling.",
                        allowableValues = {"query", "path", "body"}, example = "query")
                String location,

                @Schema(description = "Value type.",
                        allowableValues = {"string", "int", "date", "enum"}, example = "date")
                String type,

                @Schema(description = "True when the gateway / service will 400 / 422 if missing.")
                boolean required,

                @Schema(description = "Allowed values when type=enum, else null.",
                        nullable = true)
                List<String> values,

                @Schema(description = "Inline help text the FE can show beside the field.",
                        nullable = true,
                        example = "Inclusive lower bound of the date range.")
                String help
        ) {}
}

package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "FactoryToursInbox",
        description = "Pending factory-tour approvals for the caller, across LOCAL and INTERNATIONAL " +
                "request types. Each row is enriched with the requester's employee summary.")
public record FactoryToursInbox(
        List<Row> factoryTours,
        Counts counts
) {
    @Schema(name = "FactoryToursInbox.Row")
    public record Row(FactoryTourDto request, @Schema(nullable = true) EmployeeSummary employee) {}

    @Schema(name = "FactoryToursInbox.Counts")
    public record Counts(int factoryTours, int total) {}
}

package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "AggregateDto",
        description = "Grouped headcount used by overview-page charts. " +
                "`buckets[]` is one row per group key; `total` is the sum across buckets.")
public record AggregateDto(
        @Schema(description = "What field the rows are grouped by.", example = "department")
        String groupBy,

        @Schema(description = "Sum of all bucket counts.", example = "287")
        long total,

        List<Bucket> buckets
) {
    @Schema(name = "AggregateBucket")
    public record Bucket(
            @Schema(description = "Group key as a string. For id-shaped groupings (department, " +
                    "section, grade, designation) this is the id; for enum-shaped groupings " +
                    "(status, employmentType, gender) this is the enum/value name.",
                    example = "3", nullable = true)
            String key,
            @Schema(description = "Optional human-readable label — populated for id-shaped " +
                    "groupings via the catalog row, null otherwise.", nullable = true)
            String label,
            long count) {}
}

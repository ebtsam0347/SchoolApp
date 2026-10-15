package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(name = "ReorderEntryRequest",
        description = "One entry of a bulk reorder payload — the new displayOrder for a catalog row.")
public record ReorderEntryRequest(
        @Schema(description = "Catalog row id.", example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long id,

        @Schema(description = "New display order. Lower sorts first.", example = "20",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @PositiveOrZero Integer displayOrder
) {}

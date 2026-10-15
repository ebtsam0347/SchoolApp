package com.forward.hrms.bff.service.search;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row of palette output. {@code id} is provider-prefixed
 * ({@code "employee:1234"}, {@code "lookup-department:42"}) so the FE can
 * key React lists without colliding across providers.
 */
@Schema(description = "One palette result. Order is by descending score within the response.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchHit(
        @Schema(description = "Stable, provider-prefixed id (e.g. \"employee:1234\").",
                example = "employee:1234", requiredMode = Schema.RequiredMode.REQUIRED)
        String id,

        @Schema(description = "Provider type. Matches the type filter in ?types=.",
                example = "employee", requiredMode = Schema.RequiredMode.REQUIRED)
        String type,

        @Schema(description = "Primary text shown by the palette row.",
                example = "Aisha Khan", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "Optional supporting text (designation, status, period).",
                example = "Software Engineer · Engineering")
        String subtitle,

        @Schema(description = "Where the FE should navigate on selection.",
                example = "/people/1234", requiredMode = Schema.RequiredMode.REQUIRED)
        String url,

        @Schema(description = "Per-provider relevance score in [0, 1]. Aggregator may apply a small " +
                "exact-prefix boost before global ranking.",
                example = "0.92", requiredMode = Schema.RequiredMode.REQUIRED)
        double score,

        @Schema(description = "Short label the FE renders next to the row.",
                example = "Person")
        String badge
) {}

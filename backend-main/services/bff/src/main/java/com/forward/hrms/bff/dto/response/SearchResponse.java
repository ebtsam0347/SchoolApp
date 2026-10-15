package com.forward.hrms.bff.dto.response;

import com.forward.hrms.bff.service.search.SearchHit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response for {@code GET /bff/search} — the global command-palette
 * aggregator. {@code results} is the merged, globally re-ranked list;
 * {@code providers} carries per-source diagnostics so the FE can show
 * "search degraded" affordances and we can spot slow providers in dev.
 */
@Schema(description = "Globally ranked palette results plus per-provider diagnostics.")
public record SearchResponse(
        @Schema(description = "Merged, re-ranked hits across all consulted providers. " +
                "Capped at the request limit (default 5 per provider, hard ceiling 10).",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<SearchHit> results,

        @Schema(description = "True when at least one provider timed out or errored. " +
                "The FE shows what came back; degraded providers surface in `providers`.",
                example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean partial,

        @Schema(description = "One row per provider the aggregator consulted (after permission filtering). " +
                "Useful for tuning provider timeouts and zero-result coverage.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProviderDiagnostic> providers
) {

    @Schema(description = "Provider-level outcome.")
    public record ProviderDiagnostic(
            @Schema(description = "Provider type id.", example = "employee",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String type,

            @Schema(description = "Number of hits returned (post per-provider cap).",
                    example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
            int hits,

            @Schema(description = "Wall time in milliseconds until the provider settled.",
                    example = "18", requiredMode = Schema.RequiredMode.REQUIRED)
            long ms,

            @Schema(description = "ok, timeout, or error.", example = "ok",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String status
    ) {}
}

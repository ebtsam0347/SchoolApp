package com.forward.hrms.audit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Distinct values observed in the audit log, with frequency counts.
 * Lets the explorer's filter dropdowns stay honest as new services
 * emit events — the FE no longer hardcodes the set.
 */
@Schema(description = "Observed facet values across audit_events, ordered by frequency.")
public record FacetsResponse(
        List<Facet> entityTypes,
        List<Facet> actions,
        List<Facet> sourceServices
) {

    @Schema(description = "One distinct value with its row count.")
    public record Facet(
            @Schema(example = "employee") String value,
            @Schema(example = "1248") long count
    ) {}
}

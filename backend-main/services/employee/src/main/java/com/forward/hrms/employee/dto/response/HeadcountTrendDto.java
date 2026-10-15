package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "HeadcountTrendDto",
        description = "Joiners / leavers / end-of-month headcount over a trailing window. " +
                "Computed once on the server so the HR dashboard chart can render with one " +
                "round-trip rather than reducing 12+ queries client-side.")
public record HeadcountTrendDto(int months, List<Month> series) {

    @Schema(name = "HeadcountTrendDto.Month")
    public record Month(
            @Schema(example = "2026-04") String month,
            long joiners,
            long leavers,
            long endHeadcount
    ) {}
}

package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.AccountsReportParam;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AccountsReportParam", description = "A filter the report accepts.")
public record AccountsReportParamDto(
        @Schema(description = "Filter.") AccountsReportParam param,
        @Schema(description = "Legacy F-number; null for filters the legacy system didn't have.",
                example = "F15", nullable = true) String legacyCode,
        @Schema(description = "True when the run is refused without it.") boolean required
) {

    public static AccountsReportParamDto of(AccountsReportParam p, boolean required) {
        return new AccountsReportParamDto(p, p.legacyCode(), required);
    }
}

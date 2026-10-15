package com.forward.hrms.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PersonaHints",
        description = "Pre-computed flags the FE persona resolver needs but cannot derive from "
                + "/auth/me alone. Today this is just the manager signal — does the caller have "
                + "any direct reports? Permission-based scopes (HR / payroll / admin) stay on "
                + "the FE since they are trivially derivable from /auth/me.permissions.")
public record PersonaHints(
        @Schema(description = "True when the caller has at least one direct report — drives the "
                + "Manager dashboard option in the persona switcher.")
        boolean hasDirectReports,

        @Schema(description = "Number of direct reports. 0 when the caller is not a manager or has "
                + "no ESS-linked employee record.", example = "12")
        int directReportsCount
) {
    public static PersonaHints none() {
        return new PersonaHints(false, 0);
    }
}

package com.forward.hrms.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserStatsResponse",
        description = "Aggregate counts over the users table — drives the /admin overview tile "
                + "without paginating four times to read totalElements.")
public record UserStatsResponse(
        @Schema(description = "Total user accounts.", example = "1248") long total,
        @Schema(description = "Status = ACTIVE.", example = "1180") long active,
        @Schema(description = "Status = LOCKED (admin lock; failed-login lock surfaces via lockedUntil instead).",
                example = "4") long locked,
        @Schema(description = "Status = DISABLED.", example = "64") long disabled,
        @Schema(description = "Users that must change their password at next login.", example = "12") long withMustChangePw,
        @Schema(description = "Users linked to an employee record (ESS access).", example = "1230") long employeeLinked,
        @Schema(description = "Users not linked to any employee (system / admin / consultant).",
                example = "18") long standalone
) {}

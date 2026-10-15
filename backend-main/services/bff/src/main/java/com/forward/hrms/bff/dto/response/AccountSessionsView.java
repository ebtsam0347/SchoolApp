package com.forward.hrms.bff.dto.response;

import com.forward.hrms.auth.dto.response.SessionDto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "AccountSessionsView",
        description = "Caller's active sessions, partitioned for the user-stripe and security panel.")
public record AccountSessionsView(
        @Schema(description = "Total active sessions for the caller.", example = "3") int active,
        @Schema(description = "The session backing the current request, if it could be identified.")
        SessionDto current,
        @Schema(description = "Other active sessions (excludes `current`).") List<SessionDto> others
) {}

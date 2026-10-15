package com.forward.hrms.bff.dto.response;

import com.forward.hrms.auth.dto.response.MeResponse;
import com.forward.hrms.audit.dto.response.TimelineEntry;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "AccountSummary",
        description = "Composite payload for the /account layout: identity, sessions, recent timeline. "
                + "Replaces the three queries the user-stripe and overview tab fan out today.")
public record AccountSummary(
        @Schema(description = "Caller identity, roles, and effective permissions (from /auth/me).")
        MeResponse identity,

        @Schema(description = "Active sessions for the caller (from /auth/sessions).")
        AccountSessionsView sessions,

        @Schema(description = "Top user-visible events for the caller (from /timeline/user/{id}).")
        List<TimelineEntry> recentEvents
) {}

package com.forward.hrms.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Wrapper for the /bff/users/by-ids resolver. Order matches input order, deduped.")
public record UsersByIdsResponse(List<UserIdentitySummary> users) {}

package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "DelegationCandidatesResponse",
        description = "List wrapper for the delegation picker. Wrapped (not bare) so future fields " +
                "(scope, hint counts) can be added without a breaking change.")
public record DelegationCandidatesResponse(List<DelegationCandidate> users) {}

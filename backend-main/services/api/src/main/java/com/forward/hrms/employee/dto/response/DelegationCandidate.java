package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DelegationCandidate",
        description = "A user the caller is allowed to delegate approval authority to. Scoped " +
                "server-side — the caller cannot delegate to themselves, and only employees with " +
                "active ESS access (a linked auth user) appear. `role` is left null for v1; the " +
                "FE can fall back to displaying the employee code.")
public record DelegationCandidate(
        long userId,
        String fullName,
        String employeeCode,
        @Schema(nullable = true, description = "Reserved for v2 — auth role of the candidate.")
        String role
) {}

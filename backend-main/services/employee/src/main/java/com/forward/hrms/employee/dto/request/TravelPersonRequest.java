package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(name = "TravelPersonRequest", description = "A co-traveller accompanying the requester.")
public record TravelPersonRequest(
        @Schema(description = "Employee card number of the co-traveller.") @NotBlank String cardNo,
        @Schema(nullable = true) String name
) {}

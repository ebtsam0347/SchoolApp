package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TravelPersonDto")
public record TravelPersonDto(String cardNo, @Schema(nullable = true) String name) {}

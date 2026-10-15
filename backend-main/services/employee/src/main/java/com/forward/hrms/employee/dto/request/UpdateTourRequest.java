package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.TourType;
import com.forward.hrms.employee.enums.VehicleSource;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "UpdateTourRequest",
        description = "All fields optional — only non-null values are applied. Tour must be in DRAFT status.")
public record UpdateTourRequest(
        @Schema(nullable = true) TourType travelType,
        @Schema(nullable = true) LocalDateTime tourStart,
        @Schema(nullable = true) LocalDateTime tourEnd,
        @Schema(nullable = true) String origin,
        @Schema(nullable = true) String destination,
        @Schema(nullable = true) String city,
        @Schema(nullable = true) String country,
        @Schema(nullable = true) String currency,
        @Schema(nullable = true) VehicleSource vehicleSource,
        @Schema(nullable = true) String transportationType,
        @Schema(nullable = true) String vehicleNo,
        @Schema(nullable = true) String purpose,
        @Schema(nullable = true) BigDecimal totalDistanceKm,
        @Schema(nullable = true) List<TravelPersonRequest> additionalTravelPersons
) {}

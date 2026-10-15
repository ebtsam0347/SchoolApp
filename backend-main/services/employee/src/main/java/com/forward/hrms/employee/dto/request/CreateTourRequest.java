package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.TourType;
import com.forward.hrms.employee.enums.VehicleSource;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "CreateTourRequest", description = "Request to raise a factory-tour travel request. " +
        "Requester identity (cardNo/name/department/section/designation/grade) is resolved server-side.")
public record CreateTourRequest(
        @Schema(description = "LOCAL or INTERNATIONAL") @NotNull TourType travelType,
        @Schema(description = "Tour start, ISO datetime.") @NotNull LocalDateTime tourStart,
        @Schema(description = "Tour end, ISO datetime. Must be after tourStart.") @NotNull LocalDateTime tourEnd,
        @NotBlank String origin,
        @NotBlank String destination,
        @Schema(description = "Required when travelType = LOCAL.", nullable = true) String city,
        @Schema(description = "Required when travelType = INTERNATIONAL.", nullable = true) String country,
        @Schema(description = "Required when travelType = INTERNATIONAL.", nullable = true) String currency,
        @Schema(description = "Required when travelType = LOCAL.", nullable = true) VehicleSource vehicleSource,
        @Schema(nullable = true) String transportationType,
        @Schema(nullable = true) String vehicleNo,
        @Schema(nullable = true) String purpose,
        @Schema(description = "LOCAL only.", nullable = true) BigDecimal totalDistanceKm,
        @Schema(nullable = true) List<TravelPersonRequest> additionalTravelPersons
) {
    @AssertTrue(message = "tourEnd must be after tourStart")
    @Schema(hidden = true)
    public boolean isTourEndAfterTourStart() {
        return tourStart == null || tourEnd == null || tourEnd.isAfter(tourStart);
    }

    @AssertTrue(message = "city and vehicleSource are required when travelType is LOCAL")
    @Schema(hidden = true)
    public boolean isLocalFieldsPresent() {
        if (travelType != TourType.LOCAL) return true;
        return city != null && !city.isBlank() && vehicleSource != null;
    }

    @AssertTrue(message = "country and currency are required when travelType is INTERNATIONAL")
    @Schema(hidden = true)
    public boolean isInternationalFieldsPresent() {
        if (travelType != TourType.INTERNATIONAL) return true;
        return country != null && !country.isBlank() && currency != null && !currency.isBlank();
    }
}

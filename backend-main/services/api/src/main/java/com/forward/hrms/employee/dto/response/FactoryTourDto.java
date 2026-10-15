package com.forward.hrms.employee.dto.response;

import com.forward.hrms.attendance.dto.response.ApprovalStepDto;
import com.forward.hrms.attendance.dto.response.CurrentStageDto;
import com.forward.hrms.employee.enums.TourStatus;
import com.forward.hrms.employee.enums.TourType;
import com.forward.hrms.employee.enums.VehicleSource;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "FactoryTourDto")
public record FactoryTourDto(
        long id,
        String requestId,
        @Schema(description = "Always \"Factory Tour\".") String requestType,
        LocalDate requestDate,
        TourStatus status,
        String cardNo,
        String name,
        Long departmentId,
        Long sectionId,
        Long designationId,
        Long gradeId,
        TourType travelType,
        LocalDateTime tourStart,
        LocalDateTime tourEnd,
        String origin,
        String destination,
        @Schema(nullable = true) String city,
        @Schema(nullable = true) String country,
        @Schema(nullable = true) String currency,
        @Schema(nullable = true) VehicleSource vehicleSource,
        @Schema(nullable = true) String transportationType,
        @Schema(nullable = true) String vehicleNo,
        @Schema(nullable = true) String purpose,
        @Schema(nullable = true) BigDecimal totalDistanceKm,
        List<TravelPersonDto> additionalTravelPersons,
        Long approvedByUserId,
        @Schema(nullable = true) LocalDateTime approvedAt,
        @Schema(nullable = true) String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String createdBy,
        @Schema(nullable = true) CurrentStageDto currentStage,
        List<ApprovalStepDto> steps,
        @Schema(description = "Actions the calling user can take right now.") List<String> actions
) {}

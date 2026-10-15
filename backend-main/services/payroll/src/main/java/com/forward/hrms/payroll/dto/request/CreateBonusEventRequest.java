package com.forward.hrms.payroll.dto.request;

import com.forward.hrms.payroll.enums.BonusEventType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "CreateBonusEventRequest")
public record CreateBonusEventRequest(
        @NotBlank @Size(max = 128) String name,
        @NotNull BonusEventType eventType,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String fiscalYearCode,
        @NotNull LocalDate referenceDate,
        @Positive Integer fullServiceDaysThreshold,
        String notes
) {}

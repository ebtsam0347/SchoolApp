package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@Schema(name = "EmployeeRequisitionRequestNewHiring")
public record EmployeeRequisitionRequestNewHiring(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        @NotNull
        Long departmentId,

        @Schema(description = "Minimum education requirement.", example = "Bachelor's Degree")
        @NotBlank
        String minimumEducation,

        @Schema(description = "Minimum years of experience required.", example = "1")
        @NotNull
        @Min(0)
        Long minimumExperience,

        @Schema(description = "Minimum skill required.", example = "Java")
        @NotBlank
        String minimumSkill,

        @Schema(description = "Job description for the open position.", example = "Responsible for developing and" +
                " maintaining Java applications.")
        @NotBlank
        String jobDescription,

        @Schema(description = "Business justification for the hire.", example = "increasing capacity")
        @NotBlank
        String requestReason,

        @Schema(description = "Notes from the Head of Department.", example = "Need to fill the position urgently")
        String hodNotes,

        List<EmployeeRequisitionNewHiringRequestDetail> details

) {}
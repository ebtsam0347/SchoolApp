package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@Schema(name = "EmployeeRequisitionRequestReplacement", description = "Request body for creating a REPLACEMENT requisition. " +
        "Requires `replacingEmployeeId` to be set in each detail row.")
public record EmployeeRequisitionRequestReplacement(

        @Schema(description = "Minimum education requirement.", example = "Bachelor's Degree")
        @NotBlank
        String minimumEducation,

        @Schema(description = "Minimum years of experience required.", example = "1")
        @NotNull
        Long minimumExperience,

        @Schema(description = "Minimum skill required.", example = "Java")
        @NotBlank
        String minimumSkill,

        @Schema(description = "Job description for the open position.")
        @NotBlank
        String jobDescription,

        @Schema(description = "Business justification for the replacement.", example = "increasing capacity")
        @NotBlank
        String requestReason,

        @Schema(description = "Notes from the Head of Department.", example = "Need to fill the position urgently")
        String hodNotes,

        List<EmployeeRequisitionReplacementRequestDetail> details

) {
}

package com.forward.hrms.commons.approval.dto.request;

import com.forward.hrms.commons.approval.enums.ResolverKind;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "SetWorkflowChainRequest",
        description = "Replaces the entire approval chain for a request type with this ordered list. " +
                "stage_no is assigned by position (1-based), so HR never manages numbers. Editing a chain " +
                "never affects in-flight requests — they run on the chain snapshotted when they were submitted.")
public record SetWorkflowChainRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Ordered stages, first to last. Must be non-empty.")
        @NotEmpty @Valid List<Stage> stages
) {
    @Schema(name = "WorkflowStageInput", description = "One stage in the chain.")
    public record Stage(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "MANAGER snapshots the submitter's manager (delegatable); PERMISSION is a " +
                            "role-only pool gated by `approvePermission`; DEPARTMENT_HEAD snapshots a fixed " +
                            "target department's head (`resolverTarget` = department code); GRADE is a " +
                            "role-only pool gated by employee grade (`resolverTarget` = grade code).")
            @NotNull ResolverKind resolverKind,

            @Schema(nullable = true, example = "attendance.hr.approve",
                    description = "Required for PERMISSION stages — the permission a user must hold to act. " +
                            "Ignored otherwise.")
            @Size(max = 64) String approvePermission,

            @Schema(nullable = true, example = "ME",
                    description = "Required for DEPARTMENT_HEAD/GRADE stages — the target department/grade " +
                            "code. Ignored otherwise.")
            @Size(max = 64) String resolverTarget,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "HR",
                    description = "Display label shown on the chain and carried onto every stage event.")
            @NotBlank @Size(max = 48) String label
    ) {}
}

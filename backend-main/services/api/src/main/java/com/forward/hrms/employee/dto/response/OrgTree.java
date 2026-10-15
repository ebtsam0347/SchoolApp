package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "OrgTree",
        description = "Recursive manager → reports tree, depth-bounded to keep responses sane.")
public record OrgTree(
        Node root,
        int depth
) {

    @Schema(name = "OrgTree.Node")
    public record Node(
            long id,
            String fullName,
            String employeeCode,
            @Schema(nullable = true) String designationTitle,
            @Schema(nullable = true) String profilePictureUrl,
            int directReportsCount,
            List<Node> children
    ) {}
}

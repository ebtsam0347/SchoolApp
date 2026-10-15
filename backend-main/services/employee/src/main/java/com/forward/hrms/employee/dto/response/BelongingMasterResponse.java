package com.forward.hrms.employee.dto.response;

public record BelongingMasterResponse(
        Long id,

        String belongingName,

        Long authorizedEmployeeId,

        Boolean active
) {
}

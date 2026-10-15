package com.forward.hrms.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(name = "UpdatePreferencesRequest",
        description = "Sparse map of notification type → enabled flag. Only the keys present are touched.")
public record UpdatePreferencesRequest(
        @Schema(description = "Type → enabled toggle. Pass `false` to opt out of a type.",
                example = "{\"LEAVE_APPROVED\": false, \"PAYSLIP_READY\": true}",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Map<String, Boolean> preferences
) {}

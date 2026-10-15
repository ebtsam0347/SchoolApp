package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ReconciliationExportDto",
        description = "A generated Excel reconciliation report uploaded to object storage. The " +
                "downloadUrl is a short-lived signed link the FE opens directly.")
public record ReconciliationExportDto(
        @Schema(description = "Short-lived signed GET URL for the .xlsx.")
        String downloadUrl,
        @Schema(description = "Object key in the reports bucket.")
        String objectKey
) {}

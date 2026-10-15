package com.forward.hrms.payroll.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PdfUrlResponse")
public record PdfUrlResponse(
        @Schema(description = "Short-lived signed MinIO URL.") String url
) {}

package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.EmployeeDocument;
import com.forward.hrms.employee.enums.DocumentType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmployeeDocumentDto", description = "Stored document metadata + a short-lived signed download URL.")
public record EmployeeDocumentDto(
        long id,
        long employeeId,
        DocumentType docType,
        String title,
        String objectKey,
        @Schema(nullable = true) String contentType,
        @Schema(nullable = true) Long sizeBytes,
        @Schema(description = "Short-lived signed download URL.", nullable = true) String downloadUrl
) {
    public static EmployeeDocumentDto of(EmployeeDocument d, String downloadUrl) {
        return new EmployeeDocumentDto(
                d.getId(), d.getEmployeeId(),
                d.getDocType(), d.getTitle(),
                d.getObjectKey(), d.getContentType(), d.getSizeBytes(),
                downloadUrl);
    }
}

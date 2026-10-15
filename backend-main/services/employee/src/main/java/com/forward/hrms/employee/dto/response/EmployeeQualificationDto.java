package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.EmployeeQualification;
import com.forward.hrms.employee.entity.Qualification;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmployeeQualificationDto",
        description = "An employee's instance of a catalog qualification (degree).")
public record EmployeeQualificationDto(
        long id,
        long employeeId,
        long qualificationId,
        @Schema(description = "Catalog code, denormalized.", example = "BSC_CS") String qualificationCode,
        @Schema(description = "Catalog name, denormalized.", example = "BSc Computer Science") String qualificationName,
        String institution,
        @Schema(nullable = true) String boardOrUniversity,
        @Schema(nullable = true) Integer yearOfCompletion,
        @Schema(nullable = true) String grade,
        @Schema(nullable = true, description = "MinIO key for an uploaded transcript / degree.")
        String documentKey,
        @Schema(description = "Short-lived signed GET URL for the transcript. " +
                "Null when no document is attached or the storage call failed.", nullable = true)
        String documentUrl
) {
    public static EmployeeQualificationDto of(EmployeeQualification eq, Qualification q) {
        return of(eq, q, null);
    }

    public static EmployeeQualificationDto of(EmployeeQualification eq, Qualification q, String documentUrl) {
        return new EmployeeQualificationDto(
                eq.getId(), eq.getEmployeeId(), eq.getQualificationId(),
                q == null ? null : q.getCode(),
                q == null ? null : q.getName(),
                eq.getInstitution(), eq.getBoardOrUniversity(),
                eq.getYearOfCompletion(), eq.getGrade(),
                eq.getDocumentKey(),
                documentUrl);
    }
}

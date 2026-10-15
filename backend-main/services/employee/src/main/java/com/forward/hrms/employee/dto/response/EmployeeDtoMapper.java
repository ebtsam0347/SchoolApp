package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.enums.EssProvisioningStatus;

/**
 * JPA-entity → {@link EmployeeDto} mapping. Lives here (in employee)
 * rather than as a static method on the record itself because
 * {@code EmployeeDto} is now published from {@code employee-api}
 * (no Spring / no JPA), and importing {@link Employee} into that
 * module would pull JPA into the BFF classpath. Same convention as
 * the other employee DTO mappers in this package.
 */
public final class EmployeeDtoMapper {

    private EmployeeDtoMapper() {}

    /**
     * Default factory — used by mutation responses. No signed URL, no
     * count, no probation; ESS status defaults to {@code COMPLETED}
     * when a user is linked, {@code NOT_REQUESTED} otherwise. Read
     * paths should go through {@code EmployeeDtoAssembler} to also
     * pick up RUNNING / FAILED from the saga row.
     */
    public static EmployeeDto of(Employee e) {
        return of(e, null, defaultEssStatus(e), null, null, null);
    }

    /** Read-side factory — pass null for any slice the caller doesn't need. */
    public static EmployeeDto of(Employee e,
                                 String profilePictureUrl,
                                 EssProvisioningStatus essProvisioningStatus,
                                 Integer directReportsCount,
                                 ActiveProbationDto activeProbation,
                                 Long managerUserId) {
        return new EmployeeDto(
                e.getId(), e.getUserId(), e.getEmployeeCode(), e.getFullName(),
                e.getCnic(), e.getCnicIssueDate(), e.getCnicExpiryDate(),
                e.getNtn(), e.getInsuranceNumber(), e.getSscNumber(), e.getEobiNumber(),
                e.getDateOfBirth(), e.getGender(), e.getMaritalStatus(),
                e.getFatherName(), e.getMotherName(), e.getSpouseName(),
                e.getReligion(), e.getBloodGroup(),
                e.getPersonalEmail(), e.getWorkEmail(), e.getPhone(),
                e.getPermanentAddress(), e.getCurrentAddress(), e.getCityId(),
                e.getCountry(),
                e.getJoiningDate(), e.getConfirmationDate(),
                e.getEmploymentStatus(), e.getEmploymentType(),
                e.getDepartmentId(), e.getSectionId(), e.getDesignationId(),
                e.getGradeId(), e.getManagerId(), managerUserId,
                e.getProfilePictureKey(),
                profilePictureUrl,
                e.getUserId() != null,
                essProvisioningStatus != null ? essProvisioningStatus : defaultEssStatus(e),
                directReportsCount,
                activeProbation,
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private static EssProvisioningStatus defaultEssStatus(Employee e) {
        return e.getUserId() != null ? EssProvisioningStatus.COMPLETED : EssProvisioningStatus.NOT_REQUESTED;
    }
}

package com.forward.hrms.employee.entity;

import java.time.LocalDate;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.EmploymentType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "employees")
@Getter
@Setter
public class Employee extends AuditedEntity {

    @Column(name = "user_id", unique = true)
    private Long userId;

    @Column(name = "employee_code", nullable = false, length = 32, unique = true)
    private String employeeCode;

    @Column(name = "full_name", nullable = false, length = 128)
    private String fullName;

    @Column(name = "cnic", length = 15)
    private String cnic;

    @Column(name = "cnic_issue_date")
    private LocalDate cnicIssueDate;

    @Column(name = "cnic_expiry_date")
    private LocalDate cnicExpiryDate;

    @Column(name = "ntn", length = 32)
    private String ntn;

    @Column(name = "insurance_number", length = 64)
    private String insuranceNumber;

    @Column(name = "ssc_number", length = 64)
    private String sscNumber;

    @Column(name = "eobi_number", length = 32)
    private String eobiNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 16)
    private String gender;

    @Column(name = "marital_status", length = 16)
    private String maritalStatus;

    @Column(name = "father_name", length = 128)
    private String fatherName;

    @Column(name = "mother_name", length = 128)
    private String motherName;

    @Column(name = "spouse_name", length = 128)
    private String spouseName;

    @Column(name = "religion", length = 32)
    private String religion;

    @Column(name = "blood_group", length = 8)
    private String bloodGroup;

    @Column(name = "personal_email", length = 255)
    private String personalEmail;

    @Column(name = "work_email", length = 255)
    private String workEmail;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "permanent_address")
    private String permanentAddress;

    @Column(name = "current_address")
    private String currentAddress;

    @Column(name = "city_id")
    private Long cityId;

    @Column(name = "country", length = 64)
    private String country = "PK";

    @Column(name = "joining_date", nullable = false)
    private LocalDate joiningDate;

    @Column(name = "confirmation_date")
    private LocalDate confirmationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false, length = 16)
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 16)
    private EmploymentType employmentType;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "section_id")
    private Long sectionId;

    @Column(name = "designation_id")
    private Long designationId;

    @Column(name = "grade_id")
    private Long gradeId;

    @Column(name = "manager_id")
    private Long managerId;

    @Column(name = "profile_picture_key", length = 512)
    private String profilePictureKey;

    @Column(name = "reference_by", length = 100)
    private String referenceBy;
}

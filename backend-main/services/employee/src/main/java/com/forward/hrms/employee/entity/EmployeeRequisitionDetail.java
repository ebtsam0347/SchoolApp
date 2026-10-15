package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.EmployeeCategory;
import com.forward.hrms.employee.enums.EmployeeGender;
import com.forward.hrms.employee.enums.EmploymentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "employee_requisition_detail")
@Getter
@Setter
public class EmployeeRequisitionDetail extends AuditedEntity {

    @Column(name = "requisition_head_id")
    private Long requisitionHeadId;

    @Column(name = "replacing_employee_id")
    private Long replacingEmployeeId;

    @Column(name = "new_employee_code")
    private String newEmployeeCode;

    @Column(name = "temporary_employee_reference")
    private String temporaryEmployeeReference;

    @Column(name = "section_id")
    private Long sectionId;

    @Column(name = "designation_id", nullable = false)
    private Long designationId;

    @Column(name = "grade_id")
    private Long gradeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type")
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "employee_category")
    private EmployeeCategory employeeCategory;

    @Column(name = "required_until")
    private LocalDate requiredUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private EmployeeGender employeeGender;

    @Column(name = "shift_id")
    private Long shiftId;

    @Column(name = "is_lean_removed")
    private boolean isLeanRemoved;

    @Column(name = "lean_removed_remarks")
    private String leanRemovedRemarks;

    @Column(name = "lean_removed_by")
    private Long leanRemovedBy;
}

package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.payroll.enums.AllowanceRunStatus;
import com.forward.hrms.payroll.enums.AllowanceTransactionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;


@Entity
@Table(name = "allowance_transaction")
@Getter
@Setter
public class AllowanceTransaction extends AuditedEntity {

    @Column(name = "allowance_run_id", nullable = false)
    private Long allowanceRunId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_code", nullable = false, length = 32)
    private String employeeCode;

    @Column(name = "allowance_year", nullable = false)
    private int allowanceYear;

    @Column(name = "allowance_month", nullable = false)
    private int allowanceMonth;

    @Column(name = "fiscal_year_code", nullable = false, length = 7)
    private String fiscalYearCode;

    @Column(name = "grade", nullable = false)
    private String grade;

    @Column(name = "employee_salary_component_id", nullable = false)
    private Long employeeSalaryComponentId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "pdf_object_key", length = 512)
    private String pdfObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AllowanceTransactionStatus status = AllowanceTransactionStatus.DRAFT;

}
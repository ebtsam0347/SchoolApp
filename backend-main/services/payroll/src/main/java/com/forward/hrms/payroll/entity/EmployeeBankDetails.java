package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "employee_bank_details")
@Getter
@Setter
public class EmployeeBankDetails extends AuditedEntity {

    @Column(name = "employee_id", nullable = false, unique = true)
    private Long employeeId;

    @Column(name = "bank_id", nullable = false)
    private Long bankId;

    @Column(name = "branch", length = 128)
    private String branch;

    @Column(name = "account_title", nullable = false, length = 128)
    private String accountTitle;

    @Column(name = "iban", nullable = false, length = 34)
    private String iban;

    @Column(name = "account_number", length = 64)
    private String accountNumber;
}

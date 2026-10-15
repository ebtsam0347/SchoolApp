package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Bank catalog. The same row identifies a bank for two purposes:
 * (1) it carries the company's own account at that bank (account_title /
 * iban / account_number) — payroll runs use this as the disbursement
 * source; (2) it acts as a controlled list of banks that employees can
 * pick from on {@code employee_bank_details}, where the employee's own
 * account number lives.
 */
@Entity
@Table(name = "banks")
@Getter
@Setter
public class Bank extends AuditedEntity {

    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "branch", length = 128)
    private String branch;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "account_title", length = 128)
    private String accountTitle;

    @Column(name = "iban", length = 34)
    private String iban;

    @Column(name = "account_number", length = 64)
    private String accountNumber;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}

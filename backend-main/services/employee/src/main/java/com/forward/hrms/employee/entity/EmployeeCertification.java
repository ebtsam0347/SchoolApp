package com.forward.hrms.employee.entity;

import java.time.LocalDate;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "employee_certifications")
@Getter
@Setter
public class EmployeeCertification extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "issuing_body", length = 255)
    private String issuingBody;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    /** {@code null} = no expiry. */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "credential_id", length = 128)
    private String credentialId;

    @Column(name = "document_key", length = 512)
    private String documentKey;
}

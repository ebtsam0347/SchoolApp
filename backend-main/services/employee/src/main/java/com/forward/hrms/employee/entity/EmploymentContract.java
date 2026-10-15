package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.ContractStatus;
import com.forward.hrms.employee.enums.ContractType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "employment_contracts")
@Getter
@Setter
public class EmploymentContract extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 32)
    private ContractType contractType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ContractStatus status = ContractStatus.ACTIVE;

    /** One of 30, 60, 90, 120, 180. Only set when {@code contractType=PROBATION}. */
    @Column(name = "probation_days")
    private Integer probationDays;

    @Column(name = "is_extended", nullable = false)
    private boolean extended;

    @Column(name = "document_key", length = 512)
    private String documentKey;
}

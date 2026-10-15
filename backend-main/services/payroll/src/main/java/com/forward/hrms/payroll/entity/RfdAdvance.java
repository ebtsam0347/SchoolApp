package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cash handed to a cash-paid employee beyond what they were owed, because a
 * net was rounded up to the next 500-rupee note. Everything keys off
 * {@code transactionDate}: the payroll run for month M deducts the rows dated
 * in M, final settlement deducts the rows dated in the last-working-day
 * month, and a run reset deletes the "From Salary" rows dated each payslip's
 * created date.
 */
@Entity
@Table(name = "rfd_advance")
@Getter
@Setter
public class RfdAdvance extends AuditedEntity {

    /** Prefix of {@link #transactionState} on rows a payroll run creates. */
    public static final String FROM_SALARY = "From Salary ";
    public static final String FROM_BONUS = "From Bonus ";

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "transaction_state", nullable = false, length = 32)
    private String transactionState;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;
}

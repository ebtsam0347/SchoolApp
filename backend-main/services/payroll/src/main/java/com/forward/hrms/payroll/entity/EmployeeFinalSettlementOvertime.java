package com.forward.hrms.payroll.entity;

import com.forward.hrms.payroll.enums.SettlementOvertimeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Historical Normal-Overtime snapshot for one Employee Final Settlement.
 * Persisted only when the settlement is actually created — never for a
 * preview call — from the exact minutes/amounts the preview's
 * {@code normalOvertime} already returned (same
 * {@code SettlementCalculationResult}-derived values, not recalculated
 * here). {@code overtimeType} is always {@link SettlementOvertimeType#NORMAL}
 * — the settlement never represents Extra/Special overtime.
 *
 * <p>{@code monthlyBasicSalary} / {@code perDaySalary} / {@code perHourSalary}
 * / {@code overtimeMultiplier} are stored purely for audit trail — they
 * document the inputs behind the already-computed amounts, they do not
 * feed a second calculation.
 */
@Entity
@Table(name = "employee_final_settlement_overtime")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeFinalSettlementOvertime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private EmployeeFinalSettlement settlement;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "overtime_type", nullable = false, length = 30)
    @Builder.Default
    private SettlementOvertimeType overtimeType = SettlementOvertimeType.NORMAL;

    /*
     * --------------------------------------------------------
     * Normal OT — Approved
     * --------------------------------------------------------
     */

    @Column(name = "approved_minutes", nullable = false)
    @Builder.Default
    private Integer approvedMinutes = 0;

    @Column(name = "approved_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal approvedAmount = BigDecimal.ZERO;

    /*
     * --------------------------------------------------------
     * Normal OT — Pending
     * --------------------------------------------------------
     */

    @Column(name = "pending_minutes", nullable = false)
    @Builder.Default
    private Integer pendingMinutes = 0;

    @Column(name = "pending_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal pendingAmount = BigDecimal.ZERO;

    /*
     * --------------------------------------------------------
     * Normal OT — Unapplied
     * --------------------------------------------------------
     */

    @Column(name = "unapplied_minutes", nullable = false)
    @Builder.Default
    private Integer unappliedMinutes = 0;

    @Column(name = "unapplied_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal unappliedAmount = BigDecimal.ZERO;

    /*
     * --------------------------------------------------------
     * Normal OT — Total
     * --------------------------------------------------------
     */

    @Column(name = "total_minutes", nullable = false)
    @Builder.Default
    private Integer totalMinutes = 0;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /*
     * --------------------------------------------------------
     * Calculation Input Snapshot (auditability only)
     * --------------------------------------------------------
     */

    @Column(name = "monthly_basic_salary", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal monthlyBasicSalary = BigDecimal.ZERO;

    @Column(name = "per_day_salary", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal perDaySalary = BigDecimal.ZERO;

    @Column(name = "per_hour_salary", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal perHourSalary = BigDecimal.ZERO;

    @Column(name = "overtime_multiplier", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal overtimeMultiplier = BigDecimal.valueOf(2);

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

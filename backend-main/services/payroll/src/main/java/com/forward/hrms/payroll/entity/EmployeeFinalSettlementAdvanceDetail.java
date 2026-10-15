package com.forward.hrms.payroll.entity;

import com.forward.hrms.payroll.enums.AdvanceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_final_settlement_advance_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeFinalSettlementAdvanceDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * --------------------------------------------------------
     * Parent Settlement
     * --------------------------------------------------------
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private EmployeeFinalSettlement settlement;

    /*
     * --------------------------------------------------------
     * Permanent Advance Snapshot
     * --------------------------------------------------------
     */

    @Column(name = "advance_id", nullable = false)
    private Long advanceId;

    @Column(name = "request_code", nullable = false, length = 30)
    private String requestCode;

    @Column(name = "receiving_date")
    private LocalDate receivingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "advance_status", nullable = false, length = 30)
    private AdvanceStatus advanceStatus;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /*
     * --------------------------------------------------------
     * Financial Snapshot
     * --------------------------------------------------------
     */

    @Column(name = "original_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal originalAmount = BigDecimal.ZERO;

    @Column(name = "recovered_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal recoveredAmount = BigDecimal.ZERO;

    @Column(name = "outstanding_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal outstandingAmount = BigDecimal.ZERO;

    @Column(name = "monthly_deduction", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal monthlyDeduction = BigDecimal.ZERO;

    @Column(name = "bonus_offset", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal bonusOffset = BigDecimal.ZERO;

    @Column(name = "annual_leave_offset", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal annualLeaveOffset = BigDecimal.ZERO;

    /*
     * --------------------------------------------------------
     * Final Settlement Recovery
     * --------------------------------------------------------
     */

    @Column(name = "bonus_recovered", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal bonusRecovered = BigDecimal.ZERO;

    @Column(name = "leave_recovered", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal leaveRecovered = BigDecimal.ZERO;

    @Column(name = "settlement_recovered", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal settlementRecovered = BigDecimal.ZERO;

    /*
     * --------------------------------------------------------
     * Remarks
     * --------------------------------------------------------
     */

    @Column(columnDefinition = "TEXT")
    private String remarks;

    /*
     * --------------------------------------------------------
     * Audit
     * --------------------------------------------------------
     */

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "recovered_at")
    private LocalDateTime recoveredAt;

    @Column(name = "recovered_by", length = 100)
    private String recoveredBy;
}
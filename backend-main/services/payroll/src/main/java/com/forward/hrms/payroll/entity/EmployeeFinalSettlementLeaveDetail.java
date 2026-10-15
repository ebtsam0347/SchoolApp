package com.forward.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_final_settlement_leave_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeFinalSettlementLeaveDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    private EmployeeFinalSettlement settlement;

    private Long leaveTypeId;

    private String leaveCode;

    private String leaveName;

    private LocalDate cycleStart;

    private LocalDate cycleEnd;

    private BigDecimal allocated;

    //private BigDecimal accrued;
    @Column(name="accrued")
    private BigDecimal earned;

    private BigDecimal used;

    private BigDecimal pending;

    private BigDecimal payableDays;

    @Column(name = "excessive_days")
    private BigDecimal excessiveDays;

    private BigDecimal encashmentAmount;

    @Column(name = "excessive_amount")
    private BigDecimal excessiveAmount;

    @CreationTimestamp
    private LocalDateTime createdAt;
}

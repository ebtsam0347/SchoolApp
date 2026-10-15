package com.forward.hrms.payroll.entity;

import com.forward.hrms.payroll.enums.BonusEventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_final_settlement_bonus_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeFinalSettlementBonusDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id")
    private EmployeeFinalSettlement settlement;

    @Column(nullable = false)
    private Long bonusEventId;

    @Column(nullable = false)
    private String eventName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BonusEventType eventType;

    private LocalDate referenceDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal eligibleAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal outstandingAmount = BigDecimal.ZERO;

    @CreationTimestamp
    private LocalDateTime createdAt;
}

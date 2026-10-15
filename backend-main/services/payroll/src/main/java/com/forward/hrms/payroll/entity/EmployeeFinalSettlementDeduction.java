package com.forward.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_final_settlement_deductions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeFinalSettlementDeduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    private EmployeeFinalSettlement settlement;

    private String deductionCode;

    private String deductionName;

    private BigDecimal amount;

    @CreationTimestamp
    private LocalDateTime createdAt;

    // @Column(name = "recovered_at")
    //private LocalDateTime recoveredAt;
    //
    //@Column(name = "recovered_by")
    //private Long recoveredBy;
}

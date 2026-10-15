package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.belonging.ActivationStatus;
import com.forward.hrms.employee.enums.belonging.BelongingCondition;
import com.forward.hrms.employee.enums.belonging.ClearanceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "belonging_issuance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BelongingIssuance extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "belonging_master_id", nullable = false)
    private BelongingMaster belongingMaster;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    private String assetModel;

    private Short assetYear;

    private BigDecimal assetPrice;

    private LocalDate issuedDate;

    private LocalDate expiryDate;

    private BigDecimal buyBackPercEmployee;

    private BigDecimal buyBackPercEmployer;

    @Enumerated(EnumType.STRING)
    private BelongingCondition condition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivationStatus activationStatus;

    @Column(nullable = false)
    private LocalDate activationDate;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    private ClearanceStatus clearanceStatus;

    private String clearanceId;

    private LocalDate clearanceDate;

}

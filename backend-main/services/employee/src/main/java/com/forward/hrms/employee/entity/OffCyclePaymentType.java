package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "off_cycle_payment_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffCyclePaymentType extends AuditedEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * {@code @Builder.Default} is load-bearing: without it Lombok drops
     * the initializer ("@Builder will ignore the initializing expression"),
     * so {@code builder()} left {@code active} null and the insert failed
     * against a NOT NULL column.
     */
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
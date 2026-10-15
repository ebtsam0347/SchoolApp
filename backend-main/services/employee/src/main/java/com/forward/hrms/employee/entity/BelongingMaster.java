package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "belonging_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BelongingMaster extends AuditedEntity {

    @Column(name = "belonging_name", nullable = false, unique = true)
    private String belongingName;

    @Column(name = "authorized_employee_id", nullable = false)
    private Long authorizedEmployeeId;

    @Builder.Default
    private Boolean active = true;

}
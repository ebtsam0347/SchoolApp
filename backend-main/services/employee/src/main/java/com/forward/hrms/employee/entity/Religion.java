package com.forward.hrms.employee.entity;


import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "religion")
public class Religion extends AuditedEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
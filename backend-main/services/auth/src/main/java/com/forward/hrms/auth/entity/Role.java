package com.forward.hrms.auth.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@Getter
@Setter
public class Role extends AuditedEntity {

    @Column(name = "code", nullable = false, length = 64, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** True for Flyway-seeded roles — refused as DELETE targets. */
    @Column(name = "system", nullable = false)
    private boolean system;

    /**
     * True for roles that back a machine identity (POLLER, ATTENDANCE_SVC).
     * Hidden from the default role catalog so HR can't accidentally grant
     * them to a person; the role admin screen pulls them with
     * {@code ?includeServiceAccounts=true}.
     */
    @Column(name = "service_account", nullable = false)
    private boolean serviceAccount;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();
}

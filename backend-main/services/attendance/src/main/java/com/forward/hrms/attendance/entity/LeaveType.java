package com.forward.hrms.attendance.entity;

import com.forward.hrms.attendance.enums.LeaveCycleAnchor;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "leave_types")
@Getter
@Setter
public class LeaveType extends AuditedEntity {

    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "paid", nullable = false)
    private boolean paid = true;

    @Column(name = "default_per_year", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultPerYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_anchor", nullable = false, length = 16)
    private LeaveCycleAnchor cycleAnchor = LeaveCycleAnchor.NONE;

    /**
     * Prorate the first cycle for an employee who joins mid-cycle.
     * Only meaningful for {@link LeaveCycleAnchor#FISCAL} types
     * (M1 / Casual); JOINING types are always full from the joining date.
     */
    @Column(name = "prorate_on_entry", nullable = false)
    private boolean prorateOnEntry = false;

    /**
     * Whether unused balance is paid out (encashed) when the cycle closes
     * at renewal. Annual leave only today. Encashment emits a
     * {@code leave.encashed} event in days; payroll values the payout.
     */
    @Column(name = "encashable", nullable = false)
    private boolean encashable = false;

    @Column(name = "carry_forward_max", precision = 5, scale = 2)
    private BigDecimal carryForwardMax = BigDecimal.ZERO;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "show_on_dashboard", nullable = false)
    private boolean showOnDashboard = true;
}

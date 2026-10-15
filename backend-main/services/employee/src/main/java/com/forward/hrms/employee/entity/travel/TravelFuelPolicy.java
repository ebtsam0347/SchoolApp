package com.forward.hrms.employee.entity.travel;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Master fuel/petrol issuance policy maintained by Transport.
 * <p>
 * {@code code} IS the vehicle-source code — one of the {@code VehicleSource}
 * values (COMPANY, EMPLOYEE, RENTAL, PUBLIC, OTHER). There is no separate
 * vehicle-source field: Transport verification resolves the applicable
 * policy for a leg by looking up {@code code = leg.vehicleSourceCode}
 * directly (see {@code TravelRequestService.transportVerify}).
 * <p>
 * {@code fuelAverage} is kilometres per liter; Transport verification
 * divides a leg's Transport-approved {@code actualDistanceKm} by it to
 * derive {@code petrolIssued}. {@code fuelAverage = 0} is a valid row
 * (no petrol issued, e.g. a company-fueled vehicle) — never treated as
 * missing configuration.
 */
@Entity
@Table(name = "travel_fuel_policies")
@Getter
@Setter
public class TravelFuelPolicy extends AuditedEntity {

    /**
     * The vehicle-source code this policy is for — must be one of the
     * {@code VehicleSource} values (COMPANY, EMPLOYEE, RENTAL, PUBLIC,
     * OTHER). Multiple historical (inactive) rows may share the same code;
     * only one may be active at a time (see
     * {@code uq_travel_fuel_policy_active_code}).
     */
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    /**
     * Human-readable name of the fuel policy.
     */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * Fuel average in kilometres per liter. Zero validly means no
     * petrol is issued under this policy.
     */
    @Column(name = "fuel_average", nullable = false, precision = 12, scale = 2)
    private BigDecimal fuelAverage;

    /**
     * Date from which this fuel policy becomes applicable.
     */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /**
     * Date until which this fuel policy remains applicable. NULL means
     * no defined end date.
     */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /**
     * Whether Transport can currently apply this fuel policy.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * JPA/Hibernate optimistic-locking version.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}

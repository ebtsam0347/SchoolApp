package com.forward.hrms.employee.entity.travel;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.travel.TravelRequestLegStatus;
import com.forward.hrms.employee.enums.travel.VehicleSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents one journey segment belonging to a Travel Request.
 * <p>
 * A Travel Request may contain multiple legs.
 * <p>
 * Example:
 * <p>
 * Travel Request:
 * TR-2026-000125
 * <p>
 * Legs:
 * 1. Sialkot      -> Lahore
 * 2. Lahore       -> Faisalabad
 * 3. Faisalabad   -> Lahore
 * 4. Lahore       -> Sialkot
 * <p>
 * The leg stores route, timing, transportation, vehicle and
 * distance information specific to that segment of the journey.
 */
@Entity
@Table(name = "travel_request_legs")
@Getter
@Setter
public class TravelRequestLeg extends AuditedEntity {

    /**
     * Travel Request to which this journey leg belongs.
     * <p>
     * Stored as a plain {@code Long} FK (project convention); the DB
     * foreign key {@code fk_travel_request_legs_request} lives in the
     * migration.
     */
    @Column(name = "travel_request_id", nullable = false)
    private Long travelRequestId;

    /**
     * Sequential position of this leg within the Travel Request.
     * <p>
     * Example:
     * <p>
     * 1 = Sialkot -> Lahore
     * 2 = Lahore -> Faisalabad
     * 3 = Faisalabad -> Lahore
     */
    @Column(name = "leg_sequence", nullable = false)
    private Integer legSequence;

    /**
     * Business code identifying the origin location.
     * <p>
     * Examples:
     * <p>
     * SIALKOT
     * LAHORE
     * FAISALABAD
     */
    @Column(name = "origin_location_code", nullable = false, length = 100)
    private String originLocationCode;

    /**
     * Human-readable snapshot of the origin location.
     * <p>
     * Example:
     * <p>
     * Sialkot Factory
     * <p>
     * Stored as a snapshot so historical travel records do not
     * depend on a future change to the location's display name.
     */
    @Column(
            name = "origin_location_name_snapshot",
            nullable = false,
            length = 200
    )
    private String originLocationNameSnapshot;

    /**
     * Business code identifying the destination location.
     */
    @Column(
            name = "destination_location_code",
            nullable = false,
            length = 100
    )
    private String destinationLocationCode;

    /**
     * Human-readable snapshot of the destination location.
     * <p>
     * Example:
     * <p>
     * Lahore Factory
     */
    @Column(
            name = "destination_location_name_snapshot",
            nullable = false,
            length = 200
    )
    private String destinationLocationNameSnapshot;

    /**
     * Planned departure date/time for this journey leg.
     */
    @Column(name = "planned_departure_at", nullable = false)
    private LocalDateTime plannedDepartureAt;

    /**
     * Planned arrival date/time.
     * <p>
     * Can be null when an arrival time has not yet been planned.
     */
    @Column(name = "planned_arrival_at")
    private LocalDateTime plannedArrivalAt;

    /**
     * Actual departure date/time.
     * <p>
     * NULL until the journey actually starts.
     */
    @Column(name = "actual_departure_at")
    private LocalDateTime actualDepartureAt;

    /**
     * Actual arrival date/time.
     * <p>
     * NULL until the journey leg is completed.
     */
    @Column(name = "actual_arrival_at")
    private LocalDateTime actualArrivalAt;

    /**
     * Business code identifying the transportation method.
     * <p>
     * Examples:
     * <p>
     * COMPANY_VEHICLE
     * PERSONAL_VEHICLE
     * HIRED_VEHICLE
     * PUBLIC_TRANSPORT
     * AIR
     * TRAIN
     */
    @Column(
            name = "transportation_type_code",
            nullable = false,
            length = 50
    )
    private String transportationTypeCode;

    /**
     * Business code identifying the source/provider of the
     * vehicle or transportation.
     * <p>
     * Examples:
     * <p>
     * COMPANY
     * EMPLOYEE
     * RENTAL
     * PUBLIC
     * OTHER
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_source_code", length = 50)
    private VehicleSource vehicleSourceCode;

    /**
     * Vehicle registration/identification number when applicable.
     * <p>
     * Example:
     * <p>
     * LEB-1234
     * <p>
     * Can be null for transportation such as air or train.
     */
    @Column(name = "vehicle_number", length = 100)
    private String vehicleNumber;

    /**
     * Optional descriptive information about the vehicle or
     * transportation.
     * <p>
     * Examples:
     * <p>
     * Toyota Corolla
     * Company Coaster
     * PIA Flight
     * Train 16DN
     */
    @Column(name = "vehicle_description", length = 250)
    private String vehicleDescription;

    /**
     * Planned distance of this journey leg in kilometres.
     */
    @Column(
            name = "planned_distance_km",
            precision = 12,
            scale = 2
    )
    private BigDecimal plannedDistanceKm;

    /**
     * Actual distance traveled in kilometres.
     * <p>
     * This may differ from the planned distance when the actual
     * route changes.
     */
    @Column(
            name = "actual_distance_km",
            precision = 12,
            scale = 2
    )
    private BigDecimal actualDistanceKm;

    /**
     * Current lifecycle status of this journey leg.
     * <p>
     * PLANNED
     * IN_PROGRESS
     * COMPLETED
     * CANCELLED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "leg_status", nullable = false, length = 20)
    private TravelRequestLegStatus legStatus = TravelRequestLegStatus.PLANNED;

    /**
     * Optional instructions or additional information specific
     * to this journey leg.
     * <p>
     * Example:
     * <p>
     * Pickup from Lahore office at 07:00 AM.
     */
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    /**
     * Technical optimistic-locking version.
     * <p>
     * Prevents concurrent updates from silently overwriting
     * changes made by another user/process.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Fuel policy Transport applied to this leg at verification time.
     * {@code null} when this leg has no fuel issuance (e.g. air/train/public
     * transport legs Transport never assigns a fuel policy to).
     */
    @Column(name = "travel_fuel_policy_id")
    private Long travelFuelPolicyId;

    /**
     * Petrol quantity issued for this leg, calculated at Transport
     * verification as {@link #actualDistanceKm} / the fuel policy's
     * fuel average — never {@link #plannedDistanceKm}. Zero when the
     * policy's fuel average is zero or no policy applies.
     */
    @Column(
            name = "petrol_issued",
            precision = 12,
            scale = 2,
            nullable = false
    )
    private BigDecimal petrolIssued = BigDecimal.ZERO;

    /**
     * Distance to use for distance-based calculations (PER_KM entitlements,
     * estimates): the Transport-verified {@link #actualDistanceKm} when it is
     * present and {@code > 0}, otherwise the requester's
     * {@link #plannedDistanceKm}. A {@code null} or {@code 0} actual distance
     * is treated as "not verified", never as a zero-length journey.
     */
    public BigDecimal effectiveDistanceKm() {
        if (actualDistanceKm != null && actualDistanceKm.compareTo(BigDecimal.ZERO) > 0) {
            return actualDistanceKm;
        }
        return plannedDistanceKm;
    }
}

package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.TourStatus;
import com.forward.hrms.employee.enums.TourType;
import com.forward.hrms.employee.enums.VehicleSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "factory_tours")
@Getter
@Setter
public class FactoryTour extends AuditedEntity {

    @Column(name = "request_id", length = 24, unique = true)
    private String requestId;

    @Column(name = "request_date")
    private LocalDate requestDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_type", nullable = false, length = 16)
    private TourType travelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TourStatus status = TourStatus.DRAFT;

    @Column(name = "card_no", length = 32)
    private String cardNo;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "section_id")
    private Long sectionId;

    @Column(name = "designation_id")
    private Long designationId;

    @Column(name = "grade_id")
    private Long gradeId;

    @Column(name = "tour_start")
    private LocalDateTime tourStart;

    @Column(name = "tour_end")
    private LocalDateTime tourEnd;

    @Column(name = "origin", length = 128)
    private String origin;

    @Column(name = "destination", length = 128)
    private String destination;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "country", length = 64)
    private String country;

    @Column(name = "currency", length = 8)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_source", length = 24)
    private VehicleSource vehicleSource;

    @Column(name = "transportation_type", length = 64)
    private String transportationType;

    @Column(name = "vehicle_no", length = 32)
    private String vehicleNo;

    @Column(name = "purpose")
    private String purpose;

    @Column(name = "total_distance_km")
    private BigDecimal totalDistanceKm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_travel_persons", columnDefinition = "jsonb")
    private String additionalTravelPersons;

    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "reject_reason")
    private String rejectReason;
}

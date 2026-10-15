package com.forward.hrms.attendance.entity;

import com.forward.hrms.attendance.enums.DeviceStatus;
import com.forward.hrms.attendance.enums.DeviceType;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;

/**
 * Catalog of biometric / kiosk devices that emit punches AND receive
 * user enrollments. {@code device_ip} + {@code commPort} +
 * {@code commPassword} are read by the attendance-poller sidecar to
 * open a ZK-protocol session. Exactly one row per company should
 * carry {@code isEnrollment = true} — that's where HR captures
 * fingerprints / faces in person; every other device receives the
 * captured templates by replication. Status / lastSeenAt are written
 * by the poller's heartbeat call (see DeviceSyncService).
 */
@Entity
@Table(name = "attendance_devices")
@Getter
@Setter
public class AttendanceDevice extends AuditedEntity {

    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "location", length = 128)
    private String location;

    @Column(name = "device_ip", columnDefinition = "inet")
    @ColumnTransformer(write = "?::inet")
    private String deviceIp;

    @Column(name = "comm_port", nullable = false)
    private int commPort = 4370;

    @Column(name = "comm_password", nullable = false)
    private int commPassword = 0;

    @Column(name = "is_enrollment", nullable = false)
    private boolean enrollment = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DeviceStatus status = DeviceStatus.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 16)
    private DeviceType deviceType = DeviceType.ATTENDANCE;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}

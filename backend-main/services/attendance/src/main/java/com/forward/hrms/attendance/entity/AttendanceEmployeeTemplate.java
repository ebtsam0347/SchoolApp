package com.forward.hrms.attendance.entity;

import com.forward.hrms.attendance.enums.TemplateKind;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Captured biometric template stored centrally and replicated to
 * every device the employee is enrolled on. {@code fingerId} is
 * 0..9 for FINGERPRINT, null for FACE (one face per employee).
 * {@code templateFormat} is the firmware-reported format code; the
 * poller validates compatibility before each push and fails the
 * task non-retryably on mismatch.
 */
@Entity
@Table(name = "attendance_employee_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_templates_employee_kind_finger",
                columnNames = {"employee_id", "kind", "finger_id"}))
@Getter
@Setter
public class AttendanceEmployeeTemplate extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private TemplateKind kind;

    @Column(name = "finger_id")
    private Short fingerId;

    @Column(name = "template", nullable = false)
    private byte[] template;

    @Column(name = "template_format", nullable = false)
    private short templateFormat;

    @Column(name = "captured_device_id", nullable = false)
    private Long capturedDeviceId;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;
}

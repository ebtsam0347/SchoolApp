package com.forward.hrms.attendance.entity;

import com.forward.hrms.attendance.enums.OvertimeStatus;
import com.forward.hrms.attendance.enums.OvertimeType;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "overtime_requests")
@Getter
@Setter
public class OvertimeRequest extends AuditedEntity {

    @Column(name = "request_code", nullable = false, length = 20, updatable = false)
    private String requestCode;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "overtime_type", nullable = false, length = 16)
    private OvertimeType overtimeType;

    /** Null only on requests filed before V0027; required on every new one. */
    @Convert(converter = FactoryCodeConverter.class)
    @Column(name = "factory_code", length = 16)
    private com.forward.hrms.attendance.enums.FactoryCode factoryCode;

    @Column(name = "bulk_batch_id")
    private UUID bulkBatchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OvertimeStatus status;
}

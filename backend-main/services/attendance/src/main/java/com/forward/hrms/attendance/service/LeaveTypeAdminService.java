package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.request.CreateLeaveTypeRequest;
import com.forward.hrms.attendance.dto.request.UpdateLeaveTypeRequest;
import com.forward.hrms.attendance.entity.LeaveType;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.repository.LeaveTypeRepository;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LeaveTypeAdminService {

    private final LeaveTypeRepository leaveTypes;
    private final AuditEmitter audit;

    @Transactional
    public LeaveType create(CreateLeaveTypeRequest req) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (leaveTypes.existsByCode(code)) {
            throw new ConflictError(AttendanceErrorCode.LEAVE_TYPE_CODE_TAKEN,
                    "leave type code already exists: " + code);
        }
        LeaveType t = new LeaveType();
        t.setCode(code);
        t.setName(req.name().trim());
        t.setPaid(req.paid() == null || req.paid());
        t.setDefaultPerYear(req.defaultPerYear());
        t.setCycleAnchor(req.cycleAnchor());
        t.setProrateOnEntry(req.prorateOnEntry() != null && req.prorateOnEntry());
        t.setEncashable(req.encashable() != null && req.encashable());
        t.setCarryForwardMax(req.carryForwardMax() == null ? BigDecimal.ZERO : req.carryForwardMax());
        t.setRequiresApproval(req.requiresApproval() == null || req.requiresApproval());
        t.setActive(true);
        t.setShowOnDashboard(req.showOnDashboard() == null || req.showOnDashboard());
        t = leaveTypes.save(t);
        audit.emit("leave_type", String.valueOf(t.getId()), "created", null, snapshot(t), false);
        return t;
    }

    @Transactional
    public LeaveType update(long id, UpdateLeaveTypeRequest req) {
        LeaveType t = leaveTypes.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.LEAVE_TYPE_NOT_FOUND,
                        "leave type not found"));
        Map<String, Object> before = snapshot(t);
        if (req.name() != null) t.setName(req.name().trim());
        if (req.paid() != null) t.setPaid(req.paid());
        if (req.defaultPerYear() != null) t.setDefaultPerYear(req.defaultPerYear());
        if (req.cycleAnchor() != null) t.setCycleAnchor(req.cycleAnchor());
        if (req.prorateOnEntry() != null) t.setProrateOnEntry(req.prorateOnEntry());
        if (req.encashable() != null) t.setEncashable(req.encashable());
        if (req.carryForwardMax() != null) t.setCarryForwardMax(req.carryForwardMax());
        if (req.requiresApproval() != null) t.setRequiresApproval(req.requiresApproval());
        if (req.active() != null) t.setActive(req.active());
        if (req.showOnDashboard() != null) t.setShowOnDashboard(req.showOnDashboard());
        t = leaveTypes.save(t);
        audit.emit("leave_type", String.valueOf(t.getId()), "updated", before, snapshot(t), false);
        return t;
    }

    @Transactional
    public LeaveType deactivate(long id) {
        LeaveType t = leaveTypes.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.LEAVE_TYPE_NOT_FOUND,
                        "leave type not found"));
        Map<String, Object> before = snapshot(t);
        t.setActive(false);
        t = leaveTypes.save(t);
        audit.emit("leave_type", String.valueOf(t.getId()), "deactivated", before, snapshot(t), false);
        return t;
    }

    private static Map<String, Object> snapshot(LeaveType t) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", t.getCode());
        m.put("name", t.getName());
        m.put("paid", t.isPaid());
        m.put("cycleAnchor", t.getCycleAnchor() == null ? null : t.getCycleAnchor().name());
        m.put("prorateOnEntry", t.isProrateOnEntry());
        m.put("encashable", t.isEncashable());
        m.put("defaultPerYear", t.getDefaultPerYear());
        m.put("active", t.isActive());
        return m;
    }
}

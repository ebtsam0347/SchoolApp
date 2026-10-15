package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.request.CreateAttendanceDeviceRequest;
import com.forward.hrms.attendance.dto.request.SetDeviceCommPasswordRequest;
import com.forward.hrms.attendance.dto.request.UpdateAttendanceDeviceRequest;
import com.forward.hrms.attendance.dto.response.AttendanceDeviceDto;
import com.forward.hrms.attendance.dto.response.PollerTargetDto;
import com.forward.hrms.attendance.entity.AttendanceDevice;
import com.forward.hrms.attendance.entity.DevicePollState;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.enums.DeviceType;
import com.forward.hrms.attendance.repository.AttendanceDeviceRepository;
import com.forward.hrms.attendance.repository.DevicePollStateRepository;
import com.forward.hrms.attendance.repository.PunchUnmatchedRepository;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AttendanceDeviceService {

    private final AttendanceDeviceRepository devices;
    private final DevicePollStateRepository pollState;
    private final PunchUnmatchedRepository unmatched;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<AttendanceDeviceDto> list(boolean includeInactive) {
        List<AttendanceDevice> rows = includeInactive
                ? devices.findAllByOrderByCodeAsc()
                : devices.findAllByActiveTrueOrderByCodeAsc();
        return rows.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AttendanceDeviceDto get(long id) {
        return toDto(load(id));
    }

    /**
     * Read-side feed for the attendance-poller sidecar. Returns active
     * devices with a non-null {@code deviceIp}, joined to their
     * high-water-mark from {@code attendance_device_poll_state}. Includes
     * the ZK comm password — gate to {@code attendance.poller}.
     */
    @Transactional(readOnly = true)
    public List<PollerTargetDto> pollerTargets() {
        return devices.findAllByActiveTrueAndDeviceIpIsNotNullOrderByCodeAsc().stream()
                .map(d -> new PollerTargetDto(
                        d.getId(), d.getCode(), d.getDeviceIp(), d.getCommPort(), d.getCommPassword(),
                        pollState.findById(d.getId())
                                .map(DevicePollState::getLastPunchedAt)
                                .orElse(null)))
                .toList();
    }

    /**
     * Operator op — null the high-water-mark so the next poller cycle
     * pulls every record on the device. Useful when a device's clock was
     * reset or the bridge rows for a swathe of cards landed late.
     */
    @Transactional
    public AttendanceDeviceDto resetHwm(long id) {
        AttendanceDevice d = load(id);
        DevicePollState state = pollState.findById(id).orElse(null);
        if (state == null) {
            // Nothing to reset — don't audit a "we did the thing" with previous=null.
            return toDto(d);
        }
        LocalDateTime previous = state.getLastPunchedAt();
        state.setLastPunchedAt(null);
        audit.emit("attendance_device", String.valueOf(id), "hwm.reset",
                previous == null ? null : Map.of("lastPunchedAt", previous.toString()),
                Map.of("lastPunchedAt", ""), false);
        return toDto(d);
    }

    @Transactional
    public AttendanceDeviceDto create(CreateAttendanceDeviceRequest req) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (devices.existsByCode(code)) {
            throw new ConflictError(AttendanceErrorCode.DEVICE_CODE_TAKEN,
                    "device code already exists: " + code);
        }
        AttendanceDevice d = new AttendanceDevice();
        d.setCode(code);
        d.setName(req.name().trim());
        d.setLocation(req.location());
        d.setDeviceIp(req.deviceIp());
        if (req.commPort() != null) d.setCommPort(req.commPort());
        if (req.enrollment() != null) d.setEnrollment(req.enrollment());
        if (req.deviceType() != null) d.setDeviceType(req.deviceType());
        d.setActive(true);
        // commPassword defaults to 0 (no password). HR sets it later via
        // POST /devices/{id}/comm-password — kept off the create path so
        // the value never appears in any response DTO.
        AttendanceDevice saved = devices.save(d);
        audit.emit("attendance_device", String.valueOf(saved.getId()), "created",
                null, snapshot(saved), false);
        return toDto(saved);
    }

    @Transactional
    public AttendanceDeviceDto update(long id, UpdateAttendanceDeviceRequest req) {
        AttendanceDevice d = load(id);
        // §7.attendance #2 — capture a before/after diff so flips of
        // active / deviceIp / location aren't silent operationally.
        Map<String, Object> before = snapshot(d);
        if (req.name() != null && !req.name().isBlank()) d.setName(req.name().trim());
        if (req.location() != null) d.setLocation(req.location());
        if (req.deviceIp() != null) d.setDeviceIp(req.deviceIp());
        if (req.commPort() != null) d.setCommPort(req.commPort());
        if (req.enrollment() != null) d.setEnrollment(req.enrollment());
        if (req.deviceType() != null) d.setDeviceType(req.deviceType());
        if (req.active() != null) d.setActive(req.active());
        Map<String, Object> after = snapshot(d);
        if (!before.equals(after)) {
            audit.emit("attendance_device", String.valueOf(d.getId()), "updated",
                    before, after, false);
        }
        return toDto(d);
    }

    /**
     * Set the ZK comm password for a device. Write-only path — the
     * current value never leaves the service, only a {@code commPasswordSet}
     * boolean does. The value is still stored as plaintext on the row;
     * at-rest encryption is a follow-up that needs a commons crypto helper
     * we don't have yet.
     */
    @Transactional
    public AttendanceDeviceDto setCommPassword(long id, SetDeviceCommPasswordRequest req) {
        AttendanceDevice d = load(id);
        d.setCommPassword(req.commPassword());
        // §7.attendance #2 — never log the value, just the fact that it
        // was set. before/after both omit the password.
        audit.emit("attendance_device", String.valueOf(d.getId()), "comm_password.set",
                null, Map.of("commPasswordSet", true), false);
        return toDto(d);
    }

    @Transactional
    public AttendanceDeviceDto deactivate(long id) {
        AttendanceDevice d = load(id);
        if (!d.isActive()) return toDto(d);
        d.setActive(false);
        audit.emit("attendance_device", String.valueOf(d.getId()), "deactivated",
                Map.of("active", true), Map.of("active", false), false);
        return toDto(d);
    }

    @Transactional(readOnly = true)
    public List<AttendanceDeviceDto> getCanteenDevices() {
        return devices.findAllByDeviceTypeOrderByDeviceIp(DeviceType.CANTEEN).stream()
                .map(this::toDto)
                .toList();
    }

    /** Audit-friendly snapshot — note: commPassword value is intentionally omitted. */
    private static Map<String, Object> snapshot(AttendanceDevice d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", d.getCode());
        m.put("name", d.getName());
        m.put("location", d.getLocation() == null ? "" : d.getLocation());
        m.put("deviceIp", d.getDeviceIp() == null ? "" : d.getDeviceIp());
        m.put("commPort", d.getCommPort());
        m.put("enrollment", d.isEnrollment());
        m.put("deviceType", d.getDeviceType().name());
        m.put("active", d.isActive());
        m.put("commPasswordSet", d.getCommPassword() != 0);
        return m;
    }

    private AttendanceDevice load(long id) {
        return devices.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.DEVICE_NOT_FOUND,
                        "attendance device not found"));
    }

    private AttendanceDeviceDto toDto(AttendanceDevice d) {
        LocalDateTime hwm = pollState.findById(d.getId())
                .map(DevicePollState::getLastPunchedAt)
                .orElse(null);
        long pending = unmatched.countByDeviceId(d.getId());
        return AttendanceDeviceDto.of(d, hwm, pending);
    }
}

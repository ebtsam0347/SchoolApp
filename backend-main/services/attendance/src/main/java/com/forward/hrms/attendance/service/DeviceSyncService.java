package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.request.HeartbeatRequest;
import com.forward.hrms.attendance.entity.AttendanceDevice;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.repository.AttendanceDeviceRepository;
import com.forward.hrms.commons.core.error.NotFoundError;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Per-cycle device heartbeat from the attendance-poller sidecar.
 * Deliberately small and orphan to both {@link DeviceSyncQueueService}
 * (sync-task lifecycle) and {@link DeviceSnapshotService} (snapshot
 * lifecycle): heartbeat doesn't share state with either, so it lives
 * here as the home for poller-touching device-status updates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSyncService {

    private final AttendanceDeviceRepository devices;

    @Transactional
    public void heartbeat(long deviceId, HeartbeatRequest req) {
        AttendanceDevice device = requireDevice(deviceId);
        device.setStatus(req.status());
        device.setLastSeenAt(LocalDateTime.now());
        if (req.firmwareVersion() != null || req.serialNumber() != null) {
            log.info("device {} heartbeat: status={} firmware={} serial={} deviceTime={}",
                    deviceId, req.status(), req.firmwareVersion(), req.serialNumber(), req.deviceTime());
        }
    }

    private AttendanceDevice requireDevice(long deviceId) {
        return devices.findById(deviceId)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.DEVICE_NOT_FOUND,
                        "attendance device not found: " + deviceId));
    }
}

package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.DeviceSnapshot;
import com.forward.hrms.attendance.enums.DeviceSnapshotStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface DeviceSnapshotRepository extends JpaRepository<DeviceSnapshot, Long> {

    Page<DeviceSnapshot> findByDeviceId(Long deviceId, Pageable pageable);

    Page<DeviceSnapshot> findByDeviceIdAndStatusIn(
            Long deviceId, Collection<DeviceSnapshotStatus> statuses, Pageable pageable);
}

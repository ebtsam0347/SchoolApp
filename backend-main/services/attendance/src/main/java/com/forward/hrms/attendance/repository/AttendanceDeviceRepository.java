package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.AttendanceDevice;

import com.forward.hrms.attendance.enums.DeviceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceDeviceRepository extends JpaRepository<AttendanceDevice, Long> {

    Optional<AttendanceDevice> findByCode(String code);

    boolean existsByCode(String code);

    List<AttendanceDevice> findAllByActiveTrueOrderByCodeAsc();

    List<AttendanceDevice> findAllByOrderByCodeAsc();

    /** Poller-targets feed — only devices the poller can actually dial. */
    List<AttendanceDevice> findAllByActiveTrueAndDeviceIpIsNotNullOrderByCodeAsc();

    //devices that have type 'CANTEEN'
    List<AttendanceDevice> findAllByDeviceTypeOrderByDeviceIp(DeviceType deviceType);
}

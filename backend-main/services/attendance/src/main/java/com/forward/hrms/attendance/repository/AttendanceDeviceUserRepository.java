package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.AttendanceDeviceUser;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceDeviceUserRepository extends JpaRepository<AttendanceDeviceUser, Long> {

    List<AttendanceDeviceUser> findByDeviceId(Long deviceId);

    Page<AttendanceDeviceUser> findByDeviceId(Long deviceId, Pageable pageable);

    List<AttendanceDeviceUser> findByEmployeeId(Long employeeId);

    Optional<AttendanceDeviceUser> findByDeviceIdAndEmployeeId(Long deviceId, Long employeeId);
}

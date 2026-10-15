package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.AttendanceEmployeeTemplate;
import com.forward.hrms.attendance.enums.TemplateKind;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceEmployeeTemplateRepository extends JpaRepository<AttendanceEmployeeTemplate, Long> {

    List<AttendanceEmployeeTemplate> findByEmployeeId(Long employeeId);

    Optional<AttendanceEmployeeTemplate> findByEmployeeIdAndKindAndFingerId(
            Long employeeId, TemplateKind kind, Short fingerId);
}

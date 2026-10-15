package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.AttendanceEmployee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceEmployeeRepository extends JpaRepository<AttendanceEmployee, Long> {

    Optional<AttendanceEmployee> findByEmployeeId(Long employeeId);

    Optional<AttendanceEmployee> findByEmployeeCode(String employeeCode);

    /** Bulk lookup — kills the N+1 in report-side enrichment. */
    List<AttendanceEmployee> findAllByEmployeeIdIn(Collection<Long> employeeIds);

    /** Bulk lookup by code — snapshot reconciliation matches device user_ids in one query. */
    List<AttendanceEmployee> findAllByEmployeeCodeIn(Collection<String> employeeCodes);

    /**
     * Active-employee selection for the nightly close. Mirrors the old
     * {@code GET /employees?status=ACTIVE} filter — reads the local mirror
     * instead of calling employee-service.
     */
    List<AttendanceEmployee> findAllByEmploymentStatus(String employmentStatus);

    void deleteByEmployeeId(Long employeeId);
}

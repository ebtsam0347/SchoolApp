package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.CanteenAttendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface CanteenAttendanceRepository extends JpaRepository<CanteenAttendance, Long> {

    boolean existsByEmployeeIdAndAttendanceDate(
            Long employeeId, LocalDate attendanceDate);

    /**
     * Register listing. Date bounds are always supplied (the service
     * defaults to today when the caller sends none), {@code employeeId}
     * is optional — covers "today", "date range", and "employee +
     * date range" from one query.
     */
    @Query("""
            select c from CanteenAttendance c
            where c.attendanceDate between :from and :to
              and (:employeeId is null or c.employeeId = :employeeId)
            """)
    Page<CanteenAttendance> search(@Param("from") LocalDate from,
                                   @Param("to") LocalDate to,
                                   @Param("employeeId") Long employeeId,
                                   Pageable pageable);

    long countByEmployeeIdAndAttendanceDateBetween(Long employeeId, LocalDate from, LocalDate to);

    @Query("""
            select coalesce(sum(c.count), 0)
            from CanteenAttendance c
            where c.employeeId = :employeeId
              and c.attendanceDate between :from and :to
            """)
    Long sumCountByEmployeeIdAndAttendanceDateBetween(@Param("employeeId") Long employeeId,
                                                       @Param("from") LocalDate from,
                                                       @Param("to") LocalDate to);
}

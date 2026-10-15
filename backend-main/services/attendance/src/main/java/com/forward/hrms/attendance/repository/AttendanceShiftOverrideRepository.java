package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.AttendanceShiftOverride;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceShiftOverrideRepository extends JpaRepository<AttendanceShiftOverride, Long> {

    @Query("""
            select o from AttendanceShiftOverride o
            where o.employeeId = :employeeId
              and o.fromDate <= :on
              and o.toDate >= :on
            """)
    Optional<AttendanceShiftOverride> findCovering(Long employeeId, LocalDate on);

    /** Overlap probe: does any existing override for {@code employeeId} touch [from..to]? */
    @Query("""
            select o from AttendanceShiftOverride o
            where o.employeeId = :employeeId
              and o.fromDate <= :to
              and o.toDate >= :from
              and (:excludeId is null or o.id <> :excludeId)
            """)
    List<AttendanceShiftOverride> findOverlapping(Long employeeId, LocalDate from, LocalDate to, Long excludeId);

    @Query("""
            select o from AttendanceShiftOverride o
            where o.fromDate <= :on and o.toDate >= :on
            order by o.employeeId, o.fromDate
            """)
    List<AttendanceShiftOverride> findAllCovering(LocalDate on);

    @Query("""
            select o from AttendanceShiftOverride o
            where o.employeeId = :employeeId
              and o.fromDate <= :to
              and o.toDate >= :from
            order by o.fromDate
            """)
    List<AttendanceShiftOverride> findInRange(Long employeeId, LocalDate from, LocalDate to);
}

package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.AttendancePunch;
import com.forward.hrms.attendance.enums.PunchSource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendancePunchRepository extends JpaRepository<AttendancePunch, Long> {

    Optional<AttendancePunch> findByEmployeeIdAndPunchedAt(Long employeeId, LocalDateTime punchedAt);

    List<AttendancePunch> findAllByEmployeeIdAndAttendanceDateOrderByPunchedAtAsc(
            Long employeeId, LocalDate attendanceDate);

    List<AttendancePunch> findAllByEmployeeIdAndAttendanceDateBetweenOrderByPunchedAtAsc(
            Long employeeId, LocalDate from, LocalDate to);

    @Query("""
            select count(p) from AttendancePunch p
            where p.employeeId = :employeeId and p.attendanceDate = :date and p.source = :source
            """)
    long countByEmployeeAndDateAndSource(Long employeeId, LocalDate date, PunchSource source);

    /**
     * Org-wide punches list. Date bounds are required (service defaults to
     * today/today when caller omits them). Service picks between this and
     * {@link #searchInIds} on whether dept/section/code resolved to ids.
     */
    @Query("""
            select p from AttendancePunch p
            where p.attendanceDate between :from and :to
              and (:employeeId is null or p.employeeId = :employeeId)
              and (:source is null or p.source = :source)
              and (:deviceId is null or p.deviceId = :deviceId)
            """)
    Page<AttendancePunch> search(@Param("from") LocalDate from,
                                 @Param("to") LocalDate to,
                                 @Param("employeeId") Long employeeId,
                                 @Param("source") PunchSource source,
                                 @Param("deviceId") Long deviceId,
                                 Pageable pageable);

    @Query("""
            select p from AttendancePunch p
            where p.attendanceDate between :from and :to
              and p.employeeId in :employeeIds
              and (:source is null or p.source = :source)
              and (:deviceId is null or p.deviceId = :deviceId)
            """)
    Page<AttendancePunch> searchInIds(@Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      @Param("employeeIds") Collection<Long> employeeIds,
                                      @Param("source") PunchSource source,
                                      @Param("deviceId") Long deviceId,
                                      Pageable pageable);
}

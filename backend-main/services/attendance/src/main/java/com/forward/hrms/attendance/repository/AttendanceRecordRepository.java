package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.AttendanceRecord;
import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.enums.ShiftPeriod;

import com.forward.hrms.attendance.repository.projection.TerminationAttendanceAggregate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate date);

    List<AttendanceRecord> findAllByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
            Long employeeId, LocalDate from, LocalDate to);

    List<AttendanceRecord> findAllByAttendanceDate(LocalDate date);

    /**
     * Org-wide filter list. Date bounds are required (the service supplies
     * defaults — typically today/today). The service picks between this method
     * and {@link #searchInIds} based on whether dept/section/code filters
     * resolved to a concrete id set; empty resolutions short-circuit in the
     * service before reaching the repo.
     */
    @Query("""
            select r from AttendanceRecord r
            where r.attendanceDate between :from and :to
              and (:employeeId is null or r.employeeId = :employeeId)
              and (:status is null or r.status = :status)
              and (:lateOnly = false or r.lateMinutes > 0)
              and (:missingCheckout = false or (r.checkIn is not null and r.checkOut is null))
            """)
    Page<AttendanceRecord> search(@Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  @Param("employeeId") Long employeeId,
                                  @Param("status") AttendanceStatus status,
                                  @Param("lateOnly") boolean lateOnly,
                                  @Param("missingCheckout") boolean missingCheckout,
                                  Pageable pageable);

    @Query("""
            select r from AttendanceRecord r
            where r.attendanceDate between :from and :to
              and r.employeeId in :employeeIds
              and (:status is null or r.status = :status)
              and (:lateOnly = false or r.lateMinutes > 0)
              and (:missingCheckout = false or (r.checkIn is not null and r.checkOut is null))
            """)
    Page<AttendanceRecord> searchInIds(@Param("from") LocalDate from,
                                       @Param("to") LocalDate to,
                                       @Param("employeeIds") Collection<Long> employeeIds,
                                       @Param("status") AttendanceStatus status,
                                       @Param("lateOnly") boolean lateOnly,
                                       @Param("missingCheckout") boolean missingCheckout,
                                       Pageable pageable);

    /**
     * Org-wide status roll-up for a single date. Result rows are
     * {@code [status: AttendanceStatus, count: Long]} — caller buckets
     * into the dashboard shape.
     */
    @Query("""
            select r.status, count(r) from AttendanceRecord r
            where r.attendanceDate = :date
            group by r.status
            """)
    List<Object[]> countByStatusOnDate(@Param("date") LocalDate date);

    /**
     * Org-wide per-day status roll-up over a range. Result rows are
     * {@code [date: LocalDate, status: AttendanceStatus, count: Long]} —
     * one row per (date, status) pair.
     */
    @Query("""
            select r.attendanceDate, r.status, count(r) from AttendanceRecord r
            where r.attendanceDate between :from and :to
            group by r.attendanceDate, r.status
            order by r.attendanceDate asc
            """)
    List<Object[]> countByDayStatus(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * late count
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM attendance.attendance_records
            WHERE attendance_date = :date
              AND late_minutes > 0
            """, nativeQuery = true)
    Long countLateByDate(@Param("date") LocalDate date);

    /** {@link #countByStatusOnDate} limited to records whose shift is {@code period}. */
    @Query("""
            select r.status, count(r) from AttendanceRecord r
            where r.attendanceDate = :date
              and r.effectiveShift in (select s.id from Shift s where s.period = :period)
            group by r.status
            """)
    List<Object[]> countByStatusOnDateAndShiftPeriod(@Param("date") LocalDate date,
                                                     @Param("period") ShiftPeriod period);

    /** {@link #countLateByDate} limited to records whose shift is {@code period}. */
    @Query("""
            select count(r) from AttendanceRecord r
            where r.attendanceDate = :date
              and r.lateMinutes > 0
              and r.effectiveShift in (select s.id from Shift s where s.period = :period)
            """)
    long countLateByDateAndShiftPeriod(@Param("date") LocalDate date,
                                       @Param("period") ShiftPeriod period);

    /** Employees whose record in the range sits on a {@code period} shift. */
    @Query("""
            select distinct r.employeeId from AttendanceRecord r
            where r.attendanceDate between :from and :to
              and r.effectiveShift in (select s.id from Shift s where s.period = :period)
            """)
    List<Long> findEmployeeIdsOnShiftPeriod(@Param("from") LocalDate from,
                                            @Param("to") LocalDate to,
                                            @Param("period") ShiftPeriod period);

    /**
     * Monthly group-by for the analytics service. One row per (year, month,
     * status); ~24 × 4 rows max for a 24-month window. Counts on-time
     * (lateMinutes = 0) and sums OT minutes per bucket so the caller never
     * hydrates individual rows. Result columns:
     * {@code [year: int, month: int, status: AttendanceStatus,
     * count: long, onTimeCount: long, otMinutes: long]}.
     */
    @Query("""
            select year(r.attendanceDate), month(r.attendanceDate), r.status,
                   count(r),
                   sum(case when r.lateMinutes = 0 then 1 else 0 end),
                   sum(r.otNormalMinutes + r.otExtraMinutes + r.otSpecialMinutes)
            from AttendanceRecord r
            where r.attendanceDate between :from and :to
            group by year(r.attendanceDate), month(r.attendanceDate), r.status
            """)
    List<Object[]> monthlyAggregate(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Batch lookup for {@code (employeeId, date)} pairs sharing a single
     * date. Replaces the BFF "team today" N+1 (one fetch per direct
     * report). Caller bucks results by {@code employeeId} after the call.
     */
    List<AttendanceRecord> findAllByEmployeeIdInAndAttendanceDate(
            Collection<Long> employeeIds, LocalDate date);

    /**
     * Batch range lookup; same shape as the per-employee range query but
     * across many employees. Used for the payroll batch snapshot.
     */
    List<AttendanceRecord> findAllByEmployeeIdInAndAttendanceDateBetweenOrderByEmployeeIdAscAttendanceDateAsc(
            Collection<Long> employeeIds, LocalDate from, LocalDate to);


    // new query method to get required attendance summarized data in one query for termination process.
    @Query(value = """
            SELECT
            
                COALESCE(SUM(CASE WHEN status = 'PRESENT' THEN 1 ELSE 0 END),0) AS presentDays,
                COALESCE(SUM(CASE WHEN status = 'HALF_DAY' THEN 1 ELSE 0 END),0) AS halfDays,
                COALESCE(SUM(CASE WHEN status = 'LEAVE' THEN 1 ELSE 0 END),0) AS leaveDays,
                COALESCE(SUM(CASE WHEN status = 'LEAVE' AND COALESCE(paid, true) = true THEN 1 ELSE 0 END),0) AS paidLeaveDays,
                COALESCE(SUM(CASE WHEN status = 'LEAVE' AND COALESCE(paid, true) = false THEN 1 ELSE 0 END),0) AS unpaidLeaveDays,
                COALESCE(SUM(CASE WHEN status = 'ABSENT' THEN 1 ELSE 0 END),0) AS absentDays,
                COALESCE(SUM(CASE WHEN status = 'WEEKEND' THEN 1 ELSE 0 END),0) AS weekendDays,
                COALESCE(SUM(CASE WHEN status = 'HOLIDAY' THEN 1 ELSE 0 END),0) AS holidayDays
            
                        FROM attendance.attendance_records
            WHERE employee_id = :employeeId
              AND attendance_date BETWEEN :from AND :to
            """,
            nativeQuery = true)
    TerminationAttendanceAggregate getTerminationAttendanceSummary(

            @Param("employeeId") Long employeeId,

            @Param("from") LocalDate from,

            @Param("to") LocalDate to

    );

    /**
     * Instead of loading the record, we only need to know whether one already exists.
     */
    boolean existsByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate attendanceDate);

    @Query("""
            select r.employeeId
            from AttendanceRecord r
            where r.attendanceDate = :date
            """)
    Set<Long> findEmployeeIdsByAttendanceDate(@Param("date") LocalDate date);
}

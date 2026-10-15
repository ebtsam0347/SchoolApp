package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.OvertimeRequest;
import com.forward.hrms.attendance.enums.OvertimeStatus;
import com.forward.hrms.attendance.enums.OvertimeType;

import com.forward.hrms.attendance.repository.projection.ApprovedOvertimeProjection;
import com.forward.hrms.attendance.repository.projection.PendingOvertimeProjection;
import com.forward.hrms.attendance.repository.projection.UnappliedOvertimeProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, Long> {

    Page<OvertimeRequest> findAllByEmployeeId(Long employeeId, Pageable pageable);

    /**
     * cast(... as ...) hints are mandatory — see LeaveRequestRepository.search.
     * Bare {@code :param is null} fails on Postgres with "could not determine
     * data type of parameter" because the JDBC driver has no type context.
     */
    @Query("""
            select o from OvertimeRequest o
            where o.employeeId = :employeeId
              and (cast(:status as string) is null or o.status = :status)
              and (cast(:from as date) is null or o.attendanceDate >= :from)
              and (cast(:to as date) is null or o.attendanceDate <= :to)
            """)
    Page<OvertimeRequest> search(
            @Param("employeeId") Long employeeId,
            @Param("status") OvertimeStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    /** {@link #search} across several employees — a manager's direct reports. */
    @Query("""
            select o from OvertimeRequest o
            where o.employeeId in :employeeIds
              and (cast(:status as string) is null or o.status = :status)
              and (cast(:from as date) is null or o.attendanceDate >= :from)
              and (cast(:to as date) is null or o.attendanceDate <= :to)
            """)
    Page<OvertimeRequest> searchForEmployees(
            @Param("employeeIds") java.util.Collection<Long> employeeIds,
            @Param("status") OvertimeStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    @Query("""
            select o from OvertimeRequest o
            where o.employeeId = :employeeId
              and o.attendanceDate = :date
              and o.status in (com.forward.hrms.attendance.enums.OvertimeStatus.PENDING,
                               com.forward.hrms.attendance.enums.OvertimeStatus.APPROVED)
            """)
    List<OvertimeRequest> findActiveForDate(Long employeeId, LocalDate date);

    List<OvertimeRequest> findAllByBulkBatchIdOrderByEmployeeIdAsc(UUID bulkBatchId);

    boolean existsByEmployeeIdAndAttendanceDateAndOvertimeTypeAndStatusIn(
            Long employeeId, LocalDate date, OvertimeType overtimeType, java.util.Collection<OvertimeStatus> statuses);

    List<OvertimeRequest> findAllByEmployeeIdAndAttendanceDateAndStatus(
            Long employeeId, LocalDate date, OvertimeStatus status);

    @Query("""
            select o from OvertimeRequest o
            where o.employeeId = :employeeId
              and o.status in (com.forward.hrms.attendance.enums.OvertimeStatus.PENDING,
                               com.forward.hrms.attendance.enums.OvertimeStatus.APPROVED)
              and o.attendanceDate >= :onOrAfter
            """)
    List<OvertimeRequest> findActiveOnOrAfter(@Param("employeeId") Long employeeId,
                                              @Param("onOrAfter") LocalDate onOrAfter);

    // new query method to get all the data for pending overtime as summary detailed.
    @Query(value = """
            
            SELECT
            
                COUNT(DISTINCT ot.id)                                              AS pendingRequests,
            
                COALESCE(SUM(ar.ot_normal_minutes),0)                              AS normalMinutes,
            
                COALESCE(SUM(ar.ot_extra_minutes),0)                               AS extraMinutes,
            
                COALESCE(SUM(ar.ot_special_minutes),0)                             AS specialMinutes
            
            FROM attendance.overtime_requests ot
            
            JOIN attendance.attendance_records ar
            
            ON ar.employee_id = ot.employee_id
            AND ar.attendance_date = ot.attendance_date
            
            WHERE ot.employee_id = :employeeId
            
            AND ot.status = 'PENDING'
            
            AND ot.attendance_date BETWEEN :from AND :to
            
            """, nativeQuery = true)
    PendingOvertimeProjection getPendingOvertimeSummary(

            @Param("employeeId") Long employeeId,

            @Param("from") LocalDate from,

            @Param("to") LocalDate to

    );


    // to get all the un-applied overtime exists in attendance records and not yet applied as overtime table.
    @Query(value = """
            
            SELECT
            
                COUNT(*)                                              AS attendanceDays,
            
                COALESCE(SUM(ar.ot_normal_minutes),0)                 AS normalMinutes,
            
                COALESCE(SUM(ar.ot_extra_minutes),0)                  AS extraMinutes,
            
                COALESCE(SUM(ar.ot_special_minutes),0)                AS specialMinutes
            
            FROM attendance.attendance_records ar
            
            WHERE ar.employee_id = :employeeId
            
            AND ar.attendance_date BETWEEN :from AND :to
            
            AND (
            
                    ar.ot_normal_minutes > 0
            
                 OR ar.ot_extra_minutes > 0
            
                 OR ar.ot_special_minutes > 0
            
            )
            
            AND NOT EXISTS (
            
                SELECT 1
            
                FROM attendance.overtime_requests ot
            
                WHERE ot.employee_id = ar.employee_id
            
                AND ot.attendance_date = ar.attendance_date
            
            )
            
            """, nativeQuery = true)
    UnappliedOvertimeProjection getUnappliedOvertimeSummary(

            @Param("employeeId") Long employeeId,

            @Param("from") LocalDate from,

            @Param("to") LocalDate to

    );

    // to get approved overtime summary for payroll process.
    @Query(value = """
            
            SELECT
            
                COUNT(DISTINCT ot.id)                       AS approvedRequests,
            
                COALESCE(SUM(ar.ot_normal_minutes),0)       AS normalMinutes,
            
                COALESCE(SUM(ar.ot_extra_minutes),0)        AS extraMinutes,
            
                COALESCE(SUM(ar.ot_special_minutes),0)      AS specialMinutes
            
            FROM attendance.overtime_requests ot
            
            JOIN attendance.attendance_records ar
              ON ar.employee_id = ot.employee_id
             AND ar.attendance_date = ot.attendance_date
            
            WHERE ot.employee_id = :employeeId
            
            AND ot.status = 'APPROVED'
            
            AND ot.attendance_date BETWEEN :from AND :to
            
            """, nativeQuery = true)
    ApprovedOvertimeProjection getApprovedOvertimeSummary(

            @Param("employeeId") Long employeeId,

            @Param("from") LocalDate from,

            @Param("to") LocalDate to

    );

    /**
     * Requests {@code createdBy} filed for someone other than
     * {@code excludeEmployeeId} (their own employee id) — i.e. on-behalf
     * submissions. Same shape as RegularizationRequestRepository.searchFiledOnBehalf.
     *
     * <p>cast(... as ...) hint is mandatory — see LeaveRequestRepository.search.
     */
    @Query("""
            select r from OvertimeRequest r
            where r.createdBy = :createdBy
              and r.employeeId <> :excludeEmployeeId
              and (cast(:status as string) is null or r.status = :status)
              and (cast(:filedFrom as LocalDateTime) is null or r.createdAt >= :filedFrom)
              and (cast(:filedTo as LocalDateTime) is null or r.createdAt < :filedTo)
            """)
    Page<OvertimeRequest> searchFiledOnBehalf(
            @Param("createdBy") String createdBy,
            @Param("excludeEmployeeId") Long excludeEmployeeId,
            @Param("status") OvertimeStatus status,
            @Param("filedFrom") java.time.LocalDateTime filedFrom,
            @Param("filedTo") java.time.LocalDateTime filedTo,
            Pageable pageable);

    /**
     * Requests for any of {@code employeeIds} — the caller's department —
     * whoever filed them (the employee, their manager, or a team-request
     * generator). Optional status and filed-on window ({@code createdAt},
     * half-open). Backs the "Generated team requests" department view.
     */
    @Query("""
            select r from OvertimeRequest r
            where r.employeeId in :employeeIds
              and (cast(:status as string) is null or r.status = :status)
              and (cast(:filedFrom as LocalDateTime) is null or r.createdAt >= :filedFrom)
              and (cast(:filedTo as LocalDateTime) is null or r.createdAt < :filedTo)
            """)
    Page<OvertimeRequest> searchDepartment(
            @Param("employeeIds") java.util.Collection<Long> employeeIds,
            @Param("status") OvertimeStatus status,
            @Param("filedFrom") java.time.LocalDateTime filedFrom,
            @Param("filedTo") java.time.LocalDateTime filedTo,
            Pageable pageable);
}

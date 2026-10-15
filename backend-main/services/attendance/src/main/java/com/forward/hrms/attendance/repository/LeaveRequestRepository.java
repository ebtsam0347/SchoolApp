package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.LeaveRequest;
import com.forward.hrms.attendance.enums.LeaveRequestStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    Page<LeaveRequest> findAllByEmployeeId(Long employeeId, Pageable pageable);

    Page<LeaveRequest> findAllByEmployeeIdAndStatus(Long employeeId, LeaveRequestStatus status, Pageable pageable);

    /**
     * Filtered list for {@code GET /leaves/me}. Each filter is null-safe — passing
     * a null parameter ignores the predicate. Date filter is overlap-style:
     * include any request whose [fromDate, toDate] intersects [from, to].
     *
     * <p>Each {@code :param is null} branch is wrapped in {@code cast(... as ...)}
     * so Postgres can determine the parameter type — the bare {@code ? is null}
     * test gives the JDBC driver no type context and the planner errors out
     * with "could not determine data type of parameter".
     */
    @Query("""
            select r from LeaveRequest r
            where r.employeeId = :employeeId
              and (cast(:status as string) is null or r.status = :status)
              and (cast(:leaveTypeId as long) is null or r.leaveTypeId = :leaveTypeId)
              and (cast(:from as date) is null or r.toDate >= :from)
              and (cast(:to as date) is null or r.fromDate <= :to)
            """)
    Page<LeaveRequest> search(
            @Param("employeeId") Long employeeId,
            @Param("status") LeaveRequestStatus status,
            @Param("leaveTypeId") Long leaveTypeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    /** {@link #search} across several employees — a manager's direct reports. */
    @Query("""
            select r from LeaveRequest r
            where r.employeeId in :employeeIds
              and (cast(:status as string) is null or r.status = :status)
              and (cast(:leaveTypeId as long) is null or r.leaveTypeId = :leaveTypeId)
              and (cast(:from as date) is null or r.toDate >= :from)
              and (cast(:to as date) is null or r.fromDate <= :to)
            """)
    Page<LeaveRequest> searchForEmployees(
            @Param("employeeIds") java.util.Collection<Long> employeeIds,
            @Param("status") LeaveRequestStatus status,
            @Param("leaveTypeId") Long leaveTypeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    /** Overlap probe — non-cancelled / non-rejected request that touches the range. */
    @Query("""
            select r from LeaveRequest r
            where r.employeeId = :employeeId
              and r.status in (com.forward.hrms.attendance.enums.LeaveRequestStatus.PENDING,
                               com.forward.hrms.attendance.enums.LeaveRequestStatus.APPROVED,
                               com.forward.hrms.attendance.enums.LeaveRequestStatus.PARTIALLY_APPROVED)
              and r.fromDate <= :to
              and r.toDate >= :from
            """)
    List<LeaveRequest> findActiveOverlapping(Long employeeId, LocalDate from, LocalDate to);

    /**
     * Requests {@code createdBy} filed for someone other than
     * {@code excludeEmployeeId} (their own employee id) — i.e. on-behalf
     * submissions. Same shape as RegularizationRequestRepository.searchFiledOnBehalf.
     *
     * <p>cast(... as ...) hint is mandatory — see LeaveRequestRepository.search.
     */
    @Query("""
            select r from LeaveRequest r
            where r.createdBy = :createdBy
              and r.employeeId <> :excludeEmployeeId
              and (cast(:status as string) is null or r.status = :status)
              and (cast(:filedFrom as LocalDateTime) is null or r.createdAt >= :filedFrom)
              and (cast(:filedTo as LocalDateTime) is null or r.createdAt < :filedTo)
            """)
    Page<LeaveRequest> searchFiledOnBehalf(
            @Param("createdBy") String createdBy,
            @Param("excludeEmployeeId") Long excludeEmployeeId,
            @Param("status") LeaveRequestStatus status,
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
            select r from LeaveRequest r
            where r.employeeId in :employeeIds
              and (cast(:status as string) is null or r.status = :status)
              and (cast(:filedFrom as LocalDateTime) is null or r.createdAt >= :filedFrom)
              and (cast(:filedTo as LocalDateTime) is null or r.createdAt < :filedTo)
            """)
    Page<LeaveRequest> searchDepartment(
            @Param("employeeIds") java.util.Collection<Long> employeeIds,
            @Param("status") LeaveRequestStatus status,
            @Param("filedFrom") java.time.LocalDateTime filedFrom,
            @Param("filedTo") java.time.LocalDateTime filedTo,
            Pageable pageable);
}

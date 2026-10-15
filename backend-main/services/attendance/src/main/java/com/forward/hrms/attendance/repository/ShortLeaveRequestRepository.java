package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.ShortLeaveRequest;
import com.forward.hrms.attendance.enums.ShortLeaveStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ShortLeaveRequestRepository extends JpaRepository<ShortLeaveRequest, Long> {

    /**
     * cast(... as ...) hints are mandatory — see LeaveRequestRepository.search.
     * Bare {@code :param is null} fails on Postgres with "could not determine
     * data type of parameter" because the JDBC driver has no type context.
     */
    @Query("""
            select s from ShortLeaveRequest s
            where s.employeeId = :employeeId
              and (cast(:status as string) is null or s.status = :status)
              and (cast(:from as date) is null or s.leaveDate >= :from)
              and (cast(:to as date) is null or s.leaveDate <= :to)
            """)
    Page<ShortLeaveRequest> search(
            @Param("employeeId") Long employeeId,
            @Param("status") ShortLeaveStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    /** {@link #search} across several employees — a manager's direct reports. */
    @Query("""
            select s from ShortLeaveRequest s
            where s.employeeId in :employeeIds
              and (cast(:status as string) is null or s.status = :status)
              and (cast(:from as date) is null or s.leaveDate >= :from)
              and (cast(:to as date) is null or s.leaveDate <= :to)
            """)
    Page<ShortLeaveRequest> searchForEmployees(
            @Param("employeeIds") Collection<Long> employeeIds,
            @Param("status") ShortLeaveStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    @Query("""
            select s from ShortLeaveRequest s
            where s.employeeId = :employeeId
              and s.leaveDate = :date
              and s.status in (com.forward.hrms.attendance.enums.ShortLeaveStatus.PENDING,
                               com.forward.hrms.attendance.enums.ShortLeaveStatus.APPROVED)
            """)
    List<ShortLeaveRequest> findActiveForDate(Long employeeId, LocalDate date);

    @Query("""
            select s from ShortLeaveRequest s
            where s.employeeId = :employeeId
              and s.status in (com.forward.hrms.attendance.enums.ShortLeaveStatus.PENDING,
                               com.forward.hrms.attendance.enums.ShortLeaveStatus.APPROVED)
              and s.leaveDate >= :onOrAfter
            """)
    List<ShortLeaveRequest> findActiveOnOrAfter(@Param("employeeId") Long employeeId,
                                                @Param("onOrAfter") LocalDate onOrAfter);

    /**
     * Requests {@code createdBy} filed for someone other than
     * {@code excludeEmployeeId} (their own employee id) — i.e. on-behalf
     * submissions. Same shape as RegularizationRequestRepository.searchFiledOnBehalf.
     *
     * <p>cast(... as ...) hint is mandatory — see LeaveRequestRepository.search.
     */
    @Query("""
            select r from ShortLeaveRequest r
            where r.createdBy = :createdBy
              and r.employeeId <> :excludeEmployeeId
              and (cast(:status as string) is null or r.status = :status)
              and (cast(:filedFrom as LocalDateTime) is null or r.createdAt >= :filedFrom)
              and (cast(:filedTo as LocalDateTime) is null or r.createdAt < :filedTo)
            """)
    Page<ShortLeaveRequest> searchFiledOnBehalf(
            @Param("createdBy") String createdBy,
            @Param("excludeEmployeeId") Long excludeEmployeeId,
            @Param("status") ShortLeaveStatus status,
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
            select r from ShortLeaveRequest r
            where r.employeeId in :employeeIds
              and (cast(:status as string) is null or r.status = :status)
              and (cast(:filedFrom as LocalDateTime) is null or r.createdAt >= :filedFrom)
              and (cast(:filedTo as LocalDateTime) is null or r.createdAt < :filedTo)
            """)
    Page<ShortLeaveRequest> searchDepartment(
            @Param("employeeIds") java.util.Collection<Long> employeeIds,
            @Param("status") ShortLeaveStatus status,
            @Param("filedFrom") java.time.LocalDateTime filedFrom,
            @Param("filedTo") java.time.LocalDateTime filedTo,
            Pageable pageable);
}

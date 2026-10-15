package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.RegularizationRequest;
import com.forward.hrms.attendance.enums.RegularizationStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RegularizationRequestRepository extends JpaRepository<RegularizationRequest, Long> {

    Page<RegularizationRequest> findAllByEmployeeId(Long employeeId, Pageable pageable);

    List<RegularizationRequest> findAllByEmployeeIdAndStatus(Long employeeId, RegularizationStatus status);

    /**
     * Requests {@code createdBy} filed for someone other than
     * {@code excludeEmployeeId} (their own employee id) — i.e. on-behalf
     * submissions. {@code created_by} is the submitter's username, stamped on
     * insert and never updated.
     *
     * <p>cast(... as ...) hints are mandatory — see LeaveRequestRepository.search.
     */
    @Query("""
            select r from RegularizationRequest r
            where r.createdBy = :createdBy
              and r.employeeId <> :excludeEmployeeId
              and (cast(:status as string) is null or r.status = :status)
              and (cast(:from as date) is null or r.attendanceDate >= :from)
              and (cast(:to as date) is null or r.attendanceDate <= :to)
              and (cast(:filedFrom as LocalDateTime) is null or r.createdAt >= :filedFrom)
              and (cast(:filedTo as LocalDateTime) is null or r.createdAt < :filedTo)
            """)
    Page<RegularizationRequest> searchFiledOnBehalf(
            @Param("createdBy") String createdBy,
            @Param("excludeEmployeeId") Long excludeEmployeeId,
            @Param("status") RegularizationStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
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
            select r from RegularizationRequest r
            where r.employeeId in :employeeIds
              and (cast(:status as string) is null or r.status = :status)
              and (cast(:filedFrom as LocalDateTime) is null or r.createdAt >= :filedFrom)
              and (cast(:filedTo as LocalDateTime) is null or r.createdAt < :filedTo)
            """)
    Page<RegularizationRequest> searchDepartment(
            @Param("employeeIds") java.util.Collection<Long> employeeIds,
            @Param("status") RegularizationStatus status,
            @Param("filedFrom") java.time.LocalDateTime filedFrom,
            @Param("filedTo") java.time.LocalDateTime filedTo,
            Pageable pageable);
}

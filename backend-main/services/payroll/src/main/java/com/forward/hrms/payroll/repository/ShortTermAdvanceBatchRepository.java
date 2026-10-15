package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.entity.ShortTermAdvanceBatch;
import com.forward.hrms.payroll.enums.AdvanceStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShortTermAdvanceBatchRepository extends JpaRepository<ShortTermAdvanceBatch, Long> {

    List<ShortTermAdvanceBatch> findByStatusInOrderByIdDesc(List<AdvanceStatus> statuses);

    @Query("""
            select b from ShortTermAdvanceBatch b
            where (cast(:status as string) is null or b.status = :status)
              and (cast(:targetYear as integer) is null or b.targetYear = :targetYear)
              and (cast(:targetMonth as integer) is null or b.targetMonth = :targetMonth)
              and (cast(:departmentId as long) is null or b.departmentId = :departmentId)
            """)
    Page<ShortTermAdvanceBatch> search(
            @Param("status") AdvanceStatus status,
            @Param("targetYear") Integer targetYear,
            @Param("targetMonth") Integer targetMonth,
            @Param("departmentId") Long departmentId,
            Pageable pageable);

    @Query("""
            select distinct b from ShortTermAdvanceBatch b
            join ShortTermAdvance s on s.batchId = b.id
            where s.employeeId = :employeeId
              and (cast(:status as string) is null or b.status = :status)
            """)
    Page<ShortTermAdvanceBatch> searchForEmployee(
            @Param("employeeId") Long employeeId,
            @Param("status") AdvanceStatus status,
            Pageable pageable);

    /**
     * Batches {@code createdBy} raised. A batch is inherently a multi-person
     * filing, so unlike the per-employee request types there is no
     * self-exclusion here.
     */
    @Query("""
            select b from ShortTermAdvanceBatch b
            where b.createdBy = :createdBy
              and (cast(:status as string) is null or b.status = :status)
              and (cast(:filedFrom as LocalDateTime) is null or b.createdAt >= :filedFrom)
              and (cast(:filedTo as LocalDateTime) is null or b.createdAt < :filedTo)
            """)
    Page<ShortTermAdvanceBatch> searchCreatedBy(
            @Param("createdBy") String createdBy,
            @Param("status") AdvanceStatus status,
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
            select b from ShortTermAdvanceBatch b
            where exists (select 1 from ShortTermAdvance l
                          where l.batchId = b.id and l.employeeId in :employeeIds)
              and (cast(:status as string) is null or b.status = :status)
              and (cast(:filedFrom as LocalDateTime) is null or b.createdAt >= :filedFrom)
              and (cast(:filedTo as LocalDateTime) is null or b.createdAt < :filedTo)
            """)
    Page<ShortTermAdvanceBatch> searchDepartment(
            @Param("employeeIds") java.util.Collection<Long> employeeIds,
            @Param("status") AdvanceStatus status,
            @Param("filedFrom") java.time.LocalDateTime filedFrom,
            @Param("filedTo") java.time.LocalDateTime filedTo,
            Pageable pageable);
}

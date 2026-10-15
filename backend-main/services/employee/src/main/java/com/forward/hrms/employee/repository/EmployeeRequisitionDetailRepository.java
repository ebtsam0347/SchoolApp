package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.EmployeeRequisitionDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface EmployeeRequisitionDetailRepository extends JpaRepository<EmployeeRequisitionDetail, Long> {
    List<EmployeeRequisitionDetail> findAllByRequisitionHeadId(Long requisitionHeadId);
    List<EmployeeRequisitionDetail> findAllByRequisitionHeadIdOrderByIdAsc(Long requisitionHeadId);
    List<EmployeeRequisitionDetail> findAllByRequisitionHeadIdIn(Collection<Long> requisitionHeadIds);

    /** The requisition line this employee was hired against (appointment letter's Request ID). */
    java.util.Optional<EmployeeRequisitionDetail> findFirstByNewEmployeeCodeOrderByIdDesc(String newEmployeeCode);

    @Query("SELECT e FROM EmployeeRequisitionDetail e WHERE e.replacingEmployeeId = :employeeId")
    EmployeeRequisitionDetail findByReplacementEmployeeId(Long employeeId);

    @Query("SELECT e FROM EmployeeRequisitionDetail e " +
            "WHERE e.requisitionHeadId = :requisitionHeadId AND e.isLeanRemoved = :leanRemoved " +
            "ORDER BY e.id ASC")
    List<EmployeeRequisitionDetail> findAllByRequisitionHeadIdAndLeanRemoved(Long requisitionHeadId,
                                                                            boolean leanRemoved);

    /** Positions still standing after lean review — the value of {@code total_positions_lean}. */
    @Query("SELECT count(e) FROM EmployeeRequisitionDetail e " +
            "WHERE e.requisitionHeadId = :requisitionHeadId AND e.isLeanRemoved = false")
    long countRemainingAfterLean(Long requisitionHeadId);

    /** Positions filled with a real employee code. */
    @Query("SELECT count(e) FROM EmployeeRequisitionDetail e " +
            "WHERE e.requisitionHeadId = :requisitionHeadId " +
            "AND e.newEmployeeCode IS NOT NULL AND e.newEmployeeCode <> ''")
    long countWithNewEmployeeCode(Long requisitionHeadId);

    /**
     * Positions still carrying only a temporary reference — the code hasn't been
     * written over it yet, so this and {@link #countWithNewEmployeeCode} don't overlap.
     */
    @Query("SELECT count(e) FROM EmployeeRequisitionDetail e " +
            "WHERE e.requisitionHeadId = :requisitionHeadId " +
            "AND e.temporaryEmployeeReference IS NOT NULL AND e.temporaryEmployeeReference <> '' " +
            "AND (e.newEmployeeCode IS NULL OR e.newEmployeeCode = '')")
    long countWithTemporaryEmployeeReferenceOnly(Long requisitionHeadId);
}

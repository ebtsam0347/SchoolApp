package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.LeaveEncashment;
import com.forward.hrms.payroll.enums.LeaveEncashmentStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface LeaveEncashmentRepository extends JpaRepository<LeaveEncashment, Long> {

    boolean existsByEmployeeIdAndCalculationMonth(Long employeeId, LocalDate calculationMonth);

    @Query("""
            select e from LeaveEncashment e
            where (cast(:employeeId as long) is null or e.employeeId = :employeeId)
              and (cast(:status as string) is null or e.paymentStatus = :status)
            """)
    Page<LeaveEncashment> search(
            @Param("employeeId") Long employeeId,
            @Param("status") LeaveEncashmentStatus status,
            Pageable pageable);

    /**
     * Rows created by a Final Settlement's closing step — used to undo a
     * rejected/canceled settlement (see {@code final_settlement_id} on
     * the entity).
     */
    void deleteAllByFinalSettlementId(Long finalSettlementId);

    /**
     * Idempotency guard for {@code LeaveSettlementService.process()} —
     * scoped to this exact settlement, not employee+month (the
     * employee+month rule in {@code LeaveEncashmentService} governs
     * manually-created encashments and is a separate concern; a
     * settlement recreated after a proper reject/cancel reversal must
     * still be able to create its own fresh encashment for the same
     * employee/period).
     */
    boolean existsByFinalSettlementId(Long finalSettlementId);
}

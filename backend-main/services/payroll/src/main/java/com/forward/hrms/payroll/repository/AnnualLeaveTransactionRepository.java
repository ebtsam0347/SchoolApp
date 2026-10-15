package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.AnnualLeaveTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface AnnualLeaveTransactionRepository extends JpaRepository<AnnualLeaveTransaction, Long>, JpaSpecificationExecutor<AnnualLeaveTransaction> {

    boolean existsByEmployeeIdAndCalculationMonth(Long employeeId, LocalDate calculationMonth);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnnualLeaveTransaction t
            set t.paymentStatus = :paymentStatus
            where t.id = :id
              and (t.lockStatus = false or t.lockStatus is null)
            """)
    int updatePaymentStatusById(@Param("id") Long id, @Param("paymentStatus") Boolean paymentStatus);

    long countByCalculationMonth(LocalDate calculationMonth);

    long countByCalculationMonthAndPaymentStatusIsNull(LocalDate calculationMonth);

    long countByCalculationMonthAndLockStatusTrue(LocalDate calculationMonth);

    Optional<AnnualLeaveTransaction> findByEmployeeIdAndCalculationMonth(Long employeeId, LocalDate calculationMonth);

    /**
     * Locks every still-open row of the month. Rows with no payment decision
     * are skipped as a guard — the service rejects the lock before reaching
     * here when any exist.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnnualLeaveTransaction t
            set t.lockStatus = true
            where t.calculationMonth = :calculationMonth
              and (t.lockStatus = false or t.lockStatus is null)
              and t.paymentStatus is not null
            """)
    int lockMonth(@Param("calculationMonth") LocalDate calculationMonth);
}

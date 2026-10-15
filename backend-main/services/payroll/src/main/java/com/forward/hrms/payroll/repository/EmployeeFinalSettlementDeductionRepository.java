package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.EmployeeFinalSettlementDeduction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeFinalSettlementDeductionRepository extends JpaRepository<EmployeeFinalSettlementDeduction,Long> {

    List<EmployeeFinalSettlementDeduction> findBySettlementId(Long settlementId);
}

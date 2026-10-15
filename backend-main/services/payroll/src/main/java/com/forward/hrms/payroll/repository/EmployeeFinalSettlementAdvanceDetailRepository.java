package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.EmployeeFinalSettlementAdvanceDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeFinalSettlementAdvanceDetailRepository extends JpaRepository<EmployeeFinalSettlementAdvanceDetail, Long> {

    List<EmployeeFinalSettlementAdvanceDetail> findBySettlementId(Long settlementId);

}

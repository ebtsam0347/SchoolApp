package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.EmployeeFinalSettlementLeaveDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeFinalSettlementLeaveDetailRepository extends JpaRepository<EmployeeFinalSettlementLeaveDetail, Long> {

    List<EmployeeFinalSettlementLeaveDetail> findBySettlementId(Long settlementId);
}

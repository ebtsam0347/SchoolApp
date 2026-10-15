package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.EmployeeFinalSettlementBonusDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeFinalSettlementBonusDetailRepository extends JpaRepository<EmployeeFinalSettlementBonusDetail, Long> {

    List<EmployeeFinalSettlementBonusDetail> findBySettlementId(Long settlementId);
}

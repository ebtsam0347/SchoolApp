package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.EmployeeFinalSettlementOvertime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeFinalSettlementOvertimeRepository extends JpaRepository<EmployeeFinalSettlementOvertime, Long> {

    List<EmployeeFinalSettlementOvertime> findBySettlementId(Long settlementId);

}

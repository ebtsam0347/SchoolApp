package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.EmployeeFinalSettlementAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeFinalSettlementAttendanceRepository extends JpaRepository<EmployeeFinalSettlementAttendance, Long> {

    List<EmployeeFinalSettlementAttendance> findBySettlementId(Long settlementId);

}

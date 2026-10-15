package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.enums.EmployeeFinalSettlementStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeFinalSettlementRepository extends JpaRepository<EmployeeFinalSettlement, Long>,
        JpaSpecificationExecutor<EmployeeFinalSettlement> {

    Optional<EmployeeFinalSettlement> findByEmployeeIdAndStatus(Long employeeId, EmployeeFinalSettlementStatus status);

    List<EmployeeFinalSettlement> findByStatus(EmployeeFinalSettlementStatus status);

    List<EmployeeFinalSettlement> findByEmployeeId(Long employeeId);

    List<EmployeeFinalSettlement> findByStatusIn(List<EmployeeFinalSettlementStatus> statuses);

    List<EmployeeFinalSettlement> findByEmployeeIdAndStatusIn(Long employeeId, List<EmployeeFinalSettlementStatus> statuses);

    List<EmployeeFinalSettlement> findByEmployeeIdAndStatusNotIn(Long employeeId, List<EmployeeFinalSettlementStatus> statuses);

    boolean existsByEmployeeIdAndStatusIn(
            Long employeeId,
            Collection<EmployeeFinalSettlementStatus> statuses
    );
}

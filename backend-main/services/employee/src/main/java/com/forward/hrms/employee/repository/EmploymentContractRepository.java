package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.EmploymentContract;
import com.forward.hrms.employee.enums.ContractStatus;
import com.forward.hrms.employee.enums.ContractType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, Long> {

    List<EmploymentContract> findAllByEmployeeIdOrderByStartDateDesc(Long employeeId);

    Optional<EmploymentContract> findFirstByEmployeeIdAndContractTypeAndStatus(
            Long employeeId, ContractType contractType, ContractStatus status);

    /**
     * Active probation rows for the supplied employee ids — used by the list
     * assembler to fill {@code EmployeeDto.activeProbation} on probation queries
     * without an N+1 cascade.
     */
    @Query("""
            select c from EmploymentContract c
            where c.employeeId in :ids
              and c.contractType = com.forward.hrms.employee.enums.ContractType.PROBATION
              and c.status = com.forward.hrms.employee.enums.ContractStatus.ACTIVE
            """)
    List<EmploymentContract> findActiveProbationsByEmployeeIds(Collection<Long> ids);

    List<EmploymentContract> findAllByEmployeeIdAndStatus(
            Long employeeId,
            ContractStatus status
    );

    /**
     * Contracts closed by TerminationSaga at a specific last-working-day —
     * used by undoTermination to find exactly the rows to reopen.
     */
    List<EmploymentContract> findAllByEmployeeIdAndStatusAndEndDate(
            Long employeeId,
            ContractStatus status,
            java.time.LocalDate endDate
    );
}

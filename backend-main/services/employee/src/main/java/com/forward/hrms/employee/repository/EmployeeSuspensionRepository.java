package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.EmployeeSuspension;
import com.forward.hrms.employee.enums.SuspensionStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeSuspensionRepository extends JpaRepository<EmployeeSuspension, Long> {

    Optional<EmployeeSuspension> findFirstByEmployeeIdAndStatus(Long employeeId, SuspensionStatus status);

    boolean existsByEmployeeIdAndStatus(Long employeeId, SuspensionStatus status);

    List<EmployeeSuspension> findAllByEmployeeIdOrderByStartDateDesc(Long employeeId);

    /**
     * Suspensions auto-lifted by TerminationSaga at a specific date —
     * used by undoTermination to find exactly the rows to restore.
     */
    List<EmployeeSuspension> findAllByEmployeeIdAndStatusAndLiftedOn(
            Long employeeId, SuspensionStatus status, java.time.LocalDate liftedOn);
}

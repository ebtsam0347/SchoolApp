package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.EmployeeHistory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeHistoryRepository extends JpaRepository<EmployeeHistory, Long> {

    Optional<EmployeeHistory> findFirstByEmployeeIdAndEffectiveToIsNull(Long employeeId);

    Page<EmployeeHistory> findAllByEmployeeIdOrderByEffectiveFromDesc(Long employeeId, Pageable pageable);

    /**
     * The row closed by TerminationSaga on exit — used by
     * {@code reopenOnUndo} to find exactly the row to reopen.
     */
    Optional<EmployeeHistory> findFirstByEmployeeIdAndEffectiveTo(Long employeeId, java.time.LocalDate effectiveTo);
}

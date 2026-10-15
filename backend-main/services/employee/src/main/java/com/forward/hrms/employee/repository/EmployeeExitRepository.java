package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.EmployeeExit;
import com.forward.hrms.employee.enums.ExitStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeExitRepository extends JpaRepository<EmployeeExit, Long> {

    /**
     * The employee's current/most recent exit attempt. employee_exits is
     * historical (V0621) — an employee can have more than one row over
     * their lifetime, so a bare findByEmployeeId is no longer safe (it
     * would throw once a second row exists). Every call site that wants
     * "the exit record for this employee" (as opposed to a specific
     * settlement's exit, which must go through findBySettlementId)
     * means the latest one.
     */
    Optional<EmployeeExit> findFirstByEmployeeIdOrderByIdDesc(Long employeeId);

    boolean existsByEmployeeIdAndStatus(Long employeeId, ExitStatus status);

    List<EmployeeExit> findAllByStatusAndLastWorkingDayLessThanEqual(
            ExitStatus status, LocalDate lastWorkingDay);

    /**
     * Leavers per (year, month) over a window — keyed on {@code lastWorkingDay}
     * for COMPLETED exits. Result rows are {@code [year: int, month: int, count: long]}.
     */
    @Query("""
            select extract(year from x.lastWorkingDay),
                   extract(month from x.lastWorkingDay),
                   count(x)
            from EmployeeExit x
            where x.status = com.forward.hrms.employee.enums.ExitStatus.COMPLETED
              and x.lastWorkingDay >= :from
              and x.lastWorkingDay <= :to
            group by extract(year from x.lastWorkingDay),
                     extract(month from x.lastWorkingDay)
            """)
    List<Object[]> leaverCountsByMonth(LocalDate from, LocalDate to);

    Optional<EmployeeExit> findBySettlementId(Long settlementId);

    boolean existsBySettlementId(Long settlementId);

    Optional<EmployeeExit> findByEmployeeIdAndStatus(
            Long employeeId,
            ExitStatus status
    );

}

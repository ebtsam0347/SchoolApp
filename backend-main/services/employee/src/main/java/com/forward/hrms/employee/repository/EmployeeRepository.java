package com.forward.hrms.employee.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.forward.hrms.employee.dto.response.AnnualLeaveEligibleEmployee;
import com.forward.hrms.employee.dto.response.EmployeeStatusDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.forward.hrms.employee.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByUserId(Long userId);

    List<Employee> findAllByUserIdIn(Collection<Long> userIds);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    boolean existsByUserId(Long userId);

    /**
     * Employees whose ACTIVE PROBATION contract ends on or before {@code maxEndDate}
     * (and on or after {@code minEndDate} when supplied — typically {@code today} for
     * upcoming-only, or null for "include overdue").
     */
    @Query("""
            select e from Employee e
            where exists (
              select 1 from EmploymentContract c
              where c.employeeId = e.id
                and c.contractType = com.forward.hrms.employee.enums.ContractType.PROBATION
                and c.status = com.forward.hrms.employee.enums.ContractStatus.ACTIVE
                and c.endDate <= :maxEndDate
                and (:minEndDate is null or c.endDate >= :minEndDate)
            )
            """)
    Page<Employee> findByProbationDue(LocalDate minEndDate, LocalDate maxEndDate, Pageable pageable);

    List<Employee> findAllByManagerId(Long managerId);

    /**
     * True when {@code managerId} line-manages at least one non-TERMINATED
     * employee. Used to decide whether an employee is a "Manager/HOD" for
     * travel-request creation. Excludes TERMINATED reports — same convention
     * as {@link #countDirectReportsByManagerIds} and {@code EMPLOYEE_HAS_ACTIVE_REPORTS}.
     */
    @Query("""
            select (count(e) > 0) from Employee e
            where e.managerId = :managerId
              and e.employmentStatus <> com.forward.hrms.employee.enums.EmploymentStatus.TERMINATED
            """)
    boolean hasActiveDirectReports(Long managerId);

    @Query("""
            select e from Employee e
            where e.managerId = :managerId
              and e.employmentStatus <> com.forward.hrms.employee.enums.EmploymentStatus.TERMINATED
              and (cast(:q as string) is null
                   or lower(e.fullName) like lower(concat('%', cast(:q as string), '%'))
                   or lower(e.employeeCode) like lower(concat('%', cast(:q as string), '%'))
                   or lower(coalesce(e.workEmail, '')) like lower(concat('%', cast(:q as string), '%'))
                   or coalesce(e.phone, '') like concat('%', cast(:q as string), '%')
                   or coalesce(e.cnic, '') like concat('%', cast(:q as string), '%')
                   or function('similarity', lower(e.fullName), lower(cast(:q as string))) > 0.3
                   or function('similarity', lower(e.employeeCode), lower(cast(:q as string))) > 0.3)
            """)
    Page<Employee> findByManagerId(Long managerId, String q, Pageable pageable);

    List<Employee> findAllByIdIn(Collection<Long> ids);

    /**
     * Batched {@code (employeeId, userId)} lookup. Used by the assembler to
     * resolve {@code managerUserId} for read paths — attendance keys
     * {@code approval_steps.assigned_to} on user_id, so the manager DTO
     * needs the user_id of the manager row, not just its employee id.
     * Rows with no linked auth user are absent from the result.
     */
    @Query("""
            select e.id, e.userId from Employee e
            where e.id in :ids
              and e.userId is not null
            """)
    List<Object[]> findUserIdsByEmployeeIds(Collection<Long> ids);

    /**
     * Direct-reports count, batched by manager id. Used by list endpoints to populate
     * {@code EmployeeDto.directReportsCount} without an N+1 cascade. Excludes
     * TERMINATED reports — they're not part of "currently manages N".
     * Result rows are {@code [managerId: Long, count: Long]}.
     */
    @Query("""
            select e.managerId, count(e) from Employee e
            where e.managerId in :ids
              and e.employmentStatus <> com.forward.hrms.employee.enums.EmploymentStatus.TERMINATED
            group by e.managerId
            """)
    List<Object[]> countDirectReportsByManagerIds(Collection<Long> ids);

    /**
     * One-shot grouped headcount used by the overview/aggregate endpoints.
     * Result rows are {@code [groupKey: ?, count: Long]} — caller decides the
     * key column via the supplied JPQL-friendly path.
     */
    @Query("""
            select e.employmentStatus, count(e) from Employee e group by e.employmentStatus
            """)
    List<Object[]> countByEmploymentStatus();

    @Query("""
            select e.employmentType, count(e) from Employee e
            where e.employmentStatus <> com.forward.hrms.employee.enums.EmploymentStatus.TERMINATED
            group by e.employmentType
            """)
    List<Object[]> countByEmploymentType();

    @Query("""
            select e.departmentId, count(e) from Employee e
            where e.employmentStatus <> com.forward.hrms.employee.enums.EmploymentStatus.TERMINATED
            group by e.departmentId
            """)
    List<Object[]> countByDepartment();

    @Query("""
            select e.sectionId, count(e) from Employee e
            where e.employmentStatus <> com.forward.hrms.employee.enums.EmploymentStatus.TERMINATED
            group by e.sectionId
            """)
    List<Object[]> countBySection();

    @Query("""
            select e.gradeId, count(e) from Employee e
            where e.employmentStatus <> com.forward.hrms.employee.enums.EmploymentStatus.TERMINATED
            group by e.gradeId
            """)
    List<Object[]> countByGrade();

    @Query("""
            select e.designationId, count(e) from Employee e
            where e.employmentStatus <> com.forward.hrms.employee.enums.EmploymentStatus.TERMINATED
            group by e.designationId
            """)
    List<Object[]> countByDesignation();

    @Query("""
            select coalesce(lower(e.gender), 'unspecified'), count(e) from Employee e
            where e.employmentStatus <> com.forward.hrms.employee.enums.EmploymentStatus.TERMINATED
            group by lower(e.gender)
            """)
    List<Object[]> countByGender();

    @Query("""
            select count(e) from Employee e
            where e.userId is not null
              and e.employmentStatus <> com.forward.hrms.employee.enums.EmploymentStatus.TERMINATED
            """)
    long countActiveWithEss();

    @Query("""
            select count(e) from Employee e
            where e.employmentStatus <> com.forward.hrms.employee.enums.EmploymentStatus.TERMINATED
            """)
    long countNonTerminated();

    /**
     * Joiners per (year, month) over a window. Result rows are
     * {@code [year: int, month: int, count: long]}. Reads
     * {@code joining_date} only — TERMINATED rows are still included
     * because the join itself happened in that month.
     */
    @Query("""
            select extract(year from e.joiningDate),
                   extract(month from e.joiningDate),
                   count(e)
            from Employee e
            where e.joiningDate >= :from
              and e.joiningDate <= :to
            group by extract(year from e.joiningDate),
                     extract(month from e.joiningDate)
            """)
    List<Object[]> joinerCountsByMonth(LocalDate from, LocalDate to);

    /** Headcount as of a given date — i.e. joined on/before {@code asOf} and not terminated by then. */
    @Query("""
            select count(e) from Employee e
            where e.joiningDate <= :asOf
              and not exists (
                  select 1 from EmployeeExit x
                  where x.employeeId = e.id
                    and x.status = com.forward.hrms.employee.enums.ExitStatus.COMPLETED
                    and x.lastWorkingDay <= :asOf
              )
            """)
    long headcountAsOf(LocalDate asOf);

    @Query(value = """
            with recursive chain(employee_id, manager_id, depth) as (
                select e.id, e.manager_id, 1
                  from employee.employees e
                 where e.id = :candidateManagerId
                union all
                select e.id, e.manager_id, c.depth + 1
                  from employee.employees e
                  join chain c on e.id = c.manager_id
                 where c.depth < 64
            )
            select exists (select 1 from chain where employee_id = :employeeId)
            """, nativeQuery = true)
    boolean managerChainContains(Long employeeId, Long candidateManagerId);

    /**
     * serialize concurrent manager-chain mutations on
     * a single transactional advisory lock. Every {@code changeManager}
     * grabs the lock before cycle-checking + writing, so two concurrent
     * calls can't both pass the cycle check and then close the loop on
     * commit. Released automatically at tx commit or rollback. Lock id
     * is an arbitrary stable hash; the pair of {@code (key1, key2)} just
     * needs to differ from any other system-wide advisory lock id we
     * use.
     */
    @Query(value = "select pg_advisory_xact_lock(7011, 27)", nativeQuery = true)
    void lockManagerChain();

    @Query(value = """
            SELECT id, employee_code, joining_date, employment_status  FROM employee.employees
            WHERE
                EXTRACT(MONTH FROM joining_date) = :month
            AND EXTRACT(YEAR FROM joining_date) < :year
            AND employment_status IN('ACTIVE', 'RESIGNED')
            """, nativeQuery = true)
    List<AnnualLeaveEligibleEmployee> getAnnualLeaveEligibleEmployeeList(int year, int month);

    @Query("""
            select new com.forward.hrms.employee.dto.response.EmployeeStatusDto(e.id, e.employmentStatus)
            from Employee e
            where e.id in :ids
            """)
    List<EmployeeStatusDto> findStatusesByIdIn(Collection<Long> ids);
}

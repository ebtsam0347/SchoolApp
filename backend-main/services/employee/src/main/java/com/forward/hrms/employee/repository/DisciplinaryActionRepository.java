package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.DisciplinaryAction;
import com.forward.hrms.employee.enums.DisciplinaryCategory;
import com.forward.hrms.employee.enums.DisciplinaryLevel;
import com.forward.hrms.employee.enums.DisciplinaryStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface DisciplinaryActionRepository extends JpaRepository<DisciplinaryAction, Long> {

    @Query("""
            select d from DisciplinaryAction d
            where d.employeeId = :employeeId
              and (:level is null or d.level = :level)
              and (:status is null or d.status = :status)
              and (:from is null or d.issuedOn >= :from)
              and (:to is null or d.issuedOn <= :to)
            order by d.issuedOn desc
            """)
    List<DisciplinaryAction> listForEmployee(Long employeeId,
                                             DisciplinaryLevel level,
                                             DisciplinaryStatus status,
                                             LocalDate from,
                                             LocalDate to);

    /**
     * HR queue for global disciplinary review. {@code employeeId} narrows to a
     * single employee; {@code q} matches the owning employee's full name or
     * employee code (case-insensitive substring).
     */
    @Query("""
            select d from DisciplinaryAction d
            where (:level is null or d.level = :level)
              and (:category is null or d.category = :category)
              and (:status is null or d.status = :status)
              and (:from is null or d.issuedOn >= :from)
              and (:to is null or d.issuedOn <= :to)
              and (:employeeId is null or d.employeeId = :employeeId)
              and (cast(:q as string) is null or exists (
                    select 1 from Employee e where e.id = d.employeeId
                      and (lower(e.fullName) like lower(concat('%', cast(:q as string), '%'))
                           or lower(e.employeeCode) like lower(concat('%', cast(:q as string), '%')))))
            """)
    Page<DisciplinaryAction> search(DisciplinaryLevel level,
                                    DisciplinaryCategory category,
                                    DisciplinaryStatus status,
                                    LocalDate from,
                                    LocalDate to,
                                    Long employeeId,
                                    String q,
                                    Pageable pageable);

    /**
     * UPHELD-only count per level for an employee within a fiscal-year
     * window. Drives the warning-count rollup on the per-employee list
     * endpoint. Result rows are {@code [DisciplinaryLevel, Long]}.
     */
    @Query("""
            select d.level, count(d) from DisciplinaryAction d
            where d.employeeId = :employeeId
              and d.decision = com.forward.hrms.employee.enums.DisciplinaryDecision.UPHELD
              and d.issuedOn >= :fyStart
              and d.issuedOn <= :fyEnd
            group by d.level
            """)
    List<Object[]> countUpheldByLevel(Long employeeId, LocalDate fyStart, LocalDate fyEnd);
}

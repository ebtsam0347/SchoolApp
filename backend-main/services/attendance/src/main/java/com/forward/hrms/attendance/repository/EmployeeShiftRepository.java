package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.EmployeeShift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeShiftRepository extends JpaRepository<EmployeeShift, Long> {

    /**
     * Most-recent assignment whose effective range covers {@code on}.
     */
    @Query("""
            select es from EmployeeShift es
            where es.employeeId = :employeeId
              and es.effectiveFrom <= :on
              and (es.effectiveTo is null or es.effectiveTo >= :on)
            order by es.effectiveFrom desc
            """)
    List<EmployeeShift> findActiveOn(Long employeeId, LocalDate on);

    /**
     * New method to produce good result about employee active shift
     */
    @Query("""
            select es
            from EmployeeShift es
            where es.employeeId = :employeeId
              and es.effectiveFrom <= :on
              and (es.effectiveTo is null or es.effectiveTo >= :on)
            order by es.effectiveFrom desc
            """)
    Optional<EmployeeShift> findActiveShiftByEmployee(
            @Param("employeeId") Long employeeId,
            @Param("on") LocalDate on);

    default Optional<EmployeeShift> findFirstActiveOn(Long employeeId, LocalDate on) {
        return findActiveOn(employeeId, on).stream().findFirst();
    }

    List<EmployeeShift> findAllByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);

    /**
     * Rows closed exactly on the given date — used to find the
     * assignment {@code closeEmployeeShift} closed so termination-undo
     * can reopen it.
     */
    List<EmployeeShift> findByEmployeeIdAndEffectiveTo(Long employeeId, LocalDate effectiveTo);

    /**
     * We need to get all employees assigned to a shift on a given date, not just one employee.
     */
    @Query("""
            select es
            from EmployeeShift es
            where es.shiftId = :shiftId
              and es.effectiveFrom <= :date
              and (es.effectiveTo is null or es.effectiveTo >= :date)
            """)
    List<EmployeeShift> findActiveEmployeesByShift(Long shiftId, LocalDate date);

    @Query("""
            select es
            from EmployeeShift es
            where es.shiftId = :shiftId
              and es.effectiveFrom <= :date
              and (es.effectiveTo is null or es.effectiveTo >= :date)
            """)
    List<EmployeeShift> findEmployeesByShiftOn(
            @Param("shiftId") Long shiftId,
            @Param("date") LocalDate date
    );

    @Query("""
            select es
            from EmployeeShift es
            where es.effectiveFrom <= :date
              and (es.effectiveTo is null or es.effectiveTo >= :date)
            """)
    List<EmployeeShift> findAllActiveOn(@Param("date") LocalDate date);


}

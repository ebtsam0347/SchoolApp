package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.FiscalYear;
import com.forward.hrms.payroll.enums.FiscalYearStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FiscalYearRepository extends JpaRepository<FiscalYear, String> {

    Optional<FiscalYear> findByStatus(FiscalYearStatus status);

    @org.springframework.data.jpa.repository.Query("""
            select fy from FiscalYear fy
            where fy.startDate <= :date and fy.endDate >= :date
            """)
    Optional<FiscalYear> findCovering(LocalDate date);

    List<FiscalYear> findAllByOrderByStartDateDesc();

    FiscalYear findFiscalYearByCode(String code);
}

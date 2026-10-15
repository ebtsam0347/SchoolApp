package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.AllowanceRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AllowanceRunRepository extends JpaRepository<AllowanceRun,Long> {

        Optional<AllowanceRun> findByAllowanceYearAndAllowanceMonth(int year, int month);

        boolean existsByAllowanceYearAndAllowanceMonth(int year, int month);

        List<AllowanceRun> findAllByFiscalYearCodeOrderByAllowanceYearDescAllowanceMonthDesc(String fiscalYearCode);

}

package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.PayrollRun;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {

    Optional<PayrollRun> findByPeriodYearAndPeriodMonth(int year, int month);

    boolean existsByPeriodYearAndPeriodMonth(int year, int month);

    List<PayrollRun> findAllByFiscalYearCodeOrderByPeriodYearDescPeriodMonthDesc(String fiscalYearCode);
}

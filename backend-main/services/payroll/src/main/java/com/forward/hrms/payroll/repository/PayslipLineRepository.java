package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.PayslipLine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayslipLineRepository extends JpaRepository<PayslipLine, Long> {

    List<PayslipLine> findAllByPayslipIdOrderByDisplayOrder(long payslipId);

    /**
     * Bulk-load lines for a set of payslips (one query for the whole pay
     * register). Caller groups by {@code payslipId}.
     */
    List<PayslipLine> findAllByPayslipIdInOrderByPayslipIdAscDisplayOrderAsc(java.util.Collection<Long> payslipIds);
}

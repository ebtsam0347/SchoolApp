package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.BonusEvent;
import com.forward.hrms.payroll.enums.BonusEventStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BonusEventRepository extends JpaRepository<BonusEvent, Long> {

    @Query("""
            select e from BonusEvent e
            where (:fy     is null or e.fiscalYearCode = :fy)
              and (:status is null or e.status = :status)
            order by e.referenceDate desc
            """)
    List<BonusEvent> filter(String fy, BonusEventStatus status);

}

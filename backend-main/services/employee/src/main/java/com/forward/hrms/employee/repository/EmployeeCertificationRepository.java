package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.EmployeeCertification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface EmployeeCertificationRepository extends JpaRepository<EmployeeCertification, Long> {

    List<EmployeeCertification> findAllByEmployeeIdOrderByIssueDateDescIdAsc(Long employeeId);

    /**
     * HR expiring queue. {@code maxExpiry} caps the upper bound (today + N days
     * by default). {@code minExpiry} is null for "include already-expired",
     * or {@code today + 1} when the caller wants only future-dated rows.
     * {@code departmentId} / {@code sectionId} narrow the queue to a specific
     * org slice via the cert's owning employee.
     */
    @Query("""
            select c from EmployeeCertification c
            where c.expiryDate is not null
              and c.expiryDate <= :maxExpiry
              and (:minExpiry is null or c.expiryDate >= :minExpiry)
              and (:departmentId is null or exists (
                    select 1 from Employee e where e.id = c.employeeId and e.departmentId = :departmentId))
              and (:sectionId is null or exists (
                    select 1 from Employee e where e.id = c.employeeId and e.sectionId = :sectionId))
            order by c.expiryDate asc
            """)
    Page<EmployeeCertification> findExpiringOnOrBefore(LocalDate minExpiry,
                                                      LocalDate maxExpiry,
                                                      Long departmentId,
                                                      Long sectionId,
                                                      Pageable pageable);
}

package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.OffCyclePaymentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OffCyclePaymentTypeRepository extends JpaRepository<OffCyclePaymentType, Long> {

    List<OffCyclePaymentType> findAllByOrderByNameAsc();

    List<OffCyclePaymentType> findAllByActiveOrderByNameAsc(Boolean active);

    boolean existsByNameIgnoreCase(String name);
}
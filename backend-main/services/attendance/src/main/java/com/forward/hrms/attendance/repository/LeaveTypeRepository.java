package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.LeaveType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    Optional<LeaveType> findByCode(String code);

    // future-proof to support multiple leave types with encashment policy.
    List<LeaveType> findAllByEncashableTrueAndActiveTrueOrderByCodeAsc();
    
    boolean existsByCode(String code);

    List<LeaveType> findAllByActiveTrueOrderByCodeAsc();

    List<LeaveType> findAllByOrderByCodeAsc();
}

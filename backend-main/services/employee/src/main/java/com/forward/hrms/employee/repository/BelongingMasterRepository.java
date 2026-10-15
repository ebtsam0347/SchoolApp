package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.dto.response.BelongingMasterResponse;
import com.forward.hrms.employee.entity.BelongingMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BelongingMasterRepository extends JpaRepository<BelongingMaster, Long> {
    Optional<BelongingMaster> findByBelongingNameIgnoreCase(String belongingName);

    List<BelongingMaster> findByActiveTrue();
    boolean existsByBelongingNameIgnoreCase(String belongingName);

    List<BelongingMasterResponse> findByAuthorizedEmployeeId(Long authorizedEmployeeId);
}

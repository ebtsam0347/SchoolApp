package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.EmergencyContact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {

    List<EmergencyContact> findAllByEmployeeIdOrderByPrimaryDescIdAsc(Long employeeId);

    /** Demote every other primary contact for this employee. */
    @Modifying
    @Query("update EmergencyContact c set c.primary = false " +
           "where c.employeeId = :employeeId and c.primary = true and c.id <> :exceptId")
    int demoteOtherPrimaries(Long employeeId, Long exceptId);
}

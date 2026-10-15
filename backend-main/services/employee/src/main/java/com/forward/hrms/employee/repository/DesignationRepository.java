package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.Designation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface DesignationRepository extends JpaRepository<Designation, Long> {
    boolean existsByCode(String code);
    List<Designation> findAllByOrderByTitleAsc();
    List<Designation> findAllByActiveOrderByTitleAsc(boolean active);

    @Query("SELECT d.id FROM Designation d")
    Set<Long> findAllDesignationIds();
}

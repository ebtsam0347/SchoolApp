package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.Section;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface SectionRepository extends JpaRepository<Section, Long> {
    boolean existsByCode(String code);
    List<Section> findAllByOrderByNameAsc();
    List<Section> findAllByDepartmentIdOrderByNameAsc(Long departmentId);
    List<Section> findAllByActiveOrderByNameAsc(boolean active);
    List<Section> findAllByDepartmentIdAndActiveOrderByNameAsc(Long departmentId, boolean active);

    @Query("SELECT s.id FROM Section s")
    Set<Long> findAllSectionIds();
}

package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.Qualification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QualificationRepository extends JpaRepository<Qualification, Long> {
    boolean existsByCode(String code);

    @Query("select q from Qualification q order by q.displayOrder, q.name")
    List<Qualification> findAllByOrderByNameAsc();

    @Query("select q from Qualification q where q.level = :level order by q.displayOrder, q.name")
    List<Qualification> findAllByLevelOrderByNameAsc(String level);

    @Query("select q from Qualification q where q.active = :active order by q.displayOrder, q.name")
    List<Qualification> findAllByActiveOrderByNameAsc(boolean active);

    @Query("select q from Qualification q where q.level = :level and q.active = :active order by q.displayOrder, q.name")
    List<Qualification> findAllByLevelAndActiveOrderByNameAsc(String level, boolean active);
}

package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.Grade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    boolean existsByCode(String code);

    Optional<Grade> findByCode(String code);

    @Query("select g from Grade g order by g.displayOrder, g.level, g.code")
    List<Grade> findAllByOrderByLevelAsc();

    @Query("select g from Grade g where g.active = :active order by g.displayOrder, g.level, g.code")
    List<Grade> findAllByActiveOrderByLevelAsc(boolean active);

    @Query("SELECT g.id FROM Grade g")
    Set<Long> findAllGradeIds();
}

package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.Skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    boolean existsByCode(String code);
    List<Skill> findAllByOrderByNameAsc();
    List<Skill> findAllByCategoryOrderByNameAsc(String category);
    List<Skill> findAllByActiveOrderByNameAsc(boolean active);
    List<Skill> findAllByCategoryAndActiveOrderByNameAsc(String category, boolean active);
}

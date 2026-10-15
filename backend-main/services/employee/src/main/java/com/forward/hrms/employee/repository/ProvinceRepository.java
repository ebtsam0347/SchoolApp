package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.Province;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProvinceRepository extends JpaRepository<Province, Long> {
    boolean existsByCode(String code);
    List<Province> findAllByOrderByNameAsc();
    List<Province> findAllByActiveOrderByNameAsc(boolean active);
}

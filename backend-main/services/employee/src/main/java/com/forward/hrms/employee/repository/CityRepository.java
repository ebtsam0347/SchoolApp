package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.City;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {
    boolean existsByCode(String code);

    Optional<City> findByCode(String code);

    @Query("select c from City c order by c.displayOrder, c.name")
    List<City> findAllByOrderByNameAsc();

    @Query("select c from City c where c.province.id = :provinceId order by c.displayOrder, c.name")
    List<City> findAllByProvinceIdOrderByNameAsc(Long provinceId);

    @Query("select c from City c where c.active = :active order by c.displayOrder, c.name")
    List<City> findAllByActiveOrderByNameAsc(boolean active);

    @Query("select c from City c where c.province.id = :provinceId and c.active = :active order by c.displayOrder, c.name")
    List<City> findAllByProvinceIdAndActiveOrderByNameAsc(Long provinceId, boolean active);
}

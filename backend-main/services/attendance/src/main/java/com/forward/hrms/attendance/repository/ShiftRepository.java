package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.Shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    Optional<Shift> findByCode(String code);

    boolean existsByCode(String code);

    @Query("select s from Shift s where s.active = true order by s.displayOrder, s.code")
    List<Shift> findAllByActiveTrueOrderByCodeAsc();
}

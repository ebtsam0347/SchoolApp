package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.Holiday;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findAllByHolidayDate(LocalDate date);

    List<Holiday> findAllByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate from, LocalDate to);

    boolean existsByHolidayDate(LocalDate date);
}

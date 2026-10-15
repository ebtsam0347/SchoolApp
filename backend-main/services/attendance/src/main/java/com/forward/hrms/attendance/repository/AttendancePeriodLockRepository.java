package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.AttendancePeriodLock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendancePeriodLockRepository extends JpaRepository<AttendancePeriodLock, Long> {

    Optional<AttendancePeriodLock> findByPeriodYearAndPeriodMonth(int periodYear, int periodMonth);

    boolean existsByPeriodYearAndPeriodMonth(int periodYear, int periodMonth);

    List<AttendancePeriodLock> findByPeriodYearOrderByPeriodMonthAsc(int periodYear);
}

package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.DeviceEventLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface DeviceEventLogRepository extends JpaRepository<DeviceEventLog, Long> {

    /**
     * Distinct calendar dates on which the employee was logged at a
     * CANTEEN device, within the half-open interval
     * {@code [from, toExclusive)}. Service-level aggregation derives
     * "days" (size) and "weeks" (distinct 7-day chunks from day 1) from
     * this list — one DB round trip per employee.
     */
    @Query(value = """
            select distinct date(punched_at)
              from device_event_logs
             where employee_id = :employeeId
               and device_type = 'CANTEEN'
               and punched_at >= :from
               and punched_at <  :toExclusive
             order by 1
            """, nativeQuery = true)
    List<LocalDate> findDistinctCanteenDates(@Param("employeeId") long employeeId,
                                              @Param("from") LocalDateTime from,
                                              @Param("toExclusive") LocalDateTime toExclusive);
}

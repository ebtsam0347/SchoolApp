package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.PunchUnmatched;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PunchUnmatchedRepository extends JpaRepository<PunchUnmatched, Long> {

    /** Used by the device-DTO read so HR can see "X cards waiting to be matched". */
    long countByDeviceId(long deviceId);

    /**
     * Replay batch: pull rows whose employee_code now has a matching
     * {@link com.forward.hrms.attendance.entity.AttendanceEmployee}
     * snapshot row. Bounded by the caller's {@link Pageable}; an
     * unbounded query OOM-killed the JVM when the queue grew to ~560k
     * rows after a poller date-parsing bug created a multi-year
     * backlog (see commit c87e4f8).
     */
    @Query("""
            select u from PunchUnmatched u
            where exists (
                select 1 from AttendanceEmployee e
                where e.employeeCode = u.employeeCode)
            order by u.id
            """)
    List<PunchUnmatched> findResolvable(Pageable pageable);

    @Modifying
    @Query("delete from PunchUnmatched u where u.id in :ids")
    int deleteByIdIn(@Param("ids") List<Long> ids);
}

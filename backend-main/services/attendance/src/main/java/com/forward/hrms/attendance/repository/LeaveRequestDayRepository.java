package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.LeaveRequestDay;
import com.forward.hrms.attendance.enums.LeaveDayStatus;
import com.forward.hrms.attendance.repository.projection.ApprovedLeaveDayProjection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface LeaveRequestDayRepository extends JpaRepository<LeaveRequestDay, Long> {

    List<LeaveRequestDay> findAllByLeaveRequestIdOrderByAttendanceDateAsc(Long leaveRequestId);

    List<LeaveRequestDay> findAllByLeaveRequestIdAndStatus(Long leaveRequestId, LeaveDayStatus status);

    /**
     * APPROVED leave-day rows for {@code (employee, date)} — joined to the
     * parent so we can filter on employee_id without storing it on the
     * day row. At most one match (overlap rejected at submit).
     */
    @Query("""
            select d from LeaveRequestDay d
            join LeaveRequest r on r.id = d.leaveRequestId
            where d.attendanceDate = :date
              and d.status = com.forward.hrms.attendance.enums.LeaveDayStatus.APPROVED
              and r.employeeId = :employeeId
            """)
    List<LeaveRequestDay> findApprovedDayForEmployee(LocalDate date, Long employeeId);

    /**
     * Employee ids on APPROVED leave for {@code date} — one query, used by
     * the live not-checked-in roster to exclude employees who aren't
     * expected in today.
     */
    @Query("""
            select r.employeeId from LeaveRequestDay d
            join LeaveRequest r on r.id = d.leaveRequestId
            where d.attendanceDate = :date
              and d.status = com.forward.hrms.attendance.enums.LeaveDayStatus.APPROVED
            """)
    List<Long> findEmployeeIdsApprovedOnDate(LocalDate date);

    /**
     * APPROVED leave-day rows (one per calendar day) for a page of
     * employees over {@code [from, to]} — the single source for both the
     * payslip attendance classification and the per-type leave usage
     * (month + cycle), so one query serves the whole page.
     */
    @Query("""
            select r.employeeId as employeeId, d.attendanceDate as attendanceDate,
                   r.leaveTypeId as leaveTypeId, r.halfDay as halfDay
            from LeaveRequestDay d
            join LeaveRequest r on r.id = d.leaveRequestId
            where r.employeeId in :employeeIds
              and d.status = com.forward.hrms.attendance.enums.LeaveDayStatus.APPROVED
              and d.attendanceDate between :from and :to
            """)
    List<ApprovedLeaveDayProjection> approvedDaysForEmployees(
            @Param("employeeIds") Collection<Long> employeeIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}

package com.forward.hrms.employee.service;

import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeHistory;
import com.forward.hrms.employee.enums.HistoryChangeReason;
import com.forward.hrms.employee.repository.EmployeeHistoryRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Maintains the per-employee point-in-time {@code employee_history} log.
 * Always called inside the same business transaction that mutates the
 * employee row, so the history is committed atomically with the change.
 */
@Service
@RequiredArgsConstructor
public class EmployeeHistoryService {

    private final EmployeeHistoryRepository repo;

    @Transactional(propagation = Propagation.MANDATORY)
    public void seed(Employee e, HistoryChangeReason reason, String notes) {
        EmployeeHistory row = new EmployeeHistory();
        row.setEmployeeId(e.getId());
        row.setEffectiveFrom(e.getJoiningDate());
        row.setEffectiveTo(null);
        row.setDepartmentId(e.getDepartmentId());
        row.setSectionId(e.getSectionId());
        row.setDesignationId(e.getDesignationId());
        row.setGradeId(e.getGradeId());
        row.setManagerId(e.getManagerId());
        row.setChangeReason(reason);
        row.setNotes(notes);
        repo.save(row);
    }

    /**
     * Close the current history row and open a new one reflecting the
     * employee's current state. Idempotent: if the current row matches
     * the employee's current assignment, no-op. When the current row was
     * itself opened today (e.g., a second change on the same calendar
     * day), update it in place instead of closing it — closing it would
     * write {@code effective_to = today - 1} against an
     * {@code effective_from = today}, an inverted range.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordChange(Employee e, HistoryChangeReason reason, String notes) {
        Optional<EmployeeHistory> current = repo.findFirstByEmployeeIdAndEffectiveToIsNull(e.getId());
        LocalDate today = LocalDate.now();

        if (current.isPresent() && matches(current.get(), e)) return;

        if (current.isPresent() && today.equals(current.get().getEffectiveFrom())) {
            EmployeeHistory row = current.get();
            row.setDepartmentId(e.getDepartmentId());
            row.setSectionId(e.getSectionId());
            row.setDesignationId(e.getDesignationId());
            row.setGradeId(e.getGradeId());
            row.setManagerId(e.getManagerId());
            row.setChangeReason(reason);
            row.setNotes(notes);
            return;
        }

        if (current.isPresent()) {
            EmployeeHistory prev = current.get();
            prev.setEffectiveTo(today.minusDays(1));
            // Flush the UPDATE before queueing the INSERT below — Hibernate's
            // ActionQueue always runs INSERTs before UPDATEs within a single
            // flush, which would collide with uq_history_current (partial
            // unique index on employee_id WHERE effective_to IS NULL).
            repo.saveAndFlush(prev);
        }

        EmployeeHistory row = new EmployeeHistory();
        row.setEmployeeId(e.getId());
        row.setEffectiveFrom(today);
        row.setEffectiveTo(null);
        row.setDepartmentId(e.getDepartmentId());
        row.setSectionId(e.getSectionId());
        row.setDesignationId(e.getDesignationId());
        row.setGradeId(e.getGradeId());
        row.setManagerId(e.getManagerId());
        row.setChangeReason(reason);
        row.setNotes(notes);
        repo.save(row);
    }

    /**
     * Close the current row when an employee exits. Used by the
     * termination saga.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeOnExit(Employee e, LocalDate lastWorkingDay) {
        repo.findFirstByEmployeeIdAndEffectiveToIsNull(e.getId())
                .ifPresent(row -> {
                    row.setEffectiveTo(lastWorkingDay);
                    row.setChangeReason(HistoryChangeReason.EXIT);
                });
    }

    /**
     * Reverse of {@link #closeOnExit}. Used by
     * {@code TerminationSaga.undoTermination} when HR rejects a final
     * settlement — reopens the row that was closed at that exact
     * {@code lastWorkingDay} (only if it's still closed on EXIT; a no-op
     * otherwise, since some other change may have superseded it since).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reopenOnUndo(Employee e, LocalDate lastWorkingDay) {
        repo.findFirstByEmployeeIdAndEffectiveTo(e.getId(), lastWorkingDay)
                .filter(row -> row.getChangeReason() == HistoryChangeReason.EXIT)
                .ifPresent(row -> {
                    row.setEffectiveTo(null);
                    row.setChangeReason(HistoryChangeReason.REINSTATED);
                });
    }

    private static boolean matches(EmployeeHistory row, Employee e) {
        return java.util.Objects.equals(row.getDepartmentId(), e.getDepartmentId())
                && java.util.Objects.equals(row.getSectionId(), e.getSectionId())
                && java.util.Objects.equals(row.getDesignationId(), e.getDesignationId())
                && java.util.Objects.equals(row.getGradeId(), e.getGradeId())
                && java.util.Objects.equals(row.getManagerId(), e.getManagerId());
    }
}

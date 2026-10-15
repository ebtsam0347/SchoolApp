package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.payroll.dto.request.AddSalaryLineRequest;
import com.forward.hrms.payroll.dto.request.RemoveSalaryLineRequest;
import com.forward.hrms.payroll.dto.request.UpdateSalaryLineRequest;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryComponentDto;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryComponentDtoMapper;
import com.forward.hrms.payroll.entity.EmployeeSalaryComponent;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.events.PayrollEvents;
import com.forward.hrms.payroll.repository.EmployeeSalaryComponentRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmployeeSalaryComponentService {

    private final EmployeeSalaryComponentRepository repo;
    private final SalaryComponentRepository componentRepo;
    private final Outbox outbox;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<EmployeeSalaryComponentDto> listCurrent(long employeeId, ComponentType type) {
        return repo.findCurrent(employeeId, type).stream()
                .map(line -> EmployeeSalaryComponentDtoMapper.of(line, componentOf(line.getComponentId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeSalaryComponentDto> history(long employeeId) {
        return repo.findAllByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
                .map(line -> EmployeeSalaryComponentDtoMapper.of(line, componentOf(line.getComponentId())))
                .toList();
    }

    @Transactional
    public EmployeeSalaryComponentDto add(long employeeId, AddSalaryLineRequest req) {
        SalaryComponent comp = componentRepo.findById(req.componentId()).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.COMPONENT_NOT_FOUND, "component not found"));
        if (!comp.isActive()) {
            throw new ConflictError(PayrollErrorCode.COMPONENT_INACTIVE,
                    "component is inactive");
        }
        BigDecimal amount = req.amount() != null
                ? req.amount()
                : Optional.ofNullable(comp.getDefaultAmount()).orElseThrow(
                // §7.payroll #69 — caller didn't supply an amount and
                // the component has no default. Use the dedicated
                // code instead of "component not found".
                () -> new ConflictError(PayrollErrorCode.COMPONENT_AMOUNT_REQUIRED,
                        "amount required — component has no default"));

        // Close any current open-ended line for the same component (replacing it).
        Optional<EmployeeSalaryComponent> current = repo.findCurrent(employeeId, null).stream()
                .filter(l -> l.getComponentId().equals(comp.getId()))
                .findFirst();
        // §7.payroll #67 — refuse to close the existing line with an
        // effective_to that lands on or before its effective_from. The DB
        // constraint chk_emp_comp_dates would otherwise flip a 422 into a 500.
        current.ifPresent(line -> {
            if (!req.effectiveFrom().isAfter(line.getEffectiveFrom())) {
                throw new ConflictError(PayrollErrorCode.COMPONENT_DATE_INVALID,
                        "effectiveFrom must be after the current line's effectiveFrom ("
                                + line.getEffectiveFrom() + ")");
            }
            line.setEffectiveTo(req.effectiveFrom().minusDays(1));
        });

        EmployeeSalaryComponent line = new EmployeeSalaryComponent();
        line.setEmployeeId(employeeId);
        line.setComponentId(comp.getId());
        line.setAmount(amount);
        line.setEffectiveFrom(req.effectiveFrom());
        line.setNotes(req.notes());
        line = repo.save(line);

        emit(employeeId, comp, current.map(EmployeeSalaryComponent::getAmount).orElse(null),
                amount, req.effectiveFrom(),
                current.isPresent() ? "UPDATED" : "ADDED");
        return EmployeeSalaryComponentDtoMapper.of(line, comp);
    }

    @Transactional
    public EmployeeSalaryComponentDto update(long employeeId, long lineId, UpdateSalaryLineRequest req) {
        EmployeeSalaryComponent existing = loadOwned(employeeId, lineId);
        SalaryComponent comp = componentOf(existing.getComponentId());
        BigDecimal fromAmount = existing.getAmount();
        // §7.payroll #68 — same date guard as #67 on the update path.
        if (!req.effectiveFrom().isAfter(existing.getEffectiveFrom())) {
            throw new ConflictError(PayrollErrorCode.COMPONENT_DATE_INVALID,
                    "effectiveFrom must be after the existing line's effectiveFrom ("
                            + existing.getEffectiveFrom() + ")");
        }
        existing.setEffectiveTo(req.effectiveFrom().minusDays(1));

        EmployeeSalaryComponent fresh = new EmployeeSalaryComponent();
        fresh.setEmployeeId(employeeId);
        fresh.setComponentId(existing.getComponentId());
        fresh.setAmount(req.amount());
        fresh.setEffectiveFrom(req.effectiveFrom());
        fresh.setNotes(req.notes());
        fresh = repo.save(fresh);

        emit(employeeId, comp, fromAmount, req.amount(), req.effectiveFrom(), "UPDATED");
        return EmployeeSalaryComponentDtoMapper.of(fresh, comp);
    }

    @Transactional
    public void remove(long employeeId, long lineId, RemoveSalaryLineRequest req) {
        EmployeeSalaryComponent existing = loadOwned(employeeId, lineId);
        SalaryComponent comp = componentOf(existing.getComponentId());
        existing.setEffectiveTo(req.effectiveTo());
        emit(employeeId, comp, existing.getAmount(), null, req.effectiveTo(), "REMOVED");
    }

    /**
     * Used by increment-batch apply to swap the current INCOME_TAX line for a
     * new amount. Closes the old row and inserts a new one. No-ops if the
     * employee has no current INCOME_TAX line (caller validates earlier).
     */
    @Transactional
    public void replaceIncomeTax(long employeeId, BigDecimal newAmount, LocalDate effectiveFrom) {
        EmployeeSalaryComponent existing = repo.findCurrentByCode(employeeId, "INCOME_TAX")
                .orElseThrow(() -> new ConflictError(PayrollErrorCode.SALARY_LINE_NOT_FOUND,
                        "employee has no current INCOME_TAX line"));
        SalaryComponent comp = componentOf(existing.getComponentId());
        BigDecimal fromAmount = existing.getAmount();
        existing.setEffectiveTo(effectiveFrom.minusDays(1));

        EmployeeSalaryComponent fresh = new EmployeeSalaryComponent();
        fresh.setEmployeeId(employeeId);
        fresh.setComponentId(existing.getComponentId());
        fresh.setAmount(newAmount);
        fresh.setEffectiveFrom(effectiveFrom);
        fresh.setNotes("revised by increment batch");
        repo.save(fresh);

        emit(employeeId, comp, fromAmount, newAmount, effectiveFrom, "UPDATED");
    }

    /**
     * Close all current salary components on employee termination.
     * <p>
     * Idempotent:
     * - Already closed components are ignored.
     * - If no active components exist, nothing happens.
     */
    @Transactional
    public void closeOnExit(
            long employeeId,
            LocalDate lastWorkingDay
    ) {

        List<EmployeeSalaryComponent> components = repo.findCurrent(employeeId, null);

        if (components.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (EmployeeSalaryComponent component : components) {

            if (component.getEffectiveTo() == null || component.getEffectiveTo().isAfter(lastWorkingDay)) {

                component.setEffectiveTo(lastWorkingDay);
                component.setUpdatedAt(now);
                component.setNotes("closed on exit.");
                //component.setIpAddress();

            }
        }
    }

    /**
     * Reverses {@link #closeOnExit}. Fired when HR rejects a final
     * settlement and {@code employee.reinstated} arrives — reopens
     * every line closed on {@code lastWorkingDay} that carries the
     * {@code "closed on exit."} marker this method wrote, so a line an
     * HR admin deliberately removed the same day isn't accidentally
     * reopened. Idempotent — already-reopened lines (effective_to
     * already null) simply won't match the lookup on redelivery.
     */
    @Transactional
    public void reopenOnExit(
            long employeeId,
            LocalDate lastWorkingDay
    ) {
        List<EmployeeSalaryComponent> closed = repo.findByEmployeeIdAndEffectiveTo(employeeId, lastWorkingDay);
        if (closed.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (EmployeeSalaryComponent component : closed) {
            if ("closed on exit.".equals(component.getNotes())) {
                component.setEffectiveTo(null);
                component.setUpdatedAt(now);
                component.setNotes(null);
            }
        }
    }

    private EmployeeSalaryComponent loadOwned(long employeeId, long lineId) {
        EmployeeSalaryComponent line = repo.findById(lineId).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.SALARY_LINE_NOT_FOUND, "salary line not found"));
        if (!line.getEmployeeId().equals(employeeId)) {
            throw new NotFoundError(PayrollErrorCode.SALARY_LINE_NOT_FOUND, "salary line not found");
        }
        if (line.getEffectiveTo() != null) {
            throw new ConflictError(PayrollErrorCode.SALARY_LINE_NOT_CURRENT,
                    "salary line is not current");
        }
        return line;
    }

    private SalaryComponent componentOf(long id) {
        return componentRepo.findById(id).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.COMPONENT_NOT_FOUND, "component not found"));
    }

    /** Outbox event + audit row for one line change. Also used by {@link EmployeeAllowanceService}. */
    void emit(long employeeId, SalaryComponent comp,
                      BigDecimal fromAmount, BigDecimal toAmount, LocalDate effectiveFrom, String action) {
        outbox.publish(PayrollEvents.TOPIC, "employee:" + employeeId,
                PayrollEvents.SALARY_LINE_CHANGED,
                new PayrollEvents.SalaryLineChanged(employeeId, comp.getId(), comp.getCode(),
                        comp.getType().name(), fromAmount, toAmount, effectiveFrom, action));
        audit.emit("employee_salary_component", String.valueOf(employeeId),
                "salary_line_" + action.toLowerCase(),
                fromAmount == null ? null : Map.of("amount", String.valueOf(fromAmount)),
                Map.of("componentCode", comp.getCode(),
                        "amount", toAmount == null ? "" : String.valueOf(toAmount),
                        "effectiveFrom", effectiveFrom.toString()),
                true);
    }
}

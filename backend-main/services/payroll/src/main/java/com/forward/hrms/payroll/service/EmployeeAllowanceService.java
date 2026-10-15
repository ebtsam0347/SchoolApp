package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.payroll.dto.request.SetAllowancesRequest;
import com.forward.hrms.payroll.dto.response.AllowanceColumnDto;
import com.forward.hrms.payroll.dto.response.AllowanceEmployeeDto;
import com.forward.hrms.payroll.dto.response.AllowanceHistoryEntryDto;
import com.forward.hrms.payroll.dto.response.EmployeeAllowanceRowDto;
import com.forward.hrms.payroll.dto.response.EmployeeAllowancesDto;
import com.forward.hrms.payroll.entity.EmployeeSalaryComponent;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.EmployeeSalaryComponentRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Employee allowances — the flat monthly allowances paid apart from salary
 * (catalog components with {@code calculate_with_salary = false}). Each
 * employee's set is their effective-dated {@link EmployeeSalaryComponent}
 * lines on those components; the allowance run pays the open ones. This
 * service reads them as a set (grid, one employee, version history) and
 * replaces a set in one call.
 */
@Service
@RequiredArgsConstructor
public class EmployeeAllowanceService {

    private final EmployeeSalaryComponentRepository lines;
    private final SalaryComponentRepository components;
    private final EmployeeSalaryComponentService salaryComponents;
    private final EmployeeDisplayCache displayCache;

    /** Line writes closer together than this belong to the same save. */
    static final Duration SAME_SAVE = Duration.ofSeconds(5);

    @Transactional(readOnly = true)
    public List<AllowanceColumnDto> columns() {
        return components.findAllowanceComponents().stream()
                .map(c -> new AllowanceColumnDto(c.getId(), c.getCode(), c.getName()))
                .toList();
    }

    /**
     * Every employee with at least one open allowance line. Names live in the
     * employee schema, so search and paging run in memory over the display
     * cache — a few thousand rows at most.
     */
    @Transactional(readOnly = true)
    public PageResponse<EmployeeAllowanceRowDto> employees(String q, Pageable pageable) {
        Map<Long, List<EmployeeSalaryComponent>> byEmployee = lines.findAllowanceLines().stream()
                .collect(Collectors.groupingBy(EmployeeSalaryComponent::getEmployeeId));
        byEmployee.values().removeIf(ls -> ls.stream().noneMatch(EmployeeAllowanceService::isOpen));
        Map<Long, String> codes = componentCodes(byEmployee.values().stream().flatMap(List::stream).toList());
        Map<Long, EmployeeDisplay> displays = displayCache.getMany(byEmployee.keySet());

        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        List<EmployeeAllowanceRowDto> rows = byEmployee.entrySet().stream()
                .map(e -> {
                    EmployeeDisplay d = displays.getOrDefault(e.getKey(), EmployeeDisplay.placeholder(e.getKey()));
                    List<EmployeeSalaryComponent> open = e.getValue().stream().filter(EmployeeAllowanceService::isOpen).toList();
                    Map<String, BigDecimal> amounts = amounts(open, codes);
                    return new EmployeeAllowanceRowDto(e.getKey(), employee(d), amounts, since(open, codes),
                            total(amounts), lastChanged(e.getValue()));
                })
                .filter(r -> needle == null || matches(r.employee(), needle))
                .sorted(Comparator.comparing((EmployeeAllowanceRowDto r) -> r.employee().employeeCode(),
                        Comparator.nullsLast(EmployeeAllowanceService::compareCardNo)))
                .toList();

        int start = (int) Math.min(pageable.getOffset(), rows.size());
        int end = Math.min(start + pageable.getPageSize(), rows.size());
        return PageResponse.from(new PageImpl<>(rows.subList(start, end), pageable, rows.size()));
    }

    @Transactional(readOnly = true)
    public EmployeeAllowancesDto forEmployee(long employeeId) {
        EmployeeDisplay d = requireEmployee(employeeId);
        List<EmployeeSalaryComponent> all = lines.findAllowanceLines(employeeId);
        List<EmployeeSalaryComponent> open = all.stream().filter(EmployeeAllowanceService::isOpen).toList();
        Map<Long, String> codes = componentCodes(open);
        return new EmployeeAllowancesDto(employee(d), amounts(open, codes), since(open, codes), lastChanged(all));
    }

    /**
     * Latest date any of the employee's allowances changed: a line starting
     * (added or re-priced) or a line closing (re-priced or removed).
     */
    private static LocalDate lastChanged(Collection<EmployeeSalaryComponent> ls) {
        return ls.stream()
                .map(l -> isOpen(l) ? l.getEffectiveFrom() : l.getEffectiveTo())
                .max(Comparator.naturalOrder()).orElse(null);
    }

    private static boolean isOpen(EmployeeSalaryComponent l) {
        return l.getEffectiveTo() == null;
    }

    /**
     * Versions of the set, newest first — one per save. Every save opens
     * lines ({@code created_at}) and/or closes lines ({@code updated_at} of a
     * closed line); one save's writes land within milliseconds, separate saves
     * seconds apart, so events less than {@link #SAME_SAVE} apart are one
     * save. Replaying the saves in order gives the lines in force after each.
     * A save that leaves nothing in force (everything stopped) isn't a
     * version, but it still closes the one before.
     */
    @Transactional(readOnly = true)
    public List<AllowanceHistoryEntryDto> history(long employeeId) {
        requireEmployee(employeeId);
        List<EmployeeSalaryComponent> all = lines.findAllowanceLines(employeeId);
        Map<Long, String> codes = componentCodes(all);

        List<LineEvent> events = new ArrayList<>();
        for (EmployeeSalaryComponent l : all) {
            events.add(new LineEvent(l.getCreatedAt(), l.getCreatedBy(), l, true));
            if (!isOpen(l)) events.add(new LineEvent(l.getUpdatedAt(), l.getUpdatedBy(), l, false));
        }
        events.sort(Comparator.comparing(LineEvent::at));

        List<List<LineEvent>> saves = new ArrayList<>();
        for (LineEvent e : events) {
            List<LineEvent> last = saves.isEmpty() ? null : saves.getLast();
            if (last != null && Duration.between(last.getLast().at(), e.at()).compareTo(SAME_SAVE) < 0) {
                last.add(e);
            } else {
                saves.add(new ArrayList<>(List.of(e)));
            }
        }

        Map<Long, EmployeeSalaryComponent> inForce = new LinkedHashMap<>();
        List<AllowanceHistoryEntryDto> versions = new ArrayList<>();
        for (int i = 0; i < saves.size(); i++) {
            for (LineEvent e : saves.get(i)) {
                if (e.opens()) inForce.put(e.line().getId(), e.line());
                else inForce.remove(e.line().getId());
            }
            if (inForce.isEmpty()) continue;

            LineEvent made = saves.get(i).getFirst();
            LineEvent next = i + 1 < saves.size() ? saves.get(i + 1).getFirst() : null;
            Map<String, BigDecimal> amounts = amounts(List.copyOf(inForce.values()), codes);
            versions.add(new AllowanceHistoryEntryDto(
                    made.at().toLocalDate(), next == null ? null : next.at().toLocalDate(),
                    amounts, total(amounts),
                    made.by(), made.at(),
                    next == null ? null : next.by(), next == null ? null : next.at()));
        }
        return versions.reversed();
    }

    /** A line opening (created) or closing (its effective_to set) at a moment, by a user. */
    private record LineEvent(LocalDateTime at, String by, EmployeeSalaryComponent line, boolean opens) {}

    /**
     * Replace the employee's set as of today. The allowance run pays every
     * open line whatever its dates, so the date only records when HR made the
     * change (like the old HRMS entry date). Per allowance: a changed amount
     * closes the open line today and opens a new one today; 0 / absent closes
     * it today; an unchanged amount is left alone. Lines are never edited in
     * place or deleted, so every save — several a day included — is its own
     * History version. Each change goes through the same outbox event + audit
     * row as a single line edit.
     */
    @Transactional
    public void set(long employeeId, SetAllowancesRequest req) {
        requireEmployee(employeeId);
        LocalDate today = LocalDate.now();

        Map<String, SalaryComponent> allowances = components.findAllowanceComponents().stream()
                .collect(Collectors.toMap(SalaryComponent::getCode, Function.identity()));
        List<String> unknown = req.amounts().keySet().stream().filter(c -> !allowances.containsKey(c)).toList();
        if (!unknown.isEmpty()) {
            throw new UnprocessableError(PayrollErrorCode.ALLOWANCE_COMPONENT_INVALID,
                    "not an active allowance paid apart from salary: " + String.join(", ", unknown));
        }

        Map<Long, EmployeeSalaryComponent> open = lines.findOpenAllowanceLines(employeeId).stream()
                .collect(Collectors.toMap(EmployeeSalaryComponent::getComponentId, Function.identity()));

        for (SalaryComponent comp : allowances.values()) {
            BigDecimal wanted = req.amounts().get(comp.getCode());
            if (wanted != null && wanted.signum() == 0) wanted = null;
            EmployeeSalaryComponent current = open.get(comp.getId());
            BigDecimal had = current == null ? null : current.getAmount();
            if (had == null && wanted == null) continue;
            if (had != null && wanted != null && had.compareTo(wanted) == 0) continue;

            if (current != null) {
                // A line month-dated ahead by an older save can't end before it starts.
                LocalDate from = current.getEffectiveFrom();
                current.setEffectiveTo(from.isAfter(today) ? from : today);
            }
            if (wanted != null) {
                EmployeeSalaryComponent line = new EmployeeSalaryComponent();
                line.setEmployeeId(employeeId);
                line.setComponentId(comp.getId());
                line.setAmount(wanted);
                line.setEffectiveFrom(today);
                lines.save(line);
            }
            String action = had == null ? "ADDED" : wanted == null ? "REMOVED" : "UPDATED";
            salaryComponents.emit(employeeId, comp, had, wanted, today, action);
        }
    }

    private EmployeeDisplay requireEmployee(long employeeId) {
        EmployeeDisplay d = displayCache.get(employeeId);
        if (d == null) {
            throw new NotFoundError(PayrollErrorCode.EMPLOYEE_NOT_FOUND, "employee not found");
        }
        return d;
    }

    private Map<Long, String> componentCodes(Collection<EmployeeSalaryComponent> ls) {
        List<Long> ids = ls.stream().map(EmployeeSalaryComponent::getComponentId).distinct().toList();
        return components.findAllById(ids).stream()
                .collect(Collectors.toMap(SalaryComponent::getId, SalaryComponent::getCode));
    }

    /** Each open allowance's own start date. One open line per component, so no merge conflict in practice. */
    private static Map<String, LocalDate> since(List<EmployeeSalaryComponent> open, Map<Long, String> codes) {
        Map<String, LocalDate> out = new LinkedHashMap<>();
        for (EmployeeSalaryComponent l : open) {
            String code = codes.get(l.getComponentId());
            if (code != null) out.merge(code, l.getEffectiveFrom(), (a, b) -> a.isAfter(b) ? a : b);
        }
        return out;
    }

    private static Map<String, BigDecimal> amounts(List<EmployeeSalaryComponent> ls, Map<Long, String> codes) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (EmployeeSalaryComponent l : ls) {
            String code = codes.get(l.getComponentId());
            if (code != null) out.merge(code, l.getAmount(), BigDecimal::add);
        }
        return out;
    }

    private static BigDecimal total(Map<String, BigDecimal> amounts) {
        return amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static AllowanceEmployeeDto employee(EmployeeDisplay d) {
        return new AllowanceEmployeeDto(d.employeeId(), d.fullName(), d.employeeCode(),
                d.departmentName(), d.sectionName(), d.designationTitle(), d.profilePictureUrl());
    }

    private static boolean matches(AllowanceEmployeeDto e, String needle) {
        return contains(e.fullName(), needle) || contains(e.employeeCode(), needle)
                || contains(e.departmentName(), needle) || contains(e.sectionName(), needle);
    }

    private static boolean contains(String s, String needle) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(needle);
    }

    /** Card nos are numeric strings; order 27 before 100, falling back to text for anything else. */
    private static int compareCardNo(String a, String b) {
        boolean na = a.chars().allMatch(Character::isDigit) && !a.isEmpty();
        boolean nb = b.chars().allMatch(Character::isDigit) && !b.isEmpty();
        if (na && nb) {
            int byLength = Integer.compare(a.length(), b.length());
            return byLength != 0 ? byLength : a.compareTo(b);
        }
        return Objects.compare(a, b, Comparator.naturalOrder());
    }
}

package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.AddEmployeeSkillRequest;
import com.forward.hrms.employee.dto.request.UpdateEmployeeSkillRequest;
import com.forward.hrms.employee.dto.response.EmployeeSkillDto;
import com.forward.hrms.employee.dto.response.SkillEmployeeDto;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeSkill;
import com.forward.hrms.employee.entity.Skill;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.EmployeeSkillRepository;
import com.forward.hrms.employee.repository.SkillRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EmployeeSkillService {

    private final EmployeeSkillRepository repo;
    private final EmployeeRepository employees;
    private final SkillRepository skills;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<EmployeeSkillDto> list(long employeeId) {
        ensureEmployee(employeeId);
        List<EmployeeSkill> rows = repo.findAllByEmployeeIdOrderByIdAsc(employeeId);
        Map<Long, Skill> byId = byIds(rows.stream().map(EmployeeSkill::getSkillId).toList());
        return rows.stream()
                .map(es -> EmployeeSkillDto.of(es, byId.get(es.getSkillId())))
                .toList();
    }

    @Transactional
    public EmployeeSkillDto add(long employeeId, AddEmployeeSkillRequest req) {
        ensureEmployee(employeeId);
        Skill s = skills.findById(req.skillId())
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.SKILL_NOT_FOUND, "skill not found"));
        if (!s.isActive()) {
            throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "skill is inactive");
        }
        if (repo.findByEmployeeIdAndSkillId(employeeId, req.skillId()).isPresent()) {
            throw new ConflictError(EmployeeErrorCode.EMPLOYEE_SKILL_DUPLICATE,
                    "skill already attached to this employee");
        }
        EmployeeSkill es = new EmployeeSkill();
        es.setEmployeeId(employeeId);
        es.setSkillId(req.skillId());
        es.setProficiency(req.proficiency());
        es.setYears(req.years());
        es.setNotes(req.notes());
        repo.save(es);
        audit.emit("employee_skill", String.valueOf(es.getId()), "added",
                null,
                Map.of("employeeId", employeeId, "skillCode", s.getCode(),
                       "proficiency", req.proficiency().name()),
                false);
        return EmployeeSkillDto.of(es, s);
    }

    @Transactional
    public EmployeeSkillDto update(long employeeId, long employeeSkillId, UpdateEmployeeSkillRequest req) {
        EmployeeSkill es = load(employeeId, employeeSkillId);
        if (req.proficiency() != null) es.setProficiency(req.proficiency());
        if (req.years() != null) es.setYears(req.years());
        if (req.notes() != null) es.setNotes(req.notes().isBlank() ? null : req.notes());
        Skill s = skills.findById(es.getSkillId()).orElse(null);
        audit.emit("employee_skill", String.valueOf(es.getId()), "updated",
                null,
                Map.of("proficiency", es.getProficiency().name()),
                false);
        return EmployeeSkillDto.of(es, s);
    }

    @Transactional
    public void delete(long employeeId, long employeeSkillId) {
        EmployeeSkill es = load(employeeId, employeeSkillId);
        repo.delete(es);
        audit.emit("employee_skill", String.valueOf(employeeSkillId), "removed",
                null, Map.of("employeeId", employeeId, "skillId", es.getSkillId()), false);
    }

    @Transactional(readOnly = true)
    public List<SkillEmployeeDto> employeesWithSkill(long skillId) {
        if (!skills.existsById(skillId)) {
            throw new NotFoundError(EmployeeErrorCode.SKILL_NOT_FOUND, "skill not found");
        }
        List<EmployeeSkill> rows = repo.findAllBySkillIdOrderByEmployeeIdAsc(skillId);
        Map<Long, Employee> byId = employeesByIds(rows.stream().map(EmployeeSkill::getEmployeeId).toList());
        return rows.stream()
                .map(es -> SkillEmployeeDto.of(es, byId.get(es.getEmployeeId())))
                .toList();
    }

    private EmployeeSkill load(long employeeId, long id) {
        return EmployeeOwnedRows.requireOwned(repo, EmployeeSkill::getEmployeeId,
                employeeId, id, EmployeeErrorCode.EMPLOYEE_SKILL_NOT_FOUND, "employee skill");
    }

    private Map<Long, Skill> byIds(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Set<Long> distinct = new HashSet<>(ids);
        Map<Long, Skill> out = new HashMap<>();
        skills.findAllById(distinct).forEach(s -> out.put(s.getId(), s));
        return out;
    }

    private Map<Long, Employee> employeesByIds(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Set<Long> distinct = new HashSet<>(ids);
        Map<Long, Employee> out = new HashMap<>();
        employees.findAllById(distinct).forEach(e -> out.put(e.getId(), e));
        return out;
    }

    private void ensureEmployee(long employeeId) {
        EmployeeOwnedRows.ensureEmployee(employees, employeeId);
    }
}

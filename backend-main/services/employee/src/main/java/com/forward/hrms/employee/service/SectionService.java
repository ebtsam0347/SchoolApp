package com.forward.hrms.employee.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.CreateSectionRequest;
import com.forward.hrms.employee.dto.request.UpdateSectionRequest;
import com.forward.hrms.employee.dto.response.SectionDto;
import com.forward.hrms.employee.dto.response.SectionDtoMapper;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.SectionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository repo;
    private final DepartmentRepository departments;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<SectionDto> list(Long departmentId, Boolean active) {
        List<Section> rows;
        if (departmentId != null && active != null) {
            rows = repo.findAllByDepartmentIdAndActiveOrderByNameAsc(departmentId, active);
        } else if (departmentId != null) {
            rows = repo.findAllByDepartmentIdOrderByNameAsc(departmentId);
        } else if (active != null) {
            rows = repo.findAllByActiveOrderByNameAsc(active);
        } else {
            rows = repo.findAllByOrderByNameAsc();
        }
        return rows.stream().map(SectionDtoMapper::of).toList();
    }

    @Transactional
    public SectionDto create(CreateSectionRequest req) {
        if (repo.existsByCode(req.code())) {
            throw new ConflictError(EmployeeErrorCode.SECTION_CODE_TAKEN, "section code already in use");
        }
        Department dept = loadActiveDepartment(req.departmentId());
        Section s = new Section();
        s.setCode(req.code());
        s.setName(req.name());
        s.setDepartment(dept);
        s.setHeadId(req.headId());
        s.setActive(true);
        repo.save(s);
        audit.emit("section", String.valueOf(s.getId()), "created", null, snapshot(s), false);
        return SectionDtoMapper.of(s);
    }

    @Transactional
    public SectionDto update(long id, UpdateSectionRequest req) {
        Section s = load(id);
        Map<String, Object> before = snapshot(s);
        if (req.name() != null && !req.name().isBlank()) s.setName(req.name());
        if (req.departmentId() != null && !req.departmentId().equals(s.getDepartment().getId())) {
            s.setDepartment(loadActiveDepartment(req.departmentId()));
        }
        if (req.headId() != null) s.setHeadId(req.headId() == 0 ? null : req.headId());
        if (req.active() != null) s.setActive(req.active());
        audit.emit("section", String.valueOf(s.getId()), "updated", before, snapshot(s), false);
        return SectionDtoMapper.of(s);
    }

    @Transactional(readOnly = true)
    public Section loadActive(long id) {
        Section s = load(id);
        if (!s.isActive()) {
            throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "section is inactive");
        }
        return s;
    }

    private Department loadActiveDepartment(long id) {
        Department d = departments.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.DEPARTMENT_NOT_FOUND, "department not found"));
        if (!d.isActive()) {
            throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "department is inactive");
        }
        return d;
    }

    private Section load(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.SECTION_NOT_FOUND, "section not found"));
    }

    private static Map<String, Object> snapshot(Section s) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", s.getCode());
        m.put("name", s.getName());
        m.put("departmentId", s.getDepartment().getId());
        m.put("headId", s.getHeadId());
        m.put("active", s.isActive());
        return m;
    }
}

package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.CreateDepartmentRequest;
import com.forward.hrms.employee.dto.request.UpdateDepartmentRequest;
import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.dto.response.DepartmentDtoMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository repo;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<DepartmentDto> list(Boolean active) {
        List<Department> rows = (active == null)
                ? repo.findAllByOrderByNameAsc()
                : repo.findAllByActiveOrderByNameAsc(active);
        return rows.stream().map(DepartmentDtoMapper::of).toList();
    }

    @Transactional
    public DepartmentDto create(CreateDepartmentRequest req) {
        if (repo.existsByCode(req.code())) {
            throw new ConflictError(EmployeeErrorCode.DEPARTMENT_CODE_TAKEN, "department code already in use");
        }
        Department d = new Department();
        d.setCode(req.code());
        d.setName(req.name());
        d.setHeadId(req.headId());
        d.setActive(true);
        d.setProduction(Boolean.TRUE.equals(req.isProduction()));
        repo.save(d);
        audit.emit("department", String.valueOf(d.getId()), "created", null, snapshot(d), false);
        return DepartmentDtoMapper.of(d);
    }

    @Transactional
    public DepartmentDto update(long id, UpdateDepartmentRequest req) {
        Department d = load(id);
        Map<String, Object> before = snapshot(d);
        if (req.name() != null && !req.name().isBlank()) d.setName(req.name());
        if (req.headId() != null) d.setHeadId(req.headId() == 0 ? null : req.headId());
        if (req.active() != null) d.setActive(req.active());
        if (req.isProduction() != null) d.setProduction(req.isProduction());
        audit.emit("department", String.valueOf(d.getId()), "updated", before, snapshot(d), false);
        return DepartmentDtoMapper.of(d);
    }

    @Transactional(readOnly = true)
    public Department loadActive(long id) {
        Department d = load(id);
        if (!d.isActive()) {
            throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "department is inactive");
        }
        return d;
    }

    private Department load(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.DEPARTMENT_NOT_FOUND, "department not found"));
    }

    private static Map<String, Object> snapshot(Department d) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", d.getCode());
        m.put("name", d.getName());
        m.put("headId", d.getHeadId());
        m.put("active", d.isActive());
        m.put("isProduction", d.isProduction());
        return m;
    }
}

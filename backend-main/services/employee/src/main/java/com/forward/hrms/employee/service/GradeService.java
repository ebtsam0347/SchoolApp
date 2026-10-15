package com.forward.hrms.employee.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.CreateGradeRequest;
import com.forward.hrms.employee.dto.request.ReorderEntryRequest;
import com.forward.hrms.employee.dto.request.UpdateGradeRequest;
import com.forward.hrms.employee.dto.response.GradeDto;
import com.forward.hrms.employee.dto.response.GradeDtoMapper;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.GradeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GradeService {

    private final GradeRepository repo;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<GradeDto> list(Boolean active) {
        List<Grade> rows = (active == null)
                ? repo.findAllByOrderByLevelAsc()
                : repo.findAllByActiveOrderByLevelAsc(active);
        return rows.stream().map(GradeDtoMapper::of).toList();
    }

    @Transactional
    public GradeDto create(CreateGradeRequest req) {
        if (repo.existsByCode(req.code())) {
            throw new ConflictError(EmployeeErrorCode.GRADE_CODE_TAKEN, "grade code already in use");
        }
        Grade g = new Grade();
        g.setCode(req.code());
        g.setName(req.name());
        g.setLevel(req.level());
        g.setDescription(req.description());
        if (req.gradeType() != null) g.setGradeType(req.gradeType());
        g.setActive(true);
        repo.save(g);
        audit.emit("grade", String.valueOf(g.getId()), "created", null, snapshot(g), false);
        return GradeDtoMapper.of(g);
    }

    @Transactional
    public GradeDto update(long id, UpdateGradeRequest req) {
        Grade g = load(id);
        Map<String, Object> before = snapshot(g);
        if (req.name() != null && !req.name().isBlank()) g.setName(req.name());
        if (req.level() != null) g.setLevel(req.level());
        if (req.description() != null) g.setDescription(req.description());
        if (req.active() != null) g.setActive(req.active());
        if (req.gradeType() != null) g.setGradeType(req.gradeType());
        audit.emit("grade", String.valueOf(g.getId()), "updated", before, snapshot(g), false);
        return GradeDtoMapper.of(g);
    }

    @Transactional
    public List<GradeDto> reorder(List<ReorderEntryRequest> entries) {
        if (entries == null || entries.isEmpty()) return list(null);
        Set<Long> ids = new HashSet<>();
        for (ReorderEntryRequest e : entries) ids.add(e.id());
        List<Grade> rows = repo.findAllById(ids);
        if (rows.size() != ids.size()) {
            throw new NotFoundError(EmployeeErrorCode.GRADE_NOT_FOUND, "one or more grades not found");
        }
        Map<Long, Grade> byId = new HashMap<>();
        for (Grade g : rows) byId.put(g.getId(), g);
        for (ReorderEntryRequest e : entries) {
            Grade g = byId.get(e.id());
            if (g.getDisplayOrder() == e.displayOrder()) continue;
            Map<String, Object> before = snapshot(g);
            g.setDisplayOrder(e.displayOrder());
            audit.emit("grade", String.valueOf(g.getId()), "reordered", before, snapshot(g), false);
        }
        return list(null);
    }

    @Transactional(readOnly = true)
    public Grade loadActive(long id) {
        Grade g = load(id);
        if (!g.isActive()) {
            throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "grade is inactive");
        }
        return g;
    }

    private Grade load(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.GRADE_NOT_FOUND, "grade not found"));
    }

    private static Map<String, Object> snapshot(Grade g) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", g.getCode());
        m.put("name", g.getName());
        m.put("level", g.getLevel());
        m.put("description", g.getDescription());
        m.put("active", g.isActive());
        m.put("displayOrder", g.getDisplayOrder());
        m.put("gradeType", g.getGradeType().name());
        return m;
    }
}

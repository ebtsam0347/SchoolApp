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
import com.forward.hrms.employee.dto.request.CreateQualificationRequest;
import com.forward.hrms.employee.dto.request.ReorderEntryRequest;
import com.forward.hrms.employee.dto.request.UpdateQualificationRequest;
import com.forward.hrms.employee.dto.response.QualificationDto;
import com.forward.hrms.employee.dto.response.QualificationDtoMapper;
import com.forward.hrms.employee.entity.Qualification;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.QualificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QualificationService {

    private final QualificationRepository repo;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<QualificationDto> list(String level, Boolean active) {
        List<Qualification> rows;
        if (level != null && active != null) {
            rows = repo.findAllByLevelAndActiveOrderByNameAsc(level, active);
        } else if (level != null) {
            rows = repo.findAllByLevelOrderByNameAsc(level);
        } else if (active != null) {
            rows = repo.findAllByActiveOrderByNameAsc(active);
        } else {
            rows = repo.findAllByOrderByNameAsc();
        }
        return rows.stream().map(QualificationDtoMapper::of).toList();
    }

    @Transactional
    public QualificationDto create(CreateQualificationRequest req) {
        if (repo.existsByCode(req.code())) {
            throw new ConflictError(EmployeeErrorCode.QUALIFICATION_CODE_TAKEN,
                    "qualification code already in use");
        }
        Qualification q = new Qualification();
        q.setCode(req.code());
        q.setName(req.name());
        q.setLevel(req.level());
        q.setActive(true);
        repo.save(q);
        audit.emit("qualification", String.valueOf(q.getId()), "created", null, snapshot(q), false);
        return QualificationDtoMapper.of(q);
    }

    @Transactional
    public QualificationDto update(long id, UpdateQualificationRequest req) {
        Qualification q = load(id);
        Map<String, Object> before = snapshot(q);
        if (req.name() != null && !req.name().isBlank()) q.setName(req.name());
        if (req.level() != null && !req.level().isBlank()) q.setLevel(req.level());
        if (req.active() != null) q.setActive(req.active());
        audit.emit("qualification", String.valueOf(q.getId()), "updated", before, snapshot(q), false);
        return QualificationDtoMapper.of(q);
    }

    @Transactional
    public List<QualificationDto> reorder(List<ReorderEntryRequest> entries) {
        if (entries == null || entries.isEmpty()) return list(null, null);
        Set<Long> ids = new HashSet<>();
        for (ReorderEntryRequest e : entries) ids.add(e.id());
        List<Qualification> rows = repo.findAllById(ids);
        if (rows.size() != ids.size()) {
            throw new NotFoundError(EmployeeErrorCode.QUALIFICATION_NOT_FOUND,
                    "one or more qualifications not found");
        }
        Map<Long, Qualification> byId = new HashMap<>();
        for (Qualification q : rows) byId.put(q.getId(), q);
        for (ReorderEntryRequest e : entries) {
            Qualification q = byId.get(e.id());
            if (q.getDisplayOrder() == e.displayOrder()) continue;
            Map<String, Object> before = snapshot(q);
            q.setDisplayOrder(e.displayOrder());
            audit.emit("qualification", String.valueOf(q.getId()), "reordered", before, snapshot(q), false);
        }
        return list(null, null);
    }

    @Transactional(readOnly = true)
    public Qualification loadActive(long id) {
        Qualification q = load(id);
        if (!q.isActive()) {
            throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "qualification is inactive");
        }
        return q;
    }

    private Qualification load(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.QUALIFICATION_NOT_FOUND, "qualification not found"));
    }

    private static Map<String, Object> snapshot(Qualification q) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", q.getCode());
        m.put("name", q.getName());
        m.put("level", q.getLevel());
        m.put("active", q.isActive());
        m.put("displayOrder", q.getDisplayOrder());
        return m;
    }
}

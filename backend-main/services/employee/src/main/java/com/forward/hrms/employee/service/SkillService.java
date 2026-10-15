package com.forward.hrms.employee.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.CreateSkillRequest;
import com.forward.hrms.employee.dto.request.UpdateSkillRequest;
import com.forward.hrms.employee.dto.response.SkillDto;
import com.forward.hrms.employee.dto.response.SkillDtoMapper;
import com.forward.hrms.employee.entity.Skill;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.SkillRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository repo;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<SkillDto> list(String category, Boolean active) {
        List<Skill> rows;
        if (category != null && active != null) {
            rows = repo.findAllByCategoryAndActiveOrderByNameAsc(category, active);
        } else if (category != null) {
            rows = repo.findAllByCategoryOrderByNameAsc(category);
        } else if (active != null) {
            rows = repo.findAllByActiveOrderByNameAsc(active);
        } else {
            rows = repo.findAllByOrderByNameAsc();
        }
        return rows.stream().map(SkillDtoMapper::of).toList();
    }

    @Transactional
    public SkillDto create(CreateSkillRequest req) {
        if (repo.existsByCode(req.code())) {
            throw new ConflictError(EmployeeErrorCode.SKILL_CODE_TAKEN, "skill code already in use");
        }
        Skill s = new Skill();
        s.setCode(req.code());
        s.setName(req.name());
        s.setCategory(req.category());
        s.setActive(true);
        repo.save(s);
        audit.emit("skill", String.valueOf(s.getId()), "created", null, snapshot(s), false);
        return SkillDtoMapper.of(s);
    }

    @Transactional
    public SkillDto update(long id, UpdateSkillRequest req) {
        Skill s = load(id);
        Map<String, Object> before = snapshot(s);
        if (req.name() != null && !req.name().isBlank()) s.setName(req.name());
        if (req.category() != null) s.setCategory(req.category().isBlank() ? null : req.category());
        if (req.active() != null) s.setActive(req.active());
        audit.emit("skill", String.valueOf(s.getId()), "updated", before, snapshot(s), false);
        return SkillDtoMapper.of(s);
    }

    @Transactional(readOnly = true)
    public Skill loadActive(long id) {
        Skill s = load(id);
        if (!s.isActive()) {
            throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "skill is inactive");
        }
        return s;
    }

    private Skill load(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.SKILL_NOT_FOUND, "skill not found"));
    }

    private static Map<String, Object> snapshot(Skill s) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", s.getCode());
        m.put("name", s.getName());
        m.put("category", s.getCategory());
        m.put("active", s.isActive());
        return m;
    }
}

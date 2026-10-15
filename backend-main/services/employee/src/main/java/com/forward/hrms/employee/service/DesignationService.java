package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.CreateDesignationRequest;
import com.forward.hrms.employee.dto.request.UpdateDesignationRequest;
import com.forward.hrms.employee.dto.response.DesignationDto;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.dto.response.DesignationDtoMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DesignationService {

    private final DesignationRepository repo;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<DesignationDto> list(Boolean active) {
        List<Designation> rows = (active == null)
                ? repo.findAllByOrderByTitleAsc()
                : repo.findAllByActiveOrderByTitleAsc(active);
        return rows.stream().map(DesignationDtoMapper::of).toList();
    }

    @Transactional
    public DesignationDto create(CreateDesignationRequest req) {
        if (repo.existsByCode(req.code())) {
            throw new ConflictError(EmployeeErrorCode.DESIGNATION_CODE_TAKEN, "designation code already in use");
        }
        Designation d = new Designation();
        d.setCode(req.code());
        d.setTitle(req.title());
        d.setCategory(req.category());
        d.setActive(true);
        repo.save(d);
        audit.emit("designation", String.valueOf(d.getId()), "created", null, snapshot(d), false);
        return DesignationDtoMapper.of(d);
    }

    @Transactional
    public DesignationDto update(long id, UpdateDesignationRequest req) {
        Designation d = load(id);
        Map<String, Object> before = snapshot(d);
        if (req.title() != null && !req.title().isBlank()) d.setTitle(req.title());
        if (req.category() != null) d.setCategory(req.category());
        if (req.active() != null) d.setActive(req.active());
        audit.emit("designation", String.valueOf(d.getId()), "updated", before, snapshot(d), false);
        return DesignationDtoMapper.of(d);
    }

    @Transactional(readOnly = true)
    public Designation loadActive(long id) {
        Designation d = load(id);
        if (!d.isActive()) {
            throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "designation is inactive");
        }
        return d;
    }

    private Designation load(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.DESIGNATION_NOT_FOUND, "designation not found"));
    }

    private static Map<String, Object> snapshot(Designation d) {
        Map<String, Object> snap = new java.util.LinkedHashMap<>();
        snap.put("code", d.getCode());
        snap.put("title", d.getTitle());
        snap.put("category", d.getCategory() == null ? null : d.getCategory().name());
        snap.put("active", d.isActive());
        return snap;
    }
}

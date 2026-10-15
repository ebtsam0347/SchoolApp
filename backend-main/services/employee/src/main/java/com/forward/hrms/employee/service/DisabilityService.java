package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.CreateDisabilityRequest;
import com.forward.hrms.employee.dto.request.UpdateDisabilityRequest;
import com.forward.hrms.employee.dto.response.DisabilityDto;
import com.forward.hrms.employee.entity.Disability;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.DisabilityRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DisabilityService {

    private final DisabilityRepository repo;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<DisabilityDto> list(boolean includeInactive) {
        List<Disability> rows = includeInactive
                ? repo.findAllByOrderByCodeAsc()
                : repo.findAllByActiveTrueOrderByCodeAsc();
        return rows.stream().map(DisabilityDto::of).toList();
    }

    @Transactional
    public DisabilityDto create(CreateDisabilityRequest req) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (repo.existsByCode(code)) {
            throw new ConflictError(EmployeeErrorCode.DISABILITY_CODE_TAKEN,
                    "disability code already exists: " + code);
        }
        Disability d = new Disability();
        d.setCode(code);
        d.setName(req.name().trim());
        d.setActive(true);
        repo.save(d);
        audit.emit("disability", String.valueOf(d.getId()), "created",
                null, snapshot(d), false);
        return DisabilityDto.of(d);
    }

    @Transactional
    public DisabilityDto update(long id, UpdateDisabilityRequest req) {
        Disability d = load(id);
        Map<String, Object> before = snapshot(d);
        if (req.name() != null && !req.name().isBlank()) d.setName(req.name().trim());
        if (req.active() != null) d.setActive(req.active());
        audit.emit("disability", String.valueOf(d.getId()), "updated",
                before, snapshot(d), false);
        return DisabilityDto.of(d);
    }

    @Transactional
    public DisabilityDto deactivate(long id) {
        Disability d = load(id);
        Map<String, Object> before = snapshot(d);
        d.setActive(false);
        audit.emit("disability", String.valueOf(d.getId()), "deactivated",
                before, snapshot(d), false);
        return DisabilityDto.of(d);
    }

    @Transactional(readOnly = true)
    public Disability requireActive(long id) {
        Disability d = load(id);
        if (!d.isActive()) {
            throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "disability is inactive");
        }
        return d;
    }

    private Disability load(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.DISABILITY_NOT_FOUND,
                        "disability not found"));
    }

    private static Map<String, Object> snapshot(Disability d) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", d.getCode());
        m.put("name", d.getName());
        m.put("active", d.isActive());
        return m;
    }
}

package com.forward.hrms.employee.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.CreateProvinceRequest;
import com.forward.hrms.employee.dto.request.UpdateProvinceRequest;
import com.forward.hrms.employee.dto.response.ProvinceDto;
import com.forward.hrms.employee.dto.response.ProvinceDtoMapper;
import com.forward.hrms.employee.entity.Province;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.ProvinceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProvinceService {

    private final ProvinceRepository repo;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<ProvinceDto> list(Boolean active) {
        List<Province> rows = (active == null)
                ? repo.findAllByOrderByNameAsc()
                : repo.findAllByActiveOrderByNameAsc(active);
        return rows.stream().map(ProvinceDtoMapper::of).toList();
    }

    @Transactional
    public ProvinceDto create(CreateProvinceRequest req) {
        if (repo.existsByCode(req.code())) {
            throw new ConflictError(EmployeeErrorCode.PROVINCE_CODE_TAKEN, "province code already in use");
        }
        Province p = new Province();
        p.setCode(req.code());
        p.setName(req.name());
        p.setActive(true);
        repo.save(p);
        audit.emit("province", String.valueOf(p.getId()), "created", null, snapshot(p), false);
        return ProvinceDtoMapper.of(p);
    }

    @Transactional
    public ProvinceDto update(long id, UpdateProvinceRequest req) {
        Province p = load(id);
        Map<String, Object> before = snapshot(p);
        if (req.name() != null && !req.name().isBlank()) p.setName(req.name());
        if (req.active() != null) p.setActive(req.active());
        audit.emit("province", String.valueOf(p.getId()), "updated", before, snapshot(p), false);
        return ProvinceDtoMapper.of(p);
    }

    @Transactional(readOnly = true)
    public Province loadActive(long id) {
        Province p = load(id);
        if (!p.isActive()) {
            throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "province is inactive");
        }
        return p;
    }

    private Province load(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.PROVINCE_NOT_FOUND, "province not found"));
    }

    private static Map<String, Object> snapshot(Province p) {
        return Map.of("code", p.getCode(), "name", p.getName(), "active", p.isActive());
    }
}

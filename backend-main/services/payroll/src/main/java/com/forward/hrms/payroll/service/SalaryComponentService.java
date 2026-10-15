package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.payroll.dto.request.CreateSalaryComponentRequest;
import com.forward.hrms.payroll.dto.request.ReorderEntryRequest;
import com.forward.hrms.payroll.dto.request.UpdateSalaryComponentRequest;
import com.forward.hrms.payroll.dto.response.SalaryComponentDto;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;
import com.forward.hrms.payroll.dto.response.SalaryComponentDtoMapper;

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
public class SalaryComponentService {

    private final SalaryComponentRepository repo;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<SalaryComponentDto> list(ComponentType type, Boolean active) {
        return repo.filter(type, active).stream().map(SalaryComponentDtoMapper::of).toList();
    }

    @Transactional(readOnly = true)
    public SalaryComponent loadActiveByCode(String code) {
        SalaryComponent c = repo.findByCode(code).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.COMPONENT_NOT_FOUND,
                        "component " + code + " not found"));
        if (!c.isActive()) {
            throw new ConflictError(PayrollErrorCode.COMPONENT_INACTIVE,
                    "component " + code + " is inactive");
        }
        return c;
    }

    @Transactional(readOnly = true)
    public SalaryComponent load(long id) {
        return repo.findById(id).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.COMPONENT_NOT_FOUND, "component not found"));
    }

    @Transactional
    public SalaryComponentDto create(CreateSalaryComponentRequest req) {
        if (repo.existsByCode(req.code())) {
            throw new ConflictError(PayrollErrorCode.COMPONENT_CODE_TAKEN,
                    "code " + req.code() + " already in use");
        }
        CalcType calcType = req.calcType() == null ? CalcType.FLAT : req.calcType();
        if (calcType == CalcType.PER_DAY && req.type() == ComponentType.DEDUCTION) {
            throw new ConflictError(PayrollErrorCode.COMPONENT_INVALID_CALC_TYPE,
                    "PER_DAY only allowed on EARNING components");
        }
        SalaryComponent c = new SalaryComponent();
        c.setCode(req.code());
        c.setName(req.name());
        c.setType(req.type());
        c.setCalcType(calcType);
        c.setDefaultAmount(req.defaultAmount());
        c.setDisplayOrder(req.displayOrder() == null ? 0 : req.displayOrder());
        c.setActive(true);
        c.setCalculateWithSalary(req.calculateWithSalary());
        c = repo.save(c);
        audit.emit("salary_component", String.valueOf(c.getId()), "created", null, snapshot(c), false);
        return SalaryComponentDtoMapper.of(c);
    }

    @Transactional
    public SalaryComponentDto update(long id, UpdateSalaryComponentRequest req) {
        SalaryComponent c = load(id);
        Map<String, Object> before = snapshot(c);
        if (req.name() != null) c.setName(req.name());
        if (req.defaultAmount() != null) c.setDefaultAmount(req.defaultAmount());
        if (req.displayOrder() != null) c.setDisplayOrder(req.displayOrder());
        if (req.active() != null) c.setActive(req.active());
        if(req.calculateWithSalary() != null) c.setCalculateWithSalary(req.calculateWithSalary());
        audit.emit("salary_component", String.valueOf(c.getId()), "updated", before, snapshot(c), false);
        return SalaryComponentDtoMapper.of(c);
    }

    @Transactional
    public List<SalaryComponentDto> reorder(List<ReorderEntryRequest> entries) {
        if (entries == null || entries.isEmpty()) return list(null, null);
        Set<Long> ids = new HashSet<>();
        for (ReorderEntryRequest e : entries) ids.add(e.id());
        List<SalaryComponent> rows = repo.findAllById(ids);
        if (rows.size() != ids.size()) {
            throw new NotFoundError(PayrollErrorCode.COMPONENT_NOT_FOUND,
                    "one or more components not found");
        }
        Map<Long, SalaryComponent> byId = new HashMap<>();
        for (SalaryComponent c : rows) byId.put(c.getId(), c);
        for (ReorderEntryRequest e : entries) {
            SalaryComponent c = byId.get(e.id());
            if (c.getDisplayOrder() == e.displayOrder()) continue;
            Map<String, Object> before = snapshot(c);
            c.setDisplayOrder(e.displayOrder());
            audit.emit("salary_component", String.valueOf(c.getId()), "reordered", before, snapshot(c), false);
        }
        return list(null, null);
    }

    private static Map<String, Object> snapshot(SalaryComponent c) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", c.getCode());
        m.put("name", c.getName());
        m.put("type", c.getType().name());
        m.put("calcType", c.getCalcType().name());
        m.put("active", c.isActive());
        m.put("displayOrder", c.getDisplayOrder());
        return m;
    }
}

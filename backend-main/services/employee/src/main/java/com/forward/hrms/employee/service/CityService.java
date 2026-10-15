package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.CreateCityRequest;
import com.forward.hrms.employee.dto.request.ReorderEntryRequest;
import com.forward.hrms.employee.dto.request.UpdateCityRequest;
import com.forward.hrms.employee.dto.response.CityDto;
import com.forward.hrms.employee.entity.City;
import com.forward.hrms.employee.entity.Province;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.CityRepository;
import com.forward.hrms.employee.repository.ProvinceRepository;
import com.forward.hrms.employee.dto.response.CityDtoMapper;

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
public class CityService {

    private final CityRepository repo;
    private final ProvinceRepository provinces;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<CityDto> list(Long provinceId, Boolean active) {
        List<City> rows;
        if (provinceId != null && active != null) {
            rows = repo.findAllByProvinceIdAndActiveOrderByNameAsc(provinceId, active);
        } else if (provinceId != null) {
            rows = repo.findAllByProvinceIdOrderByNameAsc(provinceId);
        } else if (active != null) {
            rows = repo.findAllByActiveOrderByNameAsc(active);
        } else {
            rows = repo.findAllByOrderByNameAsc();
        }
        return rows.stream().map(CityDtoMapper::of).toList();
    }

    @Transactional
    public CityDto create(CreateCityRequest req) {
        if (repo.existsByCode(req.code())) {
            throw new ConflictError(EmployeeErrorCode.CITY_CODE_TAKEN, "city code already in use");
        }
        Province province = provinces.findById(req.provinceId())
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.PROVINCE_NOT_FOUND, "province not found"));
        if (!province.isActive()) {
            throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "province is inactive");
        }
        City c = new City();
        c.setCode(req.code());
        c.setName(req.name());
        c.setProvince(province);
        c.setActive(true);
        repo.save(c);
        audit.emit("city", String.valueOf(c.getId()), "created", null, snapshot(c), false);
        return CityDtoMapper.of(c);
    }

    @Transactional
    public CityDto update(long id, UpdateCityRequest req) {
        City c = load(id);
        Map<String, Object> before = snapshot(c);
        if (req.name() != null && !req.name().isBlank()) c.setName(req.name());
        if (req.provinceId() != null && !req.provinceId().equals(c.getProvince().getId())) {
            Province province = provinces.findById(req.provinceId())
                    .orElseThrow(() -> new NotFoundError(
                            EmployeeErrorCode.PROVINCE_NOT_FOUND, "province not found"));
            if (!province.isActive()) {
                throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "province is inactive");
            }
            c.setProvince(province);
        }
        if (req.active() != null) c.setActive(req.active());
        audit.emit("city", String.valueOf(c.getId()), "updated", before, snapshot(c), false);
        return CityDtoMapper.of(c);
    }

    @Transactional
    public List<CityDto> reorder(List<ReorderEntryRequest> entries) {
        if (entries == null || entries.isEmpty()) return list(null, null);
        Set<Long> ids = new HashSet<>();
        for (ReorderEntryRequest e : entries) ids.add(e.id());
        List<City> rows = repo.findAllById(ids);
        if (rows.size() != ids.size()) {
            throw new NotFoundError(EmployeeErrorCode.CITY_NOT_FOUND, "one or more cities not found");
        }
        Map<Long, City> byId = new HashMap<>();
        for (City c : rows) byId.put(c.getId(), c);
        for (ReorderEntryRequest e : entries) {
            City c = byId.get(e.id());
            if (c.getDisplayOrder() == e.displayOrder()) continue;
            Map<String, Object> before = snapshot(c);
            c.setDisplayOrder(e.displayOrder());
            audit.emit("city", String.valueOf(c.getId()), "reordered", before, snapshot(c), false);
        }
        return list(null, null);
    }

    @Transactional(readOnly = true)
    public City loadActive(long id) {
        City c = load(id);
        if (!c.isActive()) {
            throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "city is inactive");
        }
        return c;
    }

    private City load(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.CITY_NOT_FOUND, "city not found"));
    }

    private static Map<String, Object> snapshot(City c) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", c.getCode());
        m.put("name", c.getName());
        m.put("provinceId", c.getProvince().getId());
        m.put("active", c.isActive());
        m.put("displayOrder", c.getDisplayOrder());
        return m;
    }
}

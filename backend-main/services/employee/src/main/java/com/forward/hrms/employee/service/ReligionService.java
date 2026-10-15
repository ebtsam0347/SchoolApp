package com.forward.hrms.employee.service;

import java.util.List;
import java.util.Map;

import com.forward.hrms.employee.dto.request.ReligionRequest;
import com.forward.hrms.employee.dto.request.UpdateReligionRequest;
import com.forward.hrms.employee.dto.response.ReligionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.response.ReligionDtoMapper;
import com.forward.hrms.employee.entity.Religion;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.ReligionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReligionService {

    private final ReligionRepository repo;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<ReligionDto> list(Boolean active) {
        List<Religion> rows = (active == null)
                ? repo.findAllByOrderByNameAsc()
                : repo.findAllByActiveOrderByNameAsc(active);

        return rows.stream()
                .map(ReligionDtoMapper::of)
                .toList();
    }

    @Transactional
    public ReligionDto create(ReligionRequest req) {

        if (repo.existsByNameIgnoreCase(req.name())) {
            throw new ConflictError(
                    EmployeeErrorCode.RELIGION_NAME_TAKEN,
                    "religion name already in use"
            );
        }

        Religion religion = new Religion();
        religion.setName(req.name());
        religion.setActive(true);

        repo.save(religion);

        audit.emit(
                "religion",
                String.valueOf(religion.getId()),
                "created",
                null,
                snapshot(religion),
                false
        );

        return ReligionDtoMapper.of(religion);
    }

    @Transactional
    public ReligionDto update(long id, UpdateReligionRequest req) {

        Religion religion = load(id);

        Map<String, Object> before = snapshot(religion);

        if (req.name() != null && !req.name().isBlank()) {
            religion.setName(req.name());
        }

        if (req.active() != null) {
            religion.setActive(req.active());
        }

        audit.emit(
                "religion",
                String.valueOf(religion.getId()),
                "updated",
                before,
                snapshot(religion),
                false
        );

        return ReligionDtoMapper.of(religion);
    }

    @Transactional(readOnly = true)
    public Religion loadActive(long id) {

        Religion religion = load(id);

        if (!Boolean.TRUE.equals(religion.getActive())) {
            throw new ConflictError(
                    EmployeeErrorCode.CATALOG_INACTIVE,
                    "religion is inactive"
            );
        }

        return religion;
    }

    private Religion load(long id) {
        return repo.findById(id)
                .orElseThrow(() ->
                        new NotFoundError(
                                EmployeeErrorCode.RELIGION_NOT_FOUND,
                                "religion not found"
                        ));
    }

    private static Map<String, Object> snapshot(Religion religion) {
        return Map.of(
                "name", religion.getName(),
                "active", religion.getActive()
        );
    }
}
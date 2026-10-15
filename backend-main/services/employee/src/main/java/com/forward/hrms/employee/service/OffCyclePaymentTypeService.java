package com.forward.hrms.employee.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.OffCyclePaymentTypeRequest;
import com.forward.hrms.employee.dto.request.UpdateOffCyclePaymentType;
import com.forward.hrms.employee.dto.response.OffCyclePaymentTypeDto;
import com.forward.hrms.employee.dto.response.OffCyclePaymentTypeDtoMapper;
import com.forward.hrms.employee.entity.OffCyclePaymentType;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.OffCyclePaymentTypeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OffCyclePaymentTypeService {

    private final OffCyclePaymentTypeRepository repo;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<OffCyclePaymentTypeDto> list(Boolean active) {
        List<OffCyclePaymentType> rows = (active == null)
                ? repo.findAllByOrderByNameAsc()
                : repo.findAllByActiveOrderByNameAsc(active);

        return rows.stream()
                .map(OffCyclePaymentTypeDtoMapper::of)
                .toList();
    }

    @Transactional
    public OffCyclePaymentTypeDto create(OffCyclePaymentTypeRequest req) {

        if (repo.existsByNameIgnoreCase(req.name())) {
            throw new ConflictError(
                    EmployeeErrorCode.OFF_CYCLE_PAYMENT_TYPE_NAME_TAKEN,
                    "off cycle payment type name already in use");
        }

        OffCyclePaymentType entity = OffCyclePaymentType.builder()
                .name(req.name())
                .description(req.description())
                // A creation that omits `active` means "active" — passing the
                // null straight through hit the NOT NULL column as a 500
                // rather than a new, selectable type.
                .active(req.active() == null ? Boolean.TRUE : req.active())
                .build();

        repo.save(entity);

        audit.emit(
                "off_cycle_payment_type",
                String.valueOf(entity.getId()),
                "created",
                null,
                snapshot(entity),
                false
        );

        return OffCyclePaymentTypeDtoMapper.of(entity);
    }

    @Transactional
    public OffCyclePaymentTypeDto update(long id, UpdateOffCyclePaymentType req) {

        OffCyclePaymentType entity = load(id);

        Map<String, Object> before = snapshot(entity);

        if (req.name() != null && !req.name().isBlank()) {
            entity.setName(req.name());
        }

        if (req.description() != null) {
            entity.setDescription(req.description());
        }

        if (req.active() != null) {
            entity.setActive(req.active());
        }

        audit.emit(
                "off_cycle_payment_type",
                String.valueOf(entity.getId()),
                "updated",
                before,
                snapshot(entity),
                false
        );

        return OffCyclePaymentTypeDtoMapper.of(entity);
    }

    private OffCyclePaymentType load(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.OFF_CYCLE_PAYMENT_TYPE_NOT_FOUND,
                        "off cycle payment type not found"));
    }

    private static Map<String, Object> snapshot(OffCyclePaymentType entity) {
        return Map.of(
                "name", entity.getName(),
                "description", entity.getDescription() == null ? "" : entity.getDescription(),
                "active", entity.getActive()
        );
    }
}
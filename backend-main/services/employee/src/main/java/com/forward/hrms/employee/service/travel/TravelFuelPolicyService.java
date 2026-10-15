package com.forward.hrms.employee.service.travel;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelFuelPolicyRequest;
import com.forward.hrms.employee.dto.request.travel.response.TravelFuelPolicyResponse;
import com.forward.hrms.employee.entity.travel.TravelFuelPolicy;
import com.forward.hrms.employee.enums.travel.TravelErrorCode;
import com.forward.hrms.employee.enums.travel.VehicleSource;
import com.forward.hrms.employee.repository.travel.TravelFuelPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Administration of Transport fuel/petrol issuance policies (master
 * data). {@code code} IS the vehicle-source code (COMPANY, EMPLOYEE,
 * RENTAL, PUBLIC, OTHER) — the sole vocabulary Transport verification
 * resolves against, no separate identifier. Every mutation emits an
 * audit row per the catalog-admin doctrine.
 */
@Service
@RequiredArgsConstructor
public class TravelFuelPolicyService {

    private final TravelFuelPolicyRepository policies;
    private final AuditEmitter audit;

    @Transactional
    public TravelFuelPolicyResponse create(CreateTravelFuelPolicyRequest req) {
        if (policies.existsByCodeAndIsActiveTrue(req.code())) {
            throw new ConflictError(TravelErrorCode.TRAVEL_FUEL_POLICY_DUPLICATE,
                    "a fuel policy with code " + req.code() + " already exists");
        }

        requireValidVehicleSourceCode(req.code());

        TravelFuelPolicy p = new TravelFuelPolicy();
        p.setCode(req.code());
        p.setName(req.name());
        p.setFuelAverage(req.fuelAverage());
        p.setEffectiveFrom(req.effectiveFrom());
        p.setEffectiveTo(null);
        p.setIsActive(req.isActive());
        policies.save(p);

        audit.emit("travel_fuel_policy", String.valueOf(p.getId()), "created", null, snapshot(p), false);
        return TravelFuelPolicyResponse.of(p);
    }

    @Transactional(readOnly = true)
    public TravelFuelPolicyResponse get(Long id) {
        return TravelFuelPolicyResponse.of(require(id));
    }

    @Transactional(readOnly = true)
    public Page<TravelFuelPolicyResponse> search(String code, Boolean isActive, Pageable pageable) {
        return policies.search(code, isActive, pageable).map(TravelFuelPolicyResponse::of);
    }

    @Transactional
    public TravelFuelPolicyResponse update(Long id, CreateTravelFuelPolicyRequest req) {
        requireValidVehicleSourceCode(req.code());

        TravelFuelPolicy p = require(id);
        if (!p.getCode().equals(req.code()) && policies.existsByCodeAndIsActiveTrue(req.code())) {
            throw new ConflictError(TravelErrorCode.TRAVEL_FUEL_POLICY_DUPLICATE,
                    "a fuel policy with code " + req.code() + " already exists");
        }
        Map<String, Object> before = snapshot(p);

        p.setCode(req.code());
        p.setName(req.name());
        p.setFuelAverage(req.fuelAverage());
        p.setEffectiveFrom(req.effectiveFrom());
        p.setEffectiveTo(req.effectiveTo());
        p.setIsActive(req.isActive());

        audit.emit("travel_fuel_policy", String.valueOf(p.getId()), "updated", before, snapshot(p), false);
        return TravelFuelPolicyResponse.of(p);
    }

    @Transactional
    public TravelFuelPolicyResponse deactivate(Long id) {
        TravelFuelPolicy p = require(id);
        Map<String, Object> before = snapshot(p);
        p.setIsActive(false);
        p.setEffectiveTo(LocalDate.now());
        audit.emit("travel_fuel_policy", String.valueOf(p.getId()), "deactivated", before, snapshot(p), false);
        return TravelFuelPolicyResponse.of(p);
    }

    @Transactional
    public TravelFuelPolicyResponse activate(Long id) {
        TravelFuelPolicy p = require(id);
        Map<String, Object> before = snapshot(p);
        p.setIsActive(true);
        p.setEffectiveTo(null);
        audit.emit("travel_fuel_policy", String.valueOf(p.getId()), "activated", before, snapshot(p), false);
        return TravelFuelPolicyResponse.of(p);
    }

    private TravelFuelPolicy require(Long id) {
        return policies.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        TravelErrorCode.TRAVEL_FUEL_POLICY_NOT_FOUND, "travel fuel policy not found"));
    }

    /**
     * {@code code} IS the vehicle-source code — must be a real
     * {@link VehicleSource} value. There is no other vehicle-source
     * vocabulary; an unknown code is rejected outright rather than
     * silently accepted as a free-form identifier.
     */
    private static void requireValidVehicleSourceCode(String code) {
        try {
            VehicleSource.valueOf(code);
        } catch (IllegalArgumentException ex) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_INVALID_VEHICLE_SOURCE,
                    "unknown vehicle source code: " + code);
        }
    }

    private static Map<String, Object> snapshot(TravelFuelPolicy p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", p.getCode());
        m.put("name", p.getName());
        m.put("fuelAverage", String.valueOf(p.getFuelAverage()));
        m.put("effectiveFrom", String.valueOf(p.getEffectiveFrom()));
        m.put("effectiveTo", String.valueOf(p.getEffectiveTo()));
        m.put("isActive", p.getIsActive());
        return m;
    }
}

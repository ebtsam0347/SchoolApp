package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.BelongingClearanceRequest;
import com.forward.hrms.employee.dto.request.BelongingIssuanceRequest;
import com.forward.hrms.employee.dto.response.BelongingClearanceCheckDto;
import com.forward.hrms.employee.dto.response.BelongingIssuanceResponse;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.entity.BelongingIssuance;
import com.forward.hrms.employee.entity.BelongingMaster;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.belonging.ActivationStatus;
import com.forward.hrms.employee.enums.belonging.ClearanceStatus;
import com.forward.hrms.employee.mapper.BelongingMapper;
import com.forward.hrms.employee.repository.BelongingIssuanceRepository;
import com.forward.hrms.employee.repository.BelongingMasterRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class BelongingIssuanceService {

    private final BelongingIssuanceRepository issuanceRepository;
    private final BelongingMasterRepository masterRepository;
    private final EmployeeService employees;
    private final AuditEmitter audit;

    /**
     * Issue belonging
     */
    public BelongingIssuanceResponse issue(BelongingIssuanceRequest request) {

        validateDates(request);
        Employee employee = employees.load(request.employeeId());
        if(employee != null){
            if(employee.getEmploymentStatus() != EmploymentStatus.ACTIVE){
                throw new NotFoundError(EmployeeErrorCode.EMPLOYEE_TERMINATED, "Employee must be ACTIVE to eligible for an asset.");
            }
        }
        BelongingMaster master = getMaster(request.belongingMasterId());
        BelongingIssuance entity = BelongingIssuance.builder()
                .belongingMaster(master)
                .employeeId(request.employeeId())
                .assetModel(request.assetModel())
                .assetYear(request.assetYear())
                .assetPrice(request.assetPrice())
                .issuedDate(request.issuedDate())
                .expiryDate(request.expiryDate())
                .buyBackPercEmployee(request.buyBackPercEmployee())
                .buyBackPercEmployer(request.buyBackPercEmployer())
                .condition(request.condition())
                .activationStatus(request.activationStatus())
                .activationDate(request.activationDate())
                .description(request.description())
                .clearanceStatus(request.clearanceStatus())
                .clearanceId(null)
                .clearanceDate(null)
                .build();

        entity = issuanceRepository.save(entity);
        Map<String, Object> after = snapshot(entity);
        audit.emit("belonging_issuance", String.valueOf(entity.getId()), "created", null, after, false, "belonging issuance created");
        return BelongingMapper.toResponse(entity);
    }

    /**
     * Get issuance by id
     */
    @Transactional
    public BelongingIssuanceResponse getById(Long id) {
        return BelongingMapper.toResponse(getIssuance(id));
    }

    /**
     * Active belongings of employee
     */
    @Transactional
    public List<BelongingIssuanceResponse> getActiveBelongings(Long employeeId) {
        return issuanceRepository
                .findByEmployeeIdAndActivationStatus(
                        employeeId,
                        ActivationStatus.ACTIVE
                )
                .stream()
                .map(BelongingMapper::toResponse)
                .toList();
    }

    /**
     * All active belongings
     */
    @Transactional
    public List<BelongingIssuanceResponse> getAllActiveBelongings() {
        return issuanceRepository
                .findByActivationStatus(
                        ActivationStatus.ACTIVE
                )
                .stream()
                .map(BelongingMapper::toResponse)
                .toList();
    }

    /**
     * Termination clearance verdict for one employee.
     *
     * <p>The gate payroll-service reads before it lets HR approve a final
     * settlement. A belonging blocks when it is still {@code ACTIVE} and its
     * clearance is not {@code APPROVED}:
     *
     * <ul>
     *   <li>{@code RETURNED} / {@code LOST} / {@code DAMAGED} /
     *       {@code INACTIVE} — already out of the employee's hands, settled
     *       through the activation workflow.</li>
     *   <li>{@code clearanceStatus = APPROVED} — the authorized holder
     *       signed it off (buy-back, write-off, waiver), so the asset can
     *       stay ACTIVE without blocking.</li>
     * </ul>
     *
     * <p>The rule lives here, not in payroll: payroll asks for a verdict and
     * renders the list, it does not know what ACTIVE or APPROVED mean.
     * Nothing issued at all returns {@code cleared = true} with an empty
     * list, which is the common case for most terminations.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public BelongingClearanceCheckDto clearanceCheck(Long employeeId) {

        List<BelongingIssuance> blocking = issuanceRepository.findBlockingClearance(
                employeeId,
                ActivationStatus.ACTIVE,
                ClearanceStatus.APPROVED
        );

        if (blocking.isEmpty()) {
            return BelongingClearanceCheckDto.cleared(employeeId);
        }

        List<BelongingClearanceCheckDto.PendingBelonging> pending = blocking.stream()
                .map(BelongingMapper::toPendingBelonging)
                .toList();

        BigDecimal value = blocking.stream()
                .map(BelongingIssuance::getAssetPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BelongingClearanceCheckDto(
                employeeId,
                false,
                pending.size(),
                value,
                pending
        );
    }

    // =====================================================
    // Helpers
    // =====================================================

    private BelongingMaster getMaster(Long id) {

        return masterRepository.findById(id)
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.BELONGING_MASTER_NOT_FOUND, "Belonging Master not found of id: " + id));
    }

    private BelongingIssuance getIssuance(Long id) {

        return issuanceRepository.findById(id)
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.BELONGING_ISSUANCE_NOT_FOUND, "Belonging Issuance not found of id: " + id));
    }

    private void validateDates(BelongingIssuanceRequest request) {

        if (request.issuedDate() != null && request.expiryDate() != null
                && request.expiryDate().isBefore(request.issuedDate())) {

            throw new ConflictError(EmployeeErrorCode.VALIDATION_FAILED, "Expiry date cannot be before issued date.");
        }

        if (request.clearanceDate() != null && request.activationDate() != null
                && request.clearanceDate().isBefore(request.activationDate())) {

            throw new ConflictError(EmployeeErrorCode.VALIDATION_FAILED, "Clearance date cannot be before activation date.");
        }
    }

    /**
     * Get clearance status for all employees
     */
    @Transactional
    public List<BelongingIssuanceResponse> getClearanceStatusForAllEmployees() {

        return issuanceRepository
                .findAllByOrderByEmployeeIdAsc()
                .stream()
                .map(BelongingMapper::toResponse)
                .toList();
    }

    /**
     * Get clearance status by employee
     */
    @Transactional
    public List<BelongingIssuanceResponse> getClearanceStatusByEmployee(
            Long employeeId) {

        return issuanceRepository
                .findByEmployeeIdOrderByIssuedDateDesc(employeeId)
                .stream()
                .map(BelongingMapper::toResponse)
                .toList();
    }

    /**
     * Update employee clearance
     */
    public BelongingIssuanceResponse updateClearance(Long issuanceId, BelongingClearanceRequest request) {
        BelongingIssuance issuance = getIssuance(issuanceId);
        validateClearance(request, issuance);
        issuance.setClearanceStatus(request.clearanceStatus());
        issuance.setClearanceId(request.clearanceId());
        issuance.setClearanceDate(request.clearanceDate());
        Map<String, Object> before = snapshot(issuance);

        issuanceRepository.save(issuance);
        Map<String, Object> after =  snapshot(issuance);
        audit.emit("belonging_issuance", String.valueOf(issuance.getId()), "updated", before, after, false, "belonging issuance clearance updated");
        return BelongingMapper.toResponse(issuance);
    }

    private void validateClearance(BelongingClearanceRequest request, BelongingIssuance issuance) {
        if (request.clearanceDate() != null
                && request.clearanceDate()
                .isBefore(issuance.getActivationDate())) {

            throw new ConflictError(EmployeeErrorCode.VALIDATION_FAILED, "Clearance date cannot be before activation date.");
        }

        if (request.clearanceStatus() == ClearanceStatus.APPROVED && request.clearanceId() == null) {
            throw new ConflictError(EmployeeErrorCode.VALIDATION_FAILED, "Clearance Id is required when clearance is approved.");
        }
    }

    public BelongingIssuanceResponse updateIssuance(Long issuanceId, BelongingIssuanceRequest request) {

        BelongingIssuance issuance = getIssuance(issuanceId);
        validateDates(request);
        Employee employee = employees.load(request.employeeId());
        if(employee != null){
            if(employee.getEmploymentStatus() != EmploymentStatus.ACTIVE){
                throw new NotFoundError(EmployeeErrorCode.EMPLOYEE_TERMINATED, "Employee must be ACTIVE to eligible for an asset.");
            }
        }
        BelongingMaster master = getMaster(request.belongingMasterId());

        issuance.setBelongingMaster(master);
        issuance.setEmployeeId(request.employeeId());
        issuance.setAssetModel(request.assetModel());
        issuance.setAssetYear(request.assetYear());
        issuance.setAssetPrice(request.assetPrice());
        issuance.setIssuedDate(request.issuedDate());
        issuance.setExpiryDate(request.expiryDate());
        issuance.setBuyBackPercEmployee(request.buyBackPercEmployee());
        issuance.setBuyBackPercEmployer(request.buyBackPercEmployer());
        issuance.setCondition(request.condition());
        issuance.setActivationStatus(request.activationStatus());
        issuance.setActivationDate(request.activationDate());
        issuance.setDescription(request.description());
        issuance.setClearanceStatus(request.clearanceStatus());
        issuance.setClearanceId(request.clearanceId());
        issuance.setClearanceDate(request.clearanceDate());
        Map<String, Object> before = snapshot(issuance);

        issuanceRepository.save(issuance);
        Map<String, Object>  after =  snapshot(issuance);
        audit.emit("belonging_issuance", String.valueOf(issuance.getId()), "updated", before, after, false, "belonging issuance created");
        return BelongingMapper.toResponse(issuance);
    }

    private void validateActivationTransition(ActivationStatus current, ActivationStatus next) {

        if (current == next) {
            return;
        }
        switch (current) {

            case ACTIVE -> {
                if (next == ActivationStatus.RETURNED
                        || next == ActivationStatus.LOST
                        || next == ActivationStatus.DAMAGED) {
                    return;
                }
                throw new ConflictError(EmployeeErrorCode.VALIDATION_FAILED, "Invalid transition from ACTIVE to " + next);
            }

            case LOST, DAMAGED, RETURNED -> {
                throw new ConflictError(EmployeeErrorCode.VALIDATION_FAILED,"Cannot change status once marked as " + current);
            }

            case INACTIVE -> {
                if (next == ActivationStatus.ACTIVE) {
                    return;
                }
                throw new ConflictError(EmployeeErrorCode.VALIDATION_FAILED,"INACTIVE can only move to ACTIVE");
            }

            default -> throw new ConflictError(EmployeeErrorCode.VALIDATION_FAILED,"Invalid status transition");
        }
    }

    public BelongingIssuanceResponse updateActivationStatus(Long issuanceId, ActivationStatus status) {

        BelongingIssuance issuance = getIssuance(issuanceId);
        validateActivationTransition(issuance.getActivationStatus(), status);
        issuance.setActivationStatus(status);
        Map<String, Object> before = snapshot(issuance);
        issuanceRepository.save(issuance);
        Map<String, Object> after =  snapshot(issuance);
        audit.emit("belonging_issuance", String.valueOf(issuance.getId()), "updated", before, after, false, "belonging issuance activation updated");
        return BelongingMapper.toResponse(issuance);
    }

    private static Map<String, Object> snapshot(BelongingIssuance g) {
        Map<String, Object> m = new HashMap<>();
        m.put("belongingMasterId", g.getId().toString());
        m.put("employeeId", g.getEmployeeId().toString());
        m.put("assetModel", g.getAssetModel());
        m.put("assetYear", g.getAssetYear());
        m.put("assetPrice", g.getAssetPrice());
        m.put("issueDate", g.getIssuedDate());
        m.put("expiryDate", g.getExpiryDate());
        m.put("buyBackPercEmployee", g.getBuyBackPercEmployee());
        m.put("buyBackPercEmployer", g.getBuyBackPercEmployer());
        m.put("condition", g.getCondition());
        m.put("activationStatus", g.getActivationStatus());
        m.put("activationDate", g.getActivationDate());
        m.put("description", g.getDescription());
        m.put("clearanceStatus", g.getClearanceStatus());
        m.put("clearanceId", g.getClearanceId());
        m.put("clearanceDate", g.getClearanceDate());
        return m;
    }
}

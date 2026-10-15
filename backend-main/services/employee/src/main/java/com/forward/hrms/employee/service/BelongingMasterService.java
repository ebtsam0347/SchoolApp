package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.BelongingMasterRequest;
import com.forward.hrms.employee.dto.request.BelongingMasterUpdateRequest;
import com.forward.hrms.employee.dto.response.BelongingMasterResponse;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.entity.BelongingMaster;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.mapper.BelongingMapper;
import com.forward.hrms.employee.repository.BelongingMasterRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.transaction.Transactional;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class BelongingMasterService {

    private final BelongingMasterRepository repository;
    private final EmployeeService employees;
    private final AuditEmitter audit;

    public BelongingMasterResponse create(BelongingMasterRequest request) {
        Employee employee = employees.load(request.authorizedEmployeeId());
        if(employee != null){
            if(employee.getEmploymentStatus() != EmploymentStatus.ACTIVE){
                throw new NotFoundError(EmployeeErrorCode.EMPLOYEE_TERMINATED, "Employee must be ACTIVE to authorize for an asset.");
            }
        }
        validateDuplicateName(request.belongingName(), null);
        BelongingMaster entity = BelongingMapper.toEntity(request);
        entity = repository.save(entity);

        audit.emit("belonging_master", String.valueOf(entity.getId()), "created", null,snapshot(entity), false, "belonging master created");

        return BelongingMapper.toResponse(entity);
    }

    public BelongingMasterResponse update(Long id, BelongingMasterUpdateRequest request) {
        BelongingMaster entity = getEntity(id);
        Employee employee = employees.load(request.authorizedEmployeeId());
        if(employee != null){
            if(employee.getEmploymentStatus() != EmploymentStatus.ACTIVE){
                throw new NotFoundError(EmployeeErrorCode.EMPLOYEE_TERMINATED, "Employee must be ACTIVE to authorize for an asset.");
            }
        }
        Map<String, Object> before = snapshot(entity);
        validateDuplicateName(request.belongingName(), id);
        entity.setBelongingName(request.belongingName());
        entity.setAuthorizedEmployeeId(request.authorizedEmployeeId());
        entity.setActive(request.active());

        repository.save(entity);
        audit.emit("belonging_master", String.valueOf(entity.getId()), "updated", before, snapshot(entity), false, "belonging master updated");

        return BelongingMapper.toResponse(entity);
    }

    @Transactional
    public BelongingMasterResponse getById(Long id) {
        return BelongingMapper.toResponse(getEntity(id));
    }

    @Transactional
    public List<BelongingMasterResponse> getAll() {
        return repository.findAll()
                .stream()
                .map(BelongingMapper::toResponse)
                .toList();
    }

    @Transactional
    public List<BelongingMasterResponse> getActive() {

        return repository.findByActiveTrue()
                .stream()
                .map(BelongingMapper::toResponse)
                .toList();
    }


    public BelongingMasterResponse changeStatus(Long id, boolean active) {

        BelongingMaster entity = getEntity(id);
        Map<String, Object> before = snapshot(entity);
        entity.setActive(active);
        repository.save(entity);
        audit.emit("belonging_master", String.valueOf(entity.getId()), "updated", before, snapshot(entity), false, "belonging master status updated");
        return BelongingMapper.toResponse(entity);
    }

    // ============================================================
    // Private Helpers
    // ============================================================

    private BelongingMaster getEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundError(EmployeeErrorCode.BELONGING_MASTER_NOT_FOUND, "Belonging Master not found of id: " + id));
    }

    private void validateDuplicateName(String belongingName, Long currentId) {
        repository.findByBelongingNameIgnoreCase(belongingName).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new ConflictError(EmployeeErrorCode.VALIDATION_FAILED, "Belonging name:" +  belongingName +" is already exists.");
            };
        });
    }

    private static Map<String, Object> snapshot(BelongingMaster g) {
        Map<String, Object> m = new HashMap<>();
        m.put("belongingName", g.getBelongingName());
        m.put("authorizedEmployeeId", g.getAuthorizedEmployeeId());
        m.put("active", g.getActive());
        return m;
    }

    public List<BelongingMasterResponse> getBelongingsAuthorizedToMe(){
        Optional<CurrentUser> user = CurrentUserHolder.get();

        if (user.isEmpty()) {
            throw new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "User not found.");
        }

        EmployeeDto employeeDto = employees.getByUserId(user.get().userId());
        return repository.findByAuthorizedEmployeeId(employeeDto.id());
    }

}

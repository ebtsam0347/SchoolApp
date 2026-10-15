package com.forward.hrms.employee.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;


@Component
@RequiredArgsConstructor
public class ManagerCycleChecker {

    private final EmployeeRepository repo;

    @Transactional(propagation = Propagation.MANDATORY)
    public void verify(long employeeId, Long candidateManagerId) {
        if (candidateManagerId == null) return;
        if (candidateManagerId == employeeId) {
            throw new ConflictError(EmployeeErrorCode.MANAGER_CYCLE,
                    "an employee cannot manage themselves");
        }
        if (repo.managerChainContains(employeeId, candidateManagerId)) {
            throw new ConflictError(EmployeeErrorCode.MANAGER_CYCLE,
                    "manager assignment would create a cycle");
        }
    }
}

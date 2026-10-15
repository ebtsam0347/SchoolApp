package com.forward.hrms.employee.approval;

import com.forward.hrms.commons.approval.spi.ManagerResolver;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RequisitionManagerResolver implements ManagerResolver {

    private final EmployeeRepository employees;

    @Override
    public Long resolveManagerUserId(long employeeId) {
        return employees.findById(employeeId)
                .map(e -> e.getManagerId() == null ? null
                        : employees.findById(e.getManagerId())
                                .map(m -> m.getUserId())
                                .orElse(null))
                .orElse(null);
    }
}

package com.forward.hrms.employee.approval;

import com.forward.hrms.commons.approval.spi.DepartmentHeadResolver;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RequisitionDepartmentHeadResolver implements DepartmentHeadResolver {

    private final DepartmentRepository departments;
    private final EmployeeRepository employees;

    @Override
    public Long resolveDepartmentHeadUserId(String departmentCode) {
        return departments.findByCode(departmentCode)
                .map(Department::getHeadId)
                .flatMap(employees::findById)
                .map(Employee::getUserId)
                .orElse(null);
    }
}

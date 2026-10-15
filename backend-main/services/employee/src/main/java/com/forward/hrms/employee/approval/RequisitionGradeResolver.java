package com.forward.hrms.employee.approval;

import com.forward.hrms.commons.approval.spi.GradeResolver;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.GradeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RequisitionGradeResolver implements GradeResolver {

    private final EmployeeRepository employees;
    private final GradeRepository grades;

    @Override
    public String resolveGradeCode(long userId) {
        return employees.findByUserId(userId)
                .map(Employee::getGradeId)
                .flatMap(grades::findById)
                .map(Grade::getCode)
                .orElse(null);
    }
}

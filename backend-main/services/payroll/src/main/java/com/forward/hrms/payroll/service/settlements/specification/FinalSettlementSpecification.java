package com.forward.hrms.payroll.service.settlements.specification;


import com.forward.hrms.payroll.dto.response.FinalSettlementSearchRequest;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class FinalSettlementSpecification {

    private FinalSettlementSpecification() {}

    public static Specification<EmployeeFinalSettlement> search(
            FinalSettlementSearchRequest request) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            //-------------------------------------------------------
            // Employee
            //-------------------------------------------------------

            if (request.getEmployeeId() != null) {
                predicates.add(
                        cb.equal(root.get("employeeId"),
                                request.getEmployeeId()));
            }

            if (request.getEmployeeCode() != null &&
                    !request.getEmployeeCode().isBlank()) {

                predicates.add(
                        cb.like(
                                cb.lower(root.get("employeeCode")),
                                "%" + request.getEmployeeCode().toLowerCase() + "%"
                        ));
            }

            if (request.getEmployeeName() != null &&
                    !request.getEmployeeName().isBlank()) {

                predicates.add(
                        cb.like(
                                cb.lower(root.get("employeeName")),
                                "%" + request.getEmployeeName().toLowerCase() + "%"
                        ));
            }

            //-------------------------------------------------------
            // Status
            //-------------------------------------------------------

            if (request.getStatus() != null) {
                predicates.add(
                        cb.equal(root.get("status"),
                                request.getStatus()));
            }

            //-------------------------------------------------------
            // Organization
            //-------------------------------------------------------

            if (request.getDepartment() != null &&
                    !request.getDepartment().isBlank()) {

                predicates.add(
                        cb.equal(root.get("department"),
                                request.getDepartment()));
            }

            if (request.getSection() != null &&
                    !request.getSection().isBlank()) {

                predicates.add(
                        cb.equal(root.get("section"),
                                request.getSection()));
            }

            if (request.getDesignation() != null &&
                    !request.getDesignation().isBlank()) {

                predicates.add(
                        cb.equal(root.get("designation"),
                                request.getDesignation()));
            }

            if (request.getGrade() != null &&
                    !request.getGrade().isBlank()) {

                predicates.add(
                        cb.equal(root.get("grade"),
                                request.getGrade()));
            }

            if (request.getGradeType() != null) {

                predicates.add(
                        cb.equal(root.get("gradeType"),
                                request.getGradeType()));
            }

            //-------------------------------------------------------
            // Hire Date
            //-------------------------------------------------------

            if (request.getHireDateFrom() != null) {

                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("hireDate"),
                                request.getHireDateFrom()));
            }

            if (request.getHireDateTo() != null) {

                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("hireDate"),
                                request.getHireDateTo()));
            }

            //-------------------------------------------------------
            // Last Working Day
            //-------------------------------------------------------

            if (request.getLastWorkingDayFrom() != null) {

                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("lastWorkingDay"),
                                request.getLastWorkingDayFrom()));
            }

            if (request.getLastWorkingDayTo() != null) {

                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("lastWorkingDay"),
                                request.getLastWorkingDayTo()));
            }

            //-------------------------------------------------------
            // Created Date
            //-------------------------------------------------------

            if (request.getCreatedFrom() != null) {

                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("createdAt"),
                                request.getCreatedFrom()));
            }

            if (request.getCreatedTo() != null) {

                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("createdAt"),
                                request.getCreatedTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

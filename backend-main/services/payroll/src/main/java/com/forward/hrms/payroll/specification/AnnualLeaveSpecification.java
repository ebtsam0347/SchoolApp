package com.forward.hrms.payroll.specification;

import com.forward.hrms.payroll.entity.AnnualLeaveTransaction;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public class AnnualLeaveSpecification {

    public static Specification<AnnualLeaveTransaction> filter(
            Integer year,
            Integer month,
            String employeeCode,
            Boolean paymentStatus,
            Boolean paymentPending,
            Boolean lockStatus
    ) {

        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (year != null && month != null) {
                LocalDate calculationMonth = LocalDate.of(year, month, 1);
                predicate = cb.and(predicate, cb.equal(root.get("calculationMonth"), calculationMonth));
            }
            if (employeeCode != null && !employeeCode.isBlank()) {
                predicate = cb.and(predicate,
                        cb.like(
                                cb.lower(root.get("employeeCode")),
                                "%" + employeeCode.toLowerCase() + "%"
                        )
                );
            }

            if (Boolean.TRUE.equals(paymentPending)) {
                predicate = cb.and(predicate, cb.isNull(root.get("paymentStatus")));
            } else if (paymentStatus != null) {
                predicate = cb.and(predicate, cb.equal(root.get("paymentStatus"), paymentStatus));
            }

            // New rows start with a null lockStatus, so "not locked" has to
            // match null as well as false.
            if (Boolean.TRUE.equals(lockStatus)) {
                predicate = cb.and(predicate, cb.isTrue(root.get("lockStatus")));
            } else if (Boolean.FALSE.equals(lockStatus)) {
                predicate = cb.and(predicate, cb.or(
                        cb.isNull(root.get("lockStatus")),
                        cb.isFalse(root.get("lockStatus"))
                ));
            }

            return predicate;
        };
    }
}
package com.forward.hrms.audit.repository;

import com.forward.hrms.audit.entity.AuditDeadLetter;
import com.forward.hrms.audit.enums.SkipReason;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic predicates for /admin/audit/dead-letters. Same Specification
 * pattern as {@link AuditEventSpecifications} — sidesteps the JPQL
 * nullable-param cast dance, especially for the enum reason filter.
 */
public final class AuditDeadLetterSpecifications {

    private AuditDeadLetterSpecifications() {}

    public static Specification<AuditDeadLetter> search(String source,
                                                        SkipReason reason,
                                                        LocalDateTime since) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (source != null && !source.isBlank()) {
                predicates.add(cb.equal(root.get("sourceService"), source));
            }
            if (reason != null) {
                predicates.add(cb.equal(root.get("reason"), reason));
            }
            if (since != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("receivedAt"), since));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

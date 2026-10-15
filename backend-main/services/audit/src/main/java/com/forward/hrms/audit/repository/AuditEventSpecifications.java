package com.forward.hrms.audit.repository;

import com.forward.hrms.audit.entity.AuditEvent;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic predicates for /audit/search. Avoids the JPQL nullable-param
 * dance — null filters are simply not added. Required for the
 * {@code userVisible} Boolean filter (Postgres rejects
 * {@code cast(? as boolean)} for nullable Booleans — see the
 * "JPQL nullable filter on Postgres" memory).
 */
public final class AuditEventSpecifications {

    private AuditEventSpecifications() {}

    public static Specification<AuditEvent> search(String q,
                                                   String entityType,
                                                   String action,
                                                   String correlationId,
                                                   Long actorUserId,
                                                   Boolean userVisible,
                                                   LocalDateTime from,
                                                   LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (q != null && !q.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("message")), "%" + q.toLowerCase() + "%"));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (correlationId != null && !correlationId.isBlank()) {
                predicates.add(cb.equal(root.get("correlationId"), correlationId));
            }
            if (actorUserId != null) {
                predicates.add(cb.equal(root.get("actorUserId"), actorUserId));
            }
            if (userVisible != null) {
                predicates.add(cb.equal(root.get("userVisible"), userVisible));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), to));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

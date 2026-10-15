package com.forward.hrms.auth.repository;

import com.forward.hrms.auth.entity.Role;
import com.forward.hrms.auth.entity.User;
import com.forward.hrms.auth.enums.UserStatus;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specifications for the admin user search. Each filter is
 * independently optional; nulls are dropped from the predicate list
 * rather than going through a JPQL {@code cast(:p as Type) is null}
 * dance — Postgres rejects the cast-to-boolean form (bytea→boolean),
 * and avoiding parameter binding altogether for absent filters is
 * simpler than working around the JDBC type inference gaps.
 */
public final class UserSearchSpecifications {

    private UserSearchSpecifications() {}

    public static Specification<User> search(UserStatus status,
                                             String q,
                                             String role,
                                             Boolean employeeLinked) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                // "LOCKED" is a computed state — LoginFailureRecorder writes
                // lockedUntil on the user row but never flips status off ACTIVE.
                // Translate the filter into the timestamp predicate, and exclude
                // currently-locked rows from the ACTIVE bucket so the buckets
                // partition the user set.
                switch (status) {
                    case LOCKED -> {
                        predicates.add(cb.equal(root.get("status"), UserStatus.ACTIVE));
                        predicates.add(cb.greaterThan(root.get("lockedUntil"), cb.literal(LocalDateTime.now())));
                    }
                    case ACTIVE -> {
                        predicates.add(cb.equal(root.get("status"), UserStatus.ACTIVE));
                        predicates.add(cb.or(
                                cb.isNull(root.get("lockedUntil")),
                                cb.lessThanOrEqualTo(root.get("lockedUntil"), cb.literal(LocalDateTime.now()))));
                    }
                    default -> predicates.add(cb.equal(root.get("status"), status));
                }
            }

            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.toLowerCase() + "%";
                Predicate username = cb.like(cb.lower(root.get("username")), pattern);
                Predicate fullName = cb.like(cb.lower(root.get("fullName")), pattern);
                Expression<String> email = cb.coalesce(root.get("email"), "");
                Predicate emailLike = cb.like(cb.lower(email), pattern);
                predicates.add(cb.or(username, fullName, emailLike));
            }

            // §7.auth #15 — distinct only when we actually inner-join roles
            // for the role-code filter. Without a role filter the join is
            // never added and DISTINCT is a wasted sort on every list page.
            if (role != null && !role.isBlank()) {
                Join<User, Role> roles = root.join("roles", JoinType.INNER);
                predicates.add(cb.equal(roles.get("code"), role));
                if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                    query.distinct(true);
                }
            }

            if (employeeLinked != null) {
                predicates.add(cb.equal(root.get("employeeLinked"), employeeLinked));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

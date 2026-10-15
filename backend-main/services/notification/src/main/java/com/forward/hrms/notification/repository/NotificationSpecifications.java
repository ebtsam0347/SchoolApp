package com.forward.hrms.notification.repository;

import com.forward.hrms.notification.entity.Notification;
import com.forward.hrms.notification.enums.NotificationCategory;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Combinatorial filters for the bell feed. Each predicate is added only
 * when its argument is non-null, so the same Specification works for
 * "everything for me", "unread for me", "approvals for me", etc.
 */
public final class NotificationSpecifications {

    private NotificationSpecifications() {}

    public static Specification<Notification> forBellFeed(long userId,
                                                          boolean unreadOnly,
                                                          NotificationCategory category) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (unreadOnly) {
                predicates.add(cb.isNull(root.get("readAt")));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}

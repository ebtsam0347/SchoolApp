package com.forward.hrms.notification.repository;

import com.forward.hrms.notification.entity.NotificationPreference;
import com.forward.hrms.notification.entity.NotificationPreferenceId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreference, NotificationPreferenceId> {

    List<NotificationPreference> findAllByUserId(Long userId);

    /**
     * Bulk opt-out lookup for {@link NotificationService#dispatchBulk}.
     * Returns only the {@code (userId)} rows that have explicitly opted
     * out — caller treats every other userId as opted-in.
     */
    @org.springframework.data.jpa.repository.Query("""
            select p.userId from NotificationPreference p
            where p.userId in :userIds
              and p.type = :type
              and p.enabled = false
            """)
    List<Long> findOptedOutUserIds(@org.springframework.data.repository.query.Param("userIds")
                                   java.util.Collection<Long> userIds,
                                   @org.springframework.data.repository.query.Param("type") String type);
}

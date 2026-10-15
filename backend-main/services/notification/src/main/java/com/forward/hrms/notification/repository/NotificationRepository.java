package com.forward.hrms.notification.repository;

import com.forward.hrms.notification.entity.Notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;

public interface NotificationRepository
        extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now, n.updatedAt = :now "
            + "where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Bulk mark-read scoped to {@code userId} so a caller can never flip
     * another user's row. The {@code readAt is null} guard means the count
     * returned is the count of rows actually transitioning to read — which
     * is what the unread-counter cache should be decremented by.
     */
    @Modifying
    @Query("update Notification n set n.readAt = :now, n.updatedAt = :now "
            + "where n.userId = :userId and n.id in :ids and n.readAt is null")
    int markRead(@Param("userId") Long userId,
                 @Param("ids") Collection<Long> ids,
                 @Param("now") LocalDateTime now);

    /**
     * Sweeps everything OUTSIDE the PAYROLL category older than the
     * default retention window. PAYROLL has its own (longer) cutoff —
     * see {@link #deletePayrollOlderThan}. The {@code is null} branch
     * catches legacy rows that pre-date categorization; they fall under
     * the default retention by design (no special status without a
     * category tag).
     */
    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff "
            + "and (n.category is null or n.category <> com.forward.hrms.notification.enums.NotificationCategory.PAYROLL)")
    int deleteNonPayrollOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff "
            + "and n.category = com.forward.hrms.notification.enums.NotificationCategory.PAYROLL")
    int deletePayrollOlderThan(@Param("cutoff") LocalDateTime cutoff);
}

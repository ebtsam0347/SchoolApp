package com.forward.hrms.notification.repository;

import com.forward.hrms.notification.entity.PushSubscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findAllByUserIdIn(Collection<Long> userIds);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    @Transactional
    long deleteByUserIdAndEndpoint(Long userId, String endpoint);

    /** Called off the request thread when the push service reports the subscription gone. */
    @Transactional
    long deleteByEndpoint(String endpoint);
}

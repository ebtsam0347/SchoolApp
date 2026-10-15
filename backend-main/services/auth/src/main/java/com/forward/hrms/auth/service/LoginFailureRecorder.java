package com.forward.hrms.auth.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.auth.entity.User;
import com.forward.hrms.auth.events.AuthEvents;
import com.forward.hrms.auth.repository.UserRepository;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LoginFailureRecorder {

    private final UserRepository users;
    private final LoginFailureCounter failures;
    private final Outbox outbox;
    private final AuditEmitter audit;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBadPassword(long userId) {
        Optional<User> opt = users.findById(userId);
        if (opt.isEmpty()) return;
        User user = opt.get();
        long count = failures.increment(user.getUsername());
        user.setFailedLogins((int) Math.min(count, Integer.MAX_VALUE));

        if (count >= LoginFailureCounter.LOCK_THRESHOLD) {
            LocalDateTime until = LocalDateTime.now().plus(LoginFailureCounter.LOCK_DURATION);
            user.setLockedUntil(until);
            outbox.publish(AuthEvents.TOPIC, "user:" + user.getId(), AuthEvents.USER_LOCKED,
                    new AuthEvents.UserLocked(user.getId(), until));
            audit.emit("user", String.valueOf(user.getId()), "user.locked",
                    null, Map.of("until", until.toString()), false);
            failures.clear(user.getUsername());
        }
        audit.emit("user", String.valueOf(user.getId()), "login.failed",
                null, Map.of("reason", "BAD_PASSWORD"), false);
    }
}

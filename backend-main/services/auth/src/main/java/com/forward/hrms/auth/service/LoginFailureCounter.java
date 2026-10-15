package com.forward.hrms.auth.service;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Per-username failed-login counter. Five within the 15-minute window
 * triggers a 15-minute account lock at the database level.
 */
@Component
@RequiredArgsConstructor
public class LoginFailureCounter {

    private static final String PREFIX = "auth:login-fail:";
    private static final Duration WINDOW = Duration.ofMinutes(15);

    public static final int LOCK_THRESHOLD = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    public long increment(String username) {
        String key = PREFIX + username;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) redis.expire(key, WINDOW);
        return count == null ? 0L : count;
    }

    public void clear(String username) {
        redis.delete(PREFIX + username);
    }
}

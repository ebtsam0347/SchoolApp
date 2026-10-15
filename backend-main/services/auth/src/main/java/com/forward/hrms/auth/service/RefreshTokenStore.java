package com.forward.hrms.auth.service;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String PREFIX = "auth:refresh:";

    private final StringRedisTemplate redis;

    public void put(String refreshTokenId, long userId, long sessionId, Duration ttl) {
        redis.opsForValue().set(PREFIX + refreshTokenId, userId + ":" + sessionId, ttl);
    }

    public Optional<Lookup> get(String refreshTokenId) {
        String v = redis.opsForValue().get(PREFIX + refreshTokenId);
        if (v == null) return Optional.empty();
        int sep = v.indexOf(':');
        if (sep <= 0) return Optional.empty();
        try {
            long userId = Long.parseLong(v.substring(0, sep));
            long sessionId = Long.parseLong(v.substring(sep + 1));
            return Optional.of(new Lookup(userId, sessionId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public void delete(String refreshTokenId) {
        redis.delete(PREFIX + refreshTokenId);
    }

    public record Lookup(long userId, long sessionId) {}
}

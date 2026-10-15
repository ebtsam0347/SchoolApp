package com.forward.hrms.commons.saga;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Redis-backed {@link SagaSecretStore}. Each saga gets its own hash at
 * {@code saga:secrets:{sagaId}} with a TTL bounded by the saga's
 * max-life. Hash fields are secret names; values are the secret strings.
 *
 * <p>Wired by {@code SagaSecretStoreAutoConfiguration} when both
 * {@link StringRedisTemplate} and the {@link SagaSecretStore} interface
 * are on the classpath.
 */
public class RedisSagaSecretStore implements SagaSecretStore {

    private static final String PREFIX = "saga:secrets:";

    private final StringRedisTemplate redis;

    public RedisSagaSecretStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void put(long sagaId, Map<String, String> secrets, Duration ttl) {
        if (secrets == null || secrets.isEmpty()) return;
        String key = PREFIX + sagaId;
        redis.opsForHash().putAll(key, secrets);
        redis.expire(key, ttl);
    }

    @Override
    public String get(long sagaId, String name) {
        Object v = redis.opsForHash().get(PREFIX + sagaId, name);
        return v == null ? null : v.toString();
    }

    @Override
    public void remove(long sagaId) {
        redis.delete(PREFIX + sagaId);
    }
}

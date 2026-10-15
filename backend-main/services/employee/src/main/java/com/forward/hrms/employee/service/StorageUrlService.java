package com.forward.hrms.employee.service;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.employee.config.DocumentProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * Signs the GET URLs the FE loads directly from MinIO.
 *
 * <p>Avatars are special. A directory or approvals page renders ~100 of them
 * at once, and a pre-signed URL carries {@code X-Amz-Date} +
 * {@code X-Amz-Signature} — so re-signing per response produces a different
 * URL, a different browser cache key, and a full re-download of every avatar
 * on every page load. The signature is therefore memoized in Redis under the
 * object key: same URL for every caller and every render, so the browser
 * serves repeat views from disk cache and issues zero requests. The signature
 * itself lasts a week (SigV4's ceiling) and the memo expires a day earlier.
 *
 * <p>Redis being unavailable degrades to signing per call — the old
 * behaviour, correct but uncached.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageUrlService {

    private static final String AVATAR_URL_KEY_PREFIX = "hrms:employee:avatarurl:";

    /**
     * Folded into the signature so MinIO returns it on the object, which was
     * uploaded without any caching header of its own.
     */
    private static final Map<String, String> AVATAR_RESPONSE_OVERRIDES =
            Map.of("response-cache-control", "public, max-age=604800, immutable");

    private final ObjectStorage storage;
    private final DocumentProperties props;
    private final ObjectProvider<StringRedisTemplate> redis;

    public String signGet(String key) {
        return signGet(props.bucket(), key, props.downloadUrlTtl(), null);
    }

    public String signAvatarGet(String key) {
        if (key == null || key.isBlank()) return null;

        StringRedisTemplate r = redis.getIfAvailable();
        String cacheKey = AVATAR_URL_KEY_PREFIX + key;
        if (r != null) {
            try {
                String hit = r.opsForValue().get(cacheKey);
                if (hit != null) return hit;
            } catch (DataAccessException ex) {
                log.warn("avatar URL memo read failed for {}: {}", key, ex.getMessage());
            }
        }

        String url = signGet(props.avatarBucket(), key, props.avatarUrlTtl(), AVATAR_RESPONSE_OVERRIDES);
        if (url != null && r != null) {
            try {
                r.opsForValue().set(cacheKey, url, props.avatarUrlCacheTtl());
            } catch (DataAccessException ex) {
                log.warn("avatar URL memo write failed for {}: {}", key, ex.getMessage());
            }
        }
        return url;
    }

    /** Drop the memo for an object that is about to stop existing. */
    public void evictAvatar(String key) {
        if (key == null || key.isBlank()) return;
        StringRedisTemplate r = redis.getIfAvailable();
        if (r == null) return;
        try {
            r.delete(AVATAR_URL_KEY_PREFIX + key);
        } catch (DataAccessException ex) {
            log.warn("avatar URL memo evict failed for {}: {}", key, ex.getMessage());
        }
    }

    private String signGet(String bucket, String key, java.time.Duration ttl,
                           Map<String, String> responseOverrides) {
        if (key == null || key.isBlank()) return null;
        try {
            return storage.signedGetUrl(bucket, key, ttl, responseOverrides);
        } catch (RuntimeException ex) {
            log.warn("Failed to sign GET URL for {}: {}", key, ex.getMessage());
            return null;
        }
    }
}

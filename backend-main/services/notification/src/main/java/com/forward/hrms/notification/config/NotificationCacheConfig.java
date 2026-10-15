package com.forward.hrms.notification.config;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caffeine cache for the two read-heavy lookups in notification:
 * <ul>
 *   <li>{@code optouts} — keyed by {@code (userId, type)}, 5-min TTL.
 *       Invalidated when a user touches their preferences. Stops a
 *       50-person chat fan-out from hitting the DB 50 times per
 *       message.</li>
 *   <li>{@code templates} — keyed by {@code type}, lifetime TTL.
 *       Invalidated when a template is updated.</li>
 * </ul>
 *
 * <p>Caches live in the JVM only — there is one notification instance
 * today. If we ever scale out, swap to a distributed cache (or accept
 * stale-up-to-TTL since these reads aren't load-bearing).
 */
@Configuration
@EnableCaching
public class NotificationCacheConfig {

    public static final String CACHE_OPTOUTS = "optouts";
    public static final String CACHE_TEMPLATES = "templates";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager(CACHE_OPTOUTS, CACHE_TEMPLATES);
        mgr.registerCustomCache(CACHE_OPTOUTS, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(10_000)
                .build());
        mgr.registerCustomCache(CACHE_TEMPLATES, Caffeine.newBuilder()
                .maximumSize(500)
                .build());
        return mgr;
    }
}

package com.forward.hrms.bff.config;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache for BFF catalog reads (departments, sections, designations,
 * grades, provinces, cities, qualifications, leave types). These rarely
 * change and are read on every directory page render. 5-minute TTL is
 * a sensible compromise: staleness window is bounded, and an HR
 * catalog edit propagates within the lunch break.
 *
 * <p>An {@code employee.catalog_changed} event-driven invalidation
 * is the eventual upgrade path; deferred until the catalog edit
 * endpoint actually exists in employee-service. TTL is the floor.
 */
@Configuration
@EnableCaching
public class BffCacheConfig {

    public static final String CACHE_CATALOGS = "bff.catalogs";

    /**
     * §7.bff #27 — short-lived cache of {@code (userId → employeeId)} so
     * the search palette doesn't hit {@code /employees/me} on every
     * keystroke. 60-second TTL: fresh enough that a brand-new ESS link
     * shows up within a minute; long enough to absorb a typing burst.
     */
    public static final String CACHE_CALLER_EMPLOYEE_ID = "bff.callerEmployeeId";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager(CACHE_CATALOGS, CACHE_CALLER_EMPLOYEE_ID);
        mgr.registerCustomCache(CACHE_CATALOGS, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(64)
                .build());
        mgr.registerCustomCache(CACHE_CALLER_EMPLOYEE_ID, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(2048)
                .build());
        return mgr;
    }
}

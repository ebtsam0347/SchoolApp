package com.forward.hrms.employee.config;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;


@Configuration
@EnableCaching
public class EmployeeCacheConfig {

    public static final String CACHE_CATALOG_LABELS = "catalogLabels";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager(CACHE_CATALOG_LABELS);
        mgr.registerCustomCache(CACHE_CATALOG_LABELS, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(64)
                .build());
        return mgr;
    }
}

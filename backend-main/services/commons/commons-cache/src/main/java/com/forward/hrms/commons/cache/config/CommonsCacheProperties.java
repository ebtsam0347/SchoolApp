package com.forward.hrms.commons.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Knobs for the shared cache. Only the URL is mandatory; TTLs default
 * to safe values that double as a safety net behind the
 * event-driven invalidation.
 */
@ConfigurationProperties("hrms.cache")
public class CommonsCacheProperties {

    /** Master switch. Services that don't need the cache leave this off. */
    private boolean enabled = false;

    /**
     * Base URL of employee-service for the bulk-fetch miss path
     * (e.g. {@code http://employee:8080}). Required when {@code enabled = true}.
     */
    private String employeeServiceUrl;

    /** TTL for {@code hrms:cache:emp:{id}} entries. Default 24h. */
    private long employeeTtlSeconds = 24 * 3600L;

    /** TTL for catalog list entries ({@code hrms:cache:cat:*}). Default 1h. */
    private long catalogTtlSeconds = 3600L;

    /** TTL for tombstones (ids confirmed gone upstream). Default 1h. */
    private long tombstoneTtlSeconds = 3600L;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String employeeServiceUrl() { return employeeServiceUrl; }
    public void setEmployeeServiceUrl(String employeeServiceUrl) { this.employeeServiceUrl = employeeServiceUrl; }

    public long employeeTtlSeconds() { return employeeTtlSeconds; }
    public void setEmployeeTtlSeconds(long employeeTtlSeconds) { this.employeeTtlSeconds = employeeTtlSeconds; }

    public long catalogTtlSeconds() { return catalogTtlSeconds; }
    public void setCatalogTtlSeconds(long catalogTtlSeconds) { this.catalogTtlSeconds = catalogTtlSeconds; }

    public long tombstoneTtlSeconds() { return tombstoneTtlSeconds; }
    public void setTombstoneTtlSeconds(long tombstoneTtlSeconds) { this.tombstoneTtlSeconds = tombstoneTtlSeconds; }
}

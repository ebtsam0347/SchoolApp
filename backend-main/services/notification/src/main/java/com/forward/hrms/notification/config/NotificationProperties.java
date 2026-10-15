package com.forward.hrms.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Notification-service config — dedupe and retention windows, broadcast
 * caps. Source topics are wired directly in {@code EventConsumer}'s
 * annotations (mirrors audit-service's literal-string pattern).
 *
 * <p>{@code payrollRetention} overrides {@code retention} for rows in
 * the {@code PAYROLL} category — per {@code docs/policy.md} row #10,
 * payroll notifications match audit retention (4y) since they are the
 * employee's primary record of payslip delivery.
 */
@ConfigurationProperties(prefix = "hrms.notification")
public record NotificationProperties(
        Duration dedupeTtl,
        Duration retention,
        Duration payrollRetention,
        int broadcastMaxRecipients
) {
    public NotificationProperties {
        if (dedupeTtl == null) dedupeTtl = Duration.ofHours(24);
        if (retention == null) retention = Duration.ofDays(180);
        if (payrollRetention == null) payrollRetention = Duration.ofDays(365L * 4);
        if (broadcastMaxRecipients <= 0) broadcastMaxRecipients = 5000;
    }
}

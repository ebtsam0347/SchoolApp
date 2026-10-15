package com.forward.hrms.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Audit-service config.
 *
 * <p>{@code topics} — per-service Kafka topics consumed by audit-service.
 * Each topic gets one consumer group ({@code audit.<svc>}) so consumers
 * scale and replay independently per source.
 *
 * <p>{@code partition} — knobs for the daily {@code AuditPartitionService}
 * job that maintains {@code audit_events} monthly partitions. Defaults
 * follow {@code docs/policy.md} #9 (4y retention = 48 months) and a
 * 2-month look-ahead so a month-end maintenance miss never causes an
 * insert failure on the partition boundary.
 */
@ConfigurationProperties(prefix = "hrms.audit")
public record AuditProperties(List<String> topics, Partition partition) {

    public AuditProperties {
        topics = topics == null ? List.of() : List.copyOf(topics);
        partition = partition == null ? new Partition(48, 2) : partition;
    }

    public record Partition(int retentionMonths, int lookaheadMonths) {
        public Partition {
            if (retentionMonths <= 0) retentionMonths = 48;
            if (lookaheadMonths <= 0) lookaheadMonths = 2;
        }
    }
}

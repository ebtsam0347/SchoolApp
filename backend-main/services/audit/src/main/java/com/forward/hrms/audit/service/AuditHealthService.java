package com.forward.hrms.audit.service;

import com.forward.hrms.audit.config.AuditProperties;
import com.forward.hrms.audit.dto.response.HealthResponse;

import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Per-topic consumer lag using {@link AdminClient}. Used by the
 * {@code /admin/audit/health} endpoint. Lag = end offset - committed
 * offset; uncommitted partitions report committed = 0 so the lag equals
 * the end offset.
 */
@Slf4j
@Service
public class AuditHealthService {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final AuditProperties props;
    private final String bootstrapServers;

    public AuditHealthService(AuditProperties props,
                              @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.props = props;
        this.bootstrapServers = bootstrapServers;
    }

    public HealthResponse snapshot() {
        Properties cfg = new Properties();
        cfg.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        cfg.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) TIMEOUT.toMillis());

        List<HealthResponse.TopicHealth> out = new ArrayList<>();
        try (AdminClient admin = AdminClient.create(cfg)) {
            for (String topic : props.topics()) {
                String group = "audit." + topic.substring(0, topic.length() - ".audit".length());
                try {
                    out.add(forTopic(admin, topic, group));
                } catch (RuntimeException e) {
                    log.warn("Health probe failed for {} ({}): {}", topic, group, e.getMessage());
                    out.add(new HealthResponse.TopicHealth(topic, group, -1L, List.of()));
                }
            }
        }
        return new HealthResponse(out);
    }

    private HealthResponse.TopicHealth forTopic(AdminClient admin, String topic, String group) {
        var partitions = admin.describeTopics(List.of(topic))
                .topicNameValues().get(topic).toCompletionStage().toCompletableFuture().join()
                .partitions();
        Map<TopicPartition, OffsetSpec> req = new HashMap<>();
        partitions.forEach(p -> req.put(new TopicPartition(topic, p.partition()), OffsetSpec.latest()));

        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ends =
                admin.listOffsets(req).all().toCompletionStage().toCompletableFuture().join();

        Map<TopicPartition, OffsetAndMetadata> committed =
                admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata()
                        .toCompletionStage().toCompletableFuture().join();

        long total = 0L;
        List<HealthResponse.PartitionHealth> rows = new ArrayList<>();
        for (var entry : ends.entrySet()) {
            long end = entry.getValue().offset();
            long off = committed.containsKey(entry.getKey()) ? committed.get(entry.getKey()).offset() : 0L;
            long lag = Math.max(0, end - off);
            total += lag;
            rows.add(new HealthResponse.PartitionHealth(entry.getKey().partition(), end, off, lag));
        }
        return new HealthResponse.TopicHealth(topic, group, total, rows);
    }
}

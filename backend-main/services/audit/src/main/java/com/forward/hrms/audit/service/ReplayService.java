package com.forward.hrms.audit.service;

import com.forward.hrms.audit.config.AuditProperties;
import com.forward.hrms.audit.dto.request.ReplayRequest;
import com.forward.hrms.audit.dto.response.ReplayResult;
import com.forward.hrms.audit.enums.AuditErrorCode;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.core.error.Violation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Replays a bounded Kafka offset range for a single audit topic. Bounded
 * only — no "from beginning". Idempotent ingest (unique {@code event_id})
 * makes duplicates safe; replays count duplicates as {@code skipped}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayService {

    private static final Duration POLL = Duration.ofMillis(500);
    private static final Duration POLL_BUDGET = Duration.ofSeconds(30);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    private final AuditIngestService ingest;
    private final AuditProperties props;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Tokens issued by dryRun calls; consumed (single-use) by the
     * matching non-dry call. In-memory is enough for FSHRMS — replay
     * is a single-operator, single-process operation. Process restart
     * invalidates outstanding tokens, which is the desired behavior
     * (re-run the dry-run after a restart).
     */
    private final Map<String, ReplayToken> tokens = new ConcurrentHashMap<>();

    private record ReplayToken(String topic, long fromOffset, long toOffset, Integer partition, Instant expiresAt) {
        boolean matches(ReplayRequest req) {
            return topic.equals(req.topic())
                    && fromOffset == req.fromOffset()
                    && toOffset == req.toOffset()
                    && java.util.Objects.equals(partition, req.partition());
        }
    }

    public ReplayResult replay(ReplayRequest req) {
        validate(req);
        boolean dry = Boolean.TRUE.equals(req.dryRun());
        boolean countOnly = Boolean.TRUE.equals(req.countOnly());

        // count-only: short-circuit, no Kafka poll, no token issued.
        if (countOnly) {
            int partitionFactor = req.partition() == null ? estimatePartitionCount(req) : 1;
            int upperBound = (int) Math.min(Integer.MAX_VALUE,
                    (req.toOffset() - req.fromOffset()) * partitionFactor);
            return new ReplayResult(upperBound, 0, 0, false, true, null);
        }

        // non-dry replay requires a token from a recent dry-run call.
        if (!dry) {
            requireValidToken(req);
        }

        String source = req.topic().substring(0, req.topic().length() - ".audit".length());
        Properties cfg = consumerProps();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(cfg)) {
            List<TopicPartition> partitions = resolvePartitions(consumer, req);
            if (partitions.isEmpty()) {
                throw new UnprocessableError(AuditErrorCode.REPLAY_FAILED,
                        "Topic " + req.topic() + " has no partitions matching the request.");
            }
            consumer.assign(partitions);
            partitions.forEach(p -> consumer.seek(p, req.fromOffset()));

            int replayed = 0, skipped = 0, errors = 0;
            long deadline = System.currentTimeMillis() + POLL_BUDGET.toMillis();

            while (System.currentTimeMillis() < deadline && !allDone(consumer, partitions, req.toOffset())) {
                ConsumerRecords<String, String> batch = consumer.poll(POLL);
                if (batch.isEmpty()) continue;
                for (ConsumerRecord<String, String> rec : batch) {
                    if (rec.offset() >= req.toOffset()) continue;
                    if (dry) { replayed++; continue; }
                    try {
                        switch (ingest.ingest(source, rec.value()).status()) {
                            case INSERTED -> replayed++;
                            case DUPLICATE, SKIPPED -> skipped++;
                        }
                    } catch (RuntimeException ex) {
                        log.warn("Replay error on {}-{}@{}: {}", rec.topic(), rec.partition(), rec.offset(),
                                ex.getMessage());
                        errors++;
                    }
                }
            }
            String token = dry ? issueToken(req) : null;
            return new ReplayResult(replayed, skipped, errors, dry, false, token);
        }
    }

    private String issueToken(ReplayRequest req) {
        purgeExpired();
        String id = UUID.randomUUID().toString();
        tokens.put(id, new ReplayToken(req.topic(), req.fromOffset(), req.toOffset(),
                req.partition(), Instant.now().plus(TOKEN_TTL)));
        return id;
    }

    private void requireValidToken(ReplayRequest req) {
        if (req.replayToken() == null || req.replayToken().isBlank()) {
            throw new ValidationError(AuditErrorCode.REPLAY_TOKEN_REQUIRED,
                    "Non-dry replay requires a replayToken from a recent dryRun:true call.",
                    List.of(new Violation("replayToken", "audit.replay.token_required",
                            "Run with dryRun:true first, then resend with the returned replayToken.")));
        }
        purgeExpired();
        ReplayToken stored = tokens.remove(req.replayToken());
        if (stored == null || !stored.matches(req)) {
            throw new ValidationError(AuditErrorCode.REPLAY_TOKEN_INVALID,
                    "replayToken is unknown, expired, or doesn't match this request.",
                    List.of(new Violation("replayToken", "audit.replay.token_invalid",
                            "Tokens are single-use, valid for 5 minutes, and bound to the exact "
                                    + "(topic, fromOffset, toOffset, partition) the dry-run was issued for.")));
        }
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        tokens.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
    }

    private int estimatePartitionCount(ReplayRequest req) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps())) {
            List<PartitionInfo> info = consumer.partitionsFor(req.topic());
            return info == null || info.isEmpty() ? 1 : info.size();
        }
    }

    private void validate(ReplayRequest req) {
        if (!props.topics().contains(req.topic())) {
            throw new ValidationError(AuditErrorCode.REPLAY_TOPIC_UNKNOWN,
                    "Unknown audit topic.",
                    List.of(new Violation("topic", "audit.replay.topic_unknown",
                            "Expected one of " + props.topics() + ".")));
        }
        if (req.fromOffset() < 0 || req.toOffset() <= req.fromOffset()) {
            throw new ValidationError(AuditErrorCode.REPLAY_FAILED,
                    "Invalid offset range.",
                    List.of(new Violation("offsets", "audit.replay.offsets_invalid",
                            "fromOffset must be >= 0 and toOffset > fromOffset.")));
        }
        if (Boolean.TRUE.equals(req.dryRun()) && Boolean.TRUE.equals(req.countOnly())) {
            throw new ValidationError(AuditErrorCode.REPLAY_FAILED,
                    "dryRun and countOnly are mutually exclusive.",
                    List.of(new Violation("mode", "audit.replay.mode_conflict",
                            "Pick one: dryRun (Kafka poll, exact count, issues token) or "
                                    + "countOnly (no Kafka poll, upper-bound estimate, no token).")));
        }
    }

    private Properties consumerProps() {
        Properties p = new Properties();
        p.put("bootstrap.servers", bootstrapServers);
        p.put("group.id", "audit.replay-" + UUID.randomUUID());
        p.put("enable.auto.commit", "false");
        p.put("key.deserializer", StringDeserializer.class.getName());
        p.put("value.deserializer", StringDeserializer.class.getName());
        p.put("auto.offset.reset", "none");
        return p;
    }

    private List<TopicPartition> resolvePartitions(KafkaConsumer<String, String> consumer, ReplayRequest req) {
        List<PartitionInfo> info = consumer.partitionsFor(req.topic());
        if (info == null) return List.of();
        return info.stream()
                .filter(pi -> req.partition() == null || pi.partition() == req.partition())
                .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                .toList();
    }

    private boolean allDone(KafkaConsumer<String, String> consumer, List<TopicPartition> ps, long toOffset) {
        Map<TopicPartition, Long> positions = new HashMap<>();
        for (TopicPartition p : ps) positions.put(p, consumer.position(p));
        return positions.values().stream().allMatch(off -> off >= toOffset);
    }
}

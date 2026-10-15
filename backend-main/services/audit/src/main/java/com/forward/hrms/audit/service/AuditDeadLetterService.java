package com.forward.hrms.audit.service;

import com.forward.hrms.audit.dto.response.DeadLetterReplayResult;
import com.forward.hrms.audit.entity.AuditDeadLetter;
import com.forward.hrms.audit.enums.AuditErrorCode;
import com.forward.hrms.audit.enums.SkipReason;
import com.forward.hrms.audit.repository.AuditDeadLetterRepository;
import com.forward.hrms.audit.repository.AuditDeadLetterSpecifications;
import com.forward.hrms.audit.service.AuditIngestService.IngestOutcome;
import com.forward.hrms.commons.core.error.NotFoundError;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Persists envelopes the ingest service rejected ({@code IngestOutcome.SKIPPED})
 * so the audit pipeline can survive a producer-side bug without losing
 * records past Kafka retention. See V0005 migration for the table.
 *
 * <p>The {@code @Transactional(REQUIRES_NEW)} on {@link #record} matters:
 * the consumer is not transactional, but if anything below this line
 * throws (DB down, payload past the 1MB cap), the exception propagates
 * up to the listener, which then doesn't ack — Kafka redelivers on the
 * next poll. That's the durability guarantee of the dead-letter table.
 */
@Slf4j
@Service
public class AuditDeadLetterService {

    private static final int RAW_PAYLOAD_MAX_BYTES = 1_048_576; // 1 MB, matches CHECK constraint
    private static final int REASON_DETAIL_MAX = 4_000;
    private static final int KAFKA_KEY_MAX = 255;

    private final AuditDeadLetterRepository repo;
    private final AuditIngestService ingest;
    private final MeterRegistry meterRegistry;

    public AuditDeadLetterService(AuditDeadLetterRepository repo,
                                  AuditIngestService ingest,
                                  MeterRegistry meterRegistry) {
        this.repo = repo;
        this.ingest = ingest;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Polled on every scrape — count(*) on a normally-empty table is
     * cheap, and this is the gauge the AuditDeadLetters alert keys off.
     */
    @PostConstruct
    void registerSizeGauge() {
        Gauge.builder("audit.dead_letters.size", repo, AuditDeadLetterRepository::count)
                .description("Outstanding parked audit envelopes (audit_dead_letters row count).")
                .register(meterRegistry);
    }

    @Transactional(readOnly = true)
    public Page<AuditDeadLetter> search(String source, SkipReason reason, LocalDateTime since, Pageable pageable) {
        return repo.findAll(AuditDeadLetterSpecifications.search(source, reason, since), pageable);
    }

    @Transactional(readOnly = true)
    public AuditDeadLetter get(long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundError(
                AuditErrorCode.DEAD_LETTER_NOT_FOUND,
                "Dead-letter row " + id + " does not exist."));
    }

    @Transactional
    public void discard(long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundError(AuditErrorCode.DEAD_LETTER_NOT_FOUND,
                    "Dead-letter row " + id + " does not exist.");
        }
        repo.deleteById(id);
    }

    /**
     * Re-feeds a parked envelope through {@link AuditIngestService#ingest}.
     * On success (INSERTED or DUPLICATE) the dead-letter row is deleted;
     * on still-failing the row is updated with the new reason / detail
     * and the attempt counter advances. Idempotent — replaying an
     * already-deleted row throws {@code NotFoundError}; replaying twice
     * with no producer fix in between just bumps the attempt count.
     */
    @Transactional
    public DeadLetterReplayResult replay(long id) {
        AuditDeadLetter row = get(id);
        IngestOutcome outcome = ingest.ingest(row.getSourceService(), row.getRawPayload());
        switch (outcome.status()) {
            case INSERTED -> {
                repo.delete(row);
                log.info("Replayed dead-letter {} ({}@{}/{}): INSERTED",
                        id, row.getTopic(), row.getKafkaPartition(), row.getKafkaOffset());
                return new DeadLetterReplayResult(DeadLetterReplayResult.Status.INSERTED, null, null);
            }
            case DUPLICATE -> {
                repo.delete(row);
                log.info("Replayed dead-letter {} ({}@{}/{}): DUPLICATE — already in audit_events",
                        id, row.getTopic(), row.getKafkaPartition(), row.getKafkaOffset());
                return new DeadLetterReplayResult(DeadLetterReplayResult.Status.DUPLICATE, null, null);
            }
            case SKIPPED -> {
                row.setReason(outcome.reason());
                row.setReasonDetail(truncate(outcome.detail(), REASON_DETAIL_MAX));
                row.setLastReplayAt(LocalDateTime.now());
                row.setReplayAttempts(row.getReplayAttempts() + 1);
                log.warn("Replay of dead-letter {} still failing: reason={}", id, outcome.reason());
                return new DeadLetterReplayResult(
                        DeadLetterReplayResult.Status.STILL_FAILING,
                        outcome.reason(),
                        outcome.detail());
            }
        }
        // Unreachable — switch covers every Status; here only to satisfy the compiler.
        throw new IllegalStateException("unreachable");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditDeadLetter record(String sourceService,
                                  ConsumerRecord<String, String> rec,
                                  IngestOutcome outcome) {
        AuditDeadLetter row = new AuditDeadLetter();
        row.setSourceService(sourceService);
        row.setTopic(rec.topic());
        row.setKafkaPartition(rec.partition());
        row.setKafkaOffset(rec.offset());
        row.setKafkaKey(truncate(rec.key(), KAFKA_KEY_MAX));
        // The 1MB CHECK is also enforced in DB. We truncate here so a 5MB
        // misbehaving emitter doesn't fail the consumer in a redelivery
        // loop — the truncated raw is still useful for debugging the
        // producer; the operator can correlate with the Kafka offset
        // we recorded.
        row.setRawPayload(truncatePayload(rec.value()));
        row.setReason(outcome.reason());
        row.setReasonDetail(truncate(outcome.detail(), REASON_DETAIL_MAX));
        row.setReceivedAt(LocalDateTime.now());
        row.setReplayAttempts(0);
        return repo.save(row);
    }

    private static String truncatePayload(String s) {
        if (s == null) return "";
        // Bytes, not chars — the CHECK constraint counts octets.
        if (s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < RAW_PAYLOAD_MAX_BYTES) return s;
        // Coarse: drop to half the byte budget in chars. UTF-8 worst case
        // is 4 bytes per char, so half-bytes-as-chars is always under cap.
        int safeChars = Math.min(s.length(), RAW_PAYLOAD_MAX_BYTES / 4);
        log.warn("Truncating dead-letter raw_payload from {} chars to {} (over 1MB cap)",
                s.length(), safeChars);
        return s.substring(0, safeChars);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}

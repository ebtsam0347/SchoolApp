package com.forward.hrms.commons.kafka.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Drains the outbox to Kafka. Runs in a single tx per batch — rows are
 * marked SENT (or PENDING with backoff / FAILED on the Nth retry) in the
 * same commit, so a poller crash mid-batch retries safely.
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository repo;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties props;

    @Scheduled(fixedDelayString = "${hrms.outbox.poll-delay:PT1S}")
    @Transactional
    public void poll() {
        List<OutboxEntry> batch = repo.findReadyToSend(LocalDateTime.now(), PageRequest.of(0, props.batchSize()));
        if (batch.isEmpty()) return;

        for (OutboxEntry e : batch) {
            try {
                kafka.send(e.getTopic(), e.getMessageKey(), e.getPayload())
                        .get(props.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                e.setStatus(OutboxEntry.Status.SENT);
                e.setSentAt(LocalDateTime.now());
                e.setLastError(null);
            } catch (Exception ex) {
                e.setRetryCount(e.getRetryCount() + 1);
                e.setLastError(truncate(ex.getMessage()));
                e.setNextAttemptAt(LocalDateTime.now().plus(backoff(e.getRetryCount())));
                if (e.getRetryCount() >= props.maxRetries()) {
                    e.setStatus(OutboxEntry.Status.FAILED);
                    log.error("Outbox row {} permanently failed after {} attempts: {}", e.getId(), e.getRetryCount(), ex.getMessage());
                }
            }
        }
    }

    private static Duration backoff(int retry) {
        long seconds = (long) Math.min(300, Math.pow(2, retry));
        return Duration.ofSeconds(seconds);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 1000 ? s : s.substring(0, 1000);
    }
}

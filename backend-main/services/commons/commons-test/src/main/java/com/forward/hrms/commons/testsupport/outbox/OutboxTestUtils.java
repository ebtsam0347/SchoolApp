package com.forward.hrms.commons.testsupport.outbox;

import com.forward.hrms.commons.kafka.outbox.OutboxPublisher;
import com.forward.hrms.commons.kafka.outbox.OutboxRepository;

import java.time.Duration;

/**
 * Drains the outbox synchronously so tests don't have to wait on the
 * scheduled poller. Call after the business action; assert on the
 * downstream consumer / repo state.
 *
 * <p>callers: 0 — will become live with the first integration test that
 * exercises an outbox-publishing path.
 */
public final class OutboxTestUtils {

    private OutboxTestUtils() {}

    public static void flush(OutboxPublisher publisher) {
        publisher.poll();
    }

    public static void flushUntilEmpty(OutboxPublisher publisher, OutboxRepository repo, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            publisher.poll();
            long pending = repo.findReadyToSend(java.time.LocalDateTime.now(),
                    org.springframework.data.domain.PageRequest.of(0, 1)).size();
            if (pending == 0) return;
            try { Thread.sleep(50); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); return;
            }
        }
        throw new AssertionError("Outbox did not drain within " + timeout);
    }
}

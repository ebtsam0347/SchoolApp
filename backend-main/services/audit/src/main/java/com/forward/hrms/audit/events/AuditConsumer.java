package com.forward.hrms.audit.events;

import com.forward.hrms.audit.service.AuditDeadLetterService;
import com.forward.hrms.audit.service.AuditIngestService;
import com.forward.hrms.audit.service.AuditIngestService.IngestOutcome;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * One {@code @KafkaListener} per per-service audit topic. Each runs in its
 * own consumer group ({@code audit.<svc>}) so commit offsets, lag metrics
 * and replay-by-service stay independent — see audit.md §2.
 *
 * <p>Manual ack: the offset is committed only after {@code ingest} returns
 * (which itself only returns after the row is persisted) AND, on a
 * SKIPPED outcome, after the dead-letter row is persisted. The container
 * is configured for {@code ack-mode: manual_immediate} so any exception
 * before {@link Acknowledgment#acknowledge()} leaves the offset
 * uncommitted and Kafka redelivers — required so the silent-loss bug
 * fixed in 7f5d8c3 stays fixed (a producer-fix-then-replay path now
 * exists, but only because rejected envelopes are durable in
 * {@code audit_dead_letters}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditConsumer {

    private final AuditIngestService ingest;
    private final AuditDeadLetterService deadLetters;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "auth.audit", groupId = "audit.auth")
    public void onAuth(ConsumerRecord<String, String> rec, Acknowledgment ack) { handle("auth", rec, ack); }

    @KafkaListener(topics = "employee.audit", groupId = "audit.employee")
    public void onEmployee(ConsumerRecord<String, String> rec, Acknowledgment ack) { handle("employee", rec, ack); }

    @KafkaListener(topics = "attendance.audit", groupId = "audit.attendance")
    public void onAttendance(ConsumerRecord<String, String> rec, Acknowledgment ack) { handle("attendance", rec, ack); }

    @KafkaListener(topics = "payroll.audit", groupId = "audit.payroll")
    public void onPayroll(ConsumerRecord<String, String> rec, Acknowledgment ack) { handle("payroll", rec, ack); }

    @KafkaListener(topics = "notification.audit", groupId = "audit.notification")
    public void onNotification(ConsumerRecord<String, String> rec, Acknowledgment ack) { handle("notification", rec, ack); }

    @KafkaListener(topics = "chat.audit", groupId = "audit.chat")
    public void onChat(ConsumerRecord<String, String> rec, Acknowledgment ack) { handle("chat", rec, ack); }

    private void handle(String source, ConsumerRecord<String, String> rec, Acknowledgment ack) {
        IngestOutcome outcome = ingest.ingest(source, rec.value());
        if (outcome.status() == IngestOutcome.Status.INSERTED) {
            log.debug("Ingested audit from {} (offset={}, key={})", rec.topic(), rec.offset(), rec.key());
        } else if (outcome.status() == IngestOutcome.Status.SKIPPED) {
            // If this throws, we deliberately don't ack — Kafka redelivers
            // and the operator wakes up to a consumer-lag alert (better
            // than the silent loss the dead-letter table is here to fix).
            deadLetters.record(source, rec, outcome);
            log.warn("Dead-lettered audit envelope from {} ({}@{}/{}): reason={}",
                    source, rec.topic(), rec.partition(), rec.offset(), outcome.reason());
        }
        // §7.audit #33 — per-source / per-result Counter so HR + ops can
        // see ingest rate, duplicate rate, and skipped rate over time
        // (Prometheus alerts on a sudden SKIPPED spike point at a buggy
        // emitter; a sustained DUPLICATE spike points at consumer-group
        // misconfiguration). SKIPPED counter is now tagged with the
        // SkipReason for finer-grained alerting / dashboards.
        var counter = meterRegistry.counter("audit.ingest.outcome",
                "source", source,
                "result", outcome.status().name(),
                "reason", outcome.reason() == null ? "none" : outcome.reason().name());
        counter.increment();
        ack.acknowledge();
    }
}

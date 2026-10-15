package com.forward.hrms.commons.kafka.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.commons.core.context.CorrelationId;
import com.forward.hrms.commons.kafka.event.EventEnvelope;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Write events to the outbox INSIDE the business {@code @Transactional}.
 * The poller picks them up and publishes to Kafka. Same DB connection,
 * same commit boundary — events go out iff the business write commits.
 *
 * <p>Propagation is {@link Propagation#MANDATORY} on purpose: if a caller
 * forgot to start a transaction, fail loudly rather than silently
 * publish without the business state.
 */
@RequiredArgsConstructor
public class Outbox {

    private final OutboxRepository repo;
    private final ObjectMapper json;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, String key, String eventType, Object payload) {
        publish(topic, key, eventType, payload, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, String key, String eventType, Object payload, boolean audit) {
        EventEnvelope<Object> envelope = EventEnvelope.wrap(eventType, payload);
        OutboxEntry e = new OutboxEntry();
        e.setTopic(topic);
        e.setMessageKey(key);
        e.setPayload(toJson(envelope));
        e.setAudit(audit);
        e.setCorrelationId(CorrelationId.get().orElse(null));
        e.setCreatedAt(LocalDateTime.now());
        e.setStatus(OutboxEntry.Status.PENDING);
        repo.save(e);
    }

    private String toJson(Object o) {
        try { return json.writeValueAsString(o); }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }
}

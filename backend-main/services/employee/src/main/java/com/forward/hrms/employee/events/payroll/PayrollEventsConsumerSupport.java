package com.forward.hrms.employee.events.payroll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

public abstract class PayrollEventsConsumerSupport {

    private final ObjectMapper json;

    protected PayrollEventsConsumerSupport(ObjectMapper json) {
        this.json = json;
    }

    /**
     * Receives Outbox message JSON and dispatches it
     * to the concrete listener.
     */
    protected final void dispatch(String message) throws IOException {

        JsonNode root = json.readTree(message);

        String eventType = root.path("eventType").asText(null);

        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException(
                    "Kafka event does not contain a valid eventType."
            );
        }

        JsonNode payload = root.get("payload");

        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException(
                    "Kafka event does not contain a payload."
            );
        }

        handle(
                eventType,
                payload
        );
    }

    /**
     * Every listener implements this.
     */
    protected abstract void handle(
            String eventType,
            JsonNode payload
    );

    /**
     * Reads LocalDate from payload.
     */
    protected LocalDate readDate(
            JsonNode node,
            String field
    ) {

        if (node == null) {
            return null;
        }

        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        return LocalDate.parse(value.asText());
    }

    /**
     * Reads LocalDateTime from payload.
     */
    protected LocalDateTime readDateTime(
            JsonNode node,
            String field
    ) {

        if (node == null) {
            return null;
        }

        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        return LocalDateTime.parse(value.asText());
    }

    /**
     * Reads a Long value from payload.
     */
    protected Long readLong(
            JsonNode node,
            String field
    ) {

        if (node == null) {
            return null;
        }

        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        return value.asLong();
    }

    /**
     * Reads a String value from payload.
     */
    protected String readString(
            JsonNode node,
            String field
    ) {

        if (node == null) {
            return null;
        }

        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        return value.asText();
    }
}
package com.forward.hrms.commons.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Per-step view onto the saga row. Steps read inputs / accumulated state
 * from {@link #payload()} and write back into the same {@link ObjectNode} —
 * the runner serializes the (mutated) node into {@code saga_state.payload}
 * after the step returns.
 *
 * <p>Sensitive values registered via
 * {@code SagaService.startWithSecrets} are accessed through
 * {@link #secret(String)}; they live in the {@link SagaSecretStore} and
 * never appear in the payload jsonb. Asking for an unknown secret —
 * including in a saga that wasn't started with a secret store —
 * throws {@link IllegalStateException} loudly so a step's reliance on
 * a missing secret fails immediately rather than silently using null.
 */
public final class SagaContext {

    private final long sagaId;
    private final String correlationKey;
    private final ObjectNode payload;
    private final int attempt;
    private final Function<String, String> secretLookup;

    public SagaContext(long sagaId, String correlationKey, ObjectNode payload, int attempt) {
        this(sagaId, correlationKey, payload, attempt, null);
    }

    public SagaContext(long sagaId, String correlationKey, ObjectNode payload, int attempt,
                       Function<String, String> secretLookup) {
        this.sagaId = sagaId;
        this.correlationKey = correlationKey;
        this.payload = payload;
        this.attempt = attempt;
        this.secretLookup = secretLookup;
    }

    public long sagaId() { return sagaId; }
    public String correlationKey() { return correlationKey; }
    public ObjectNode payload() { return payload; }
    public int attempt() { return attempt; }

    /**
     * Fetch a secret registered at saga-start. Throws when no secret
     * store is wired or when the named secret isn't present (probably
     * past its TTL).
     */
    public String secret(String name) {
        if (secretLookup == null) {
            throw new IllegalStateException(
                    "No SagaSecretStore wired for saga " + sagaId
                            + " — was it started with startWithSecrets?");
        }
        String value = secretLookup.apply(name);
        if (value == null) {
            throw new IllegalStateException(
                    "Saga secret '" + name + "' not found for saga " + sagaId
                            + " (TTL expired, or never registered)");
        }
        return value;
    }

    /**
     * The kickoff caller's {@code X-User-*} header snapshot, written into
     * the payload at saga start by {@link #writeIdentity}. Saga steps replay
     * it on outbound HTTP calls so the downstream identity filter accepts
     * the runner-thread request. Returns an empty map when no identity was
     * captured.
     */
    public Map<String, String> identity() {
        Map<String, String> headers = new LinkedHashMap<>();
        JsonNode node = payload.path("identity");
        if (!node.isObject()) return headers;
        node.properties().forEach(en -> headers.put(en.getKey(), en.getValue().asText()));
        return headers;
    }

    /**
     * Write a kickoff caller's identity snapshot into a saga payload under
     * the conventional {@code identity} key. Call at saga start (from the
     * request-bound thread); steps read it back via {@link #identity()}.
     */
    public static void writeIdentity(ObjectNode payload, Map<String, String> snapshot) {
        ObjectNode identity = payload.putObject("identity");
        if (snapshot != null) snapshot.forEach(identity::put);
    }
}

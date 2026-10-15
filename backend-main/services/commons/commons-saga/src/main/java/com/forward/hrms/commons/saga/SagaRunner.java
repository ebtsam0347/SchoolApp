package com.forward.hrms.commons.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Drives saga rows. Polls for due {@code RUNNING} rows, advances each by
 * one step inside its own transaction. One step = one tx so a long step
 * doesn't hold the row lock for the whole batch and a crash mid-batch
 * just re-runs the affected step (steps must be idempotent — that
 * contract is on the step).
 *
 * <p>Single-poller-per-instance assumption: only one bean polls per JVM,
 * so there's no need for {@code FOR UPDATE SKIP LOCKED} (the JPQL query
 * returns ids and each {@link #advanceOne} re-reads with default
 * isolation). Switch to a native query with skip-locked if we ever scale
 * this out horizontally.
 */
@Slf4j
public class SagaRunner {

    private final SagaRepository repo;
    private final ObjectProvider<SagaRunner> self;
    private final SagaRegistry registry;
    private final ObjectMapper json;
    private final SagaProperties props;
    private final SagaSecretStore secretStore;

    public SagaRunner(SagaRepository repo, ObjectProvider<SagaRunner> self, SagaRegistry registry,
                      ObjectMapper json, SagaProperties props, SagaSecretStore secretStore) {
        this.repo = repo;
        this.self = self;
        this.registry = registry;
        this.json = json;
        this.props = props;
        this.secretStore = secretStore;
    }

    @Scheduled(fixedDelayString = "${hrms.saga.poll-delay:PT2S}")
    public void poll() {
        List<Long> due = repo.findDueIds(LocalDateTime.now(), PageRequest.of(0, props.batchSize()));
        if (due.isEmpty()) return;
        for (Long id : due) {
            try {
                self.getObject().advanceOne(id);
            } catch (Exception ex) {
                log.error("Saga {} advance threw — will retry on next poll", id, ex);
            }
        }
    }

    @Transactional
    public void advanceOne(long id) {
        SagaState s = repo.findById(id).orElse(null);
        if (s == null || s.getStatus() != SagaState.Status.RUNNING) return;
        if (s.getNextAttemptAt() != null && s.getNextAttemptAt().isAfter(LocalDateTime.now())) return;

        Saga saga = registry.byName(s.getSagaName());
        if (saga == null) {
            fail(s, "UNKNOWN_SAGA", "No Saga bean registered for name=" + s.getSagaName());
            return;
        }

        List<SagaStep> steps = saga.steps();
        if (s.getStepIndex() >= steps.size()) {
            complete(s);
            return;
        }
        SagaStep step = steps.get(s.getStepIndex());

        ObjectNode payload;
        try {
            JsonNode parsed = json.readTree(s.getPayload());
            payload = parsed instanceof ObjectNode on ? on : json.createObjectNode();
        } catch (Exception ex) {
            fail(s, "PAYLOAD_PARSE", "Failed to parse payload: " + ex.getMessage());
            return;
        }

        java.util.function.Function<String, String> secretLookup = secretStore == null
                ? null
                : name -> secretStore.get(s.getId(), name);
        SagaContext ctx = new SagaContext(s.getId(), s.getCorrelationKey(), payload,
                s.getAttemptCount(), secretLookup);
        StepResult result;
        // Replay the kickoff caller's identity onto this scheduled thread
        // so any audit/event emitted by the step is credited to the user
        // who started the saga, not "system". HTTP forwarding already uses
        // the same payload.identity snapshot per call; this just covers
        // the local emits (EventEnvelope.wrap, AuditedEntityListener).
        CurrentUser replayed = identityFromPayload(payload.path("identity"));
        boolean userSet = false;
        if (replayed != null) {
            CurrentUserHolder.set(replayed);
            userSet = true;
        }
        try {
            result = step.execute(ctx);
        } catch (Exception ex) {
            log.warn("Saga {} step {} threw — will retry", s.getId(), step.name(), ex);
            result = StepResult.retry(ex.getClass().getSimpleName() + ": " + nullToEmpty(ex.getMessage()));
        } finally {
            if (userSet) CurrentUserHolder.clear();
        }

        switch (result) {
            case StepResult.Done ignored -> {
                int next = s.getStepIndex() + 1;
                s.setAttemptCount(0);
                s.setLastError(null);
                if (next >= steps.size()) {
                    complete(s);
                } else {
                    s.setStepIndex(next);
                    s.setCurrentStep(steps.get(next).name());
                    s.setNextAttemptAt(LocalDateTime.now());
                }
            }
            case StepResult.Retry r -> {
                int n = s.getAttemptCount() + 1;
                s.setAttemptCount(n);
                s.setLastError(truncate(r.reason()));
                if (n >= props.maxAttemptsPerStep()) {
                    fail(s, "RETRY_BUDGET_EXHAUSTED",
                            "Step " + step.name() + " failed " + n + " times: " + nullToEmpty(r.reason()));
                } else {
                    s.setNextAttemptAt(LocalDateTime.now().plus(backoff(n)));
                }
            }
            case StepResult.Failed f -> fail(s, f.code(), f.message());
        }

        s.setPayload(payload.toString());
        s.setUpdatedAt(LocalDateTime.now());
        s.setUpdatedBy("system");
    }

    private void complete(SagaState s) {
        s.setStatus(SagaState.Status.COMPLETED);
        s.setNextAttemptAt(null);
        dropSecrets(s);
    }

    private void fail(SagaState s, String code, String message) {
        s.setStatus(SagaState.Status.FAILED);
        s.setLastError(truncate(code + ": " + nullToEmpty(message)));
        s.setNextAttemptAt(null);
        // Keep secrets on FAILED so a manual `POST /sagas/{id}/retry` can
        // still feed them back into the failing step (the canonical case
        // is onboarding's temporary password — HR fixes the upstream
        // problem and re-runs without re-entering a password). The
        // SagaSecretStore TTL (default 72h) is the cleanup backstop;
        // anything stale enough to age out is also stale enough that a
        // human-driven retry would no longer be the right move.
    }

    /**
     * Drop saga secrets on COMPLETED only. FAILED retains the hash so
     * {@code POST /sagas/{id}/retry} can re-feed it. Best-effort — Redis
     * outage during cleanup is logged and swallowed; the TTL covers it.
     */
    private void dropSecrets(SagaState s) {
        if (secretStore == null) return;
        try {
            secretStore.remove(s.getId());
        } catch (RuntimeException ex) {
            log.warn("Saga {} secret cleanup failed (TTL will catch up): {}", s.getId(), ex.getMessage());
        }
    }

    private Duration backoff(int attempt) {
        long base = props.backoffBase().toSeconds();
        long cap = props.backoffCap().toSeconds();
        long secs = (long) Math.min(cap, base * Math.pow(2, attempt - 1));
        return Duration.ofSeconds(Math.max(1, secs));
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 2000 ? s : s.substring(0, 2000);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    // Header names mirror commons-web IdentityHeaders. Inlined here to
    // avoid pulling commons-web into commons-saga; the wire contract is
    // the keys callers already write into payload.identity.
    private static CurrentUser identityFromPayload(JsonNode identity) {
        if (identity == null || !identity.isObject()) return null;
        String userIdRaw = textHeader(identity, "X-User-Id");
        if (userIdRaw == null) return null;
        long userId;
        try {
            userId = Long.parseLong(userIdRaw);
        } catch (NumberFormatException ex) {
            return null;
        }
        long sessionId = 0L;
        String sess = textHeader(identity, "X-User-Session");
        if (sess != null) {
            try { sessionId = Long.parseLong(sess); } catch (NumberFormatException ignored) { /* leave 0 */ }
        }
        return new CurrentUser(
                userId,
                textHeader(identity, "X-User-Name"),
                textHeader(identity, "X-User-FullName"),
                sessionId,
                parseSet(textHeader(identity, "X-User-Roles")),
                parseSet(textHeader(identity, "X-User-Permissions")));
    }

    private static String textHeader(JsonNode n, String name) {
        JsonNode v = n.path(name);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static Set<String> parseSet(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        Set<String> out = new HashSet<>();
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}

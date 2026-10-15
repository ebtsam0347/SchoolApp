package com.forward.hrms.commons.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.context.RequestContext;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Caller-facing API. {@link #start} is {@link Propagation#MANDATORY} —
 * always invoked inside the kickoff business transaction so a saga row
 * commits atomically with the work that justifies it (mirrors the
 * outbox).
 *
 * <p>Idempotency: re-issuing {@code start} for an existing
 * {@code (sagaName, correlationKey)} returns the existing row regardless
 * of status — RUNNING continues, COMPLETED/FAILED are surfaced unchanged.
 * Restart of a FAILED saga is explicit via {@link #retry}.
 */
public class SagaService {

    /** Bounded TTL for saga secrets when callers don't override. Generous
     *  enough to survive the runner's full retry budget plus operator
     *  intervention windows; short enough that an abandoned FAILED saga
     *  doesn't leave secrets in Redis indefinitely. */
    public static final Duration DEFAULT_SECRET_TTL = Duration.ofHours(72);

    private final SagaRepository repo;
    private final SagaRegistry registry;
    private final ObjectMapper json;
    private final SagaSecretStore secretStore;

    public SagaService(SagaRepository repo, SagaRegistry registry, ObjectMapper json,
                       SagaSecretStore secretStore) {
        this.repo = repo;
        this.registry = registry;
        this.json = json;
        this.secretStore = secretStore;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SagaState start(String sagaName, String correlationKey, JsonNode payload) {
        return startInternal(sagaName, correlationKey, payload, Map.of(), DEFAULT_SECRET_TTL);
    }

    /**
     * Like {@link #start} but stashes {@code secrets} in the
     * {@link SagaSecretStore} (Redis-backed by default) instead of the
     * jsonb {@code saga_state.payload}. Steps fetch values via
     * {@link SagaContext#secret(String)}. The inspection endpoint
     * ({@code GET /sagas/{id}}) never sees them.
     *
     * <p>Throws {@link IllegalStateException} if no
     * {@link SagaSecretStore} bean is wired (services that don't include
     * Redis but try to call this method).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SagaState startWithSecrets(String sagaName, String correlationKey,
                                      JsonNode payload, Map<String, String> secrets,
                                      Duration ttl) {
        if (secretStore == null) {
            throw new IllegalStateException(
                    "startWithSecrets requires a SagaSecretStore bean — add spring-boot-starter-data-redis "
                            + "to the service or wire a SagaSecretStore manually.");
        }
        return startInternal(sagaName, correlationKey, payload, secrets,
                ttl == null ? DEFAULT_SECRET_TTL : ttl);
    }

    private SagaState startInternal(String sagaName, String correlationKey, JsonNode payload,
                                    Map<String, String> secrets, Duration secretTtl) {
        Optional<SagaState> existing = repo.findBySagaNameAndCorrelationKey(sagaName, correlationKey);
        if (existing.isPresent()) return existing.get();

        Saga saga = registry.byName(sagaName);
        if (saga == null) {
            throw new IllegalStateException("Unknown saga: " + sagaName);
        }
        if (saga.steps().isEmpty()) {
            throw new IllegalStateException("Saga " + sagaName + " has no steps");
        }

        LocalDateTime now = LocalDateTime.now();
        String actor = CurrentUserHolder.get().map(u -> u.username()).orElse("system");
        String ip = RequestContext.current().map(RequestContext::ipAddress).orElse(null);

        SagaState s = new SagaState();
        s.setSagaName(sagaName);
        s.setCorrelationKey(correlationKey);
        s.setStatus(SagaState.Status.RUNNING);
        s.setCurrentStep(saga.steps().get(0).name());
        s.setStepIndex(0);
        s.setAttemptCount(0);
        s.setNextAttemptAt(now);
        s.setPayload(payload == null ? "{}" : payload.toString());
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        s.setCreatedBy(actor);
        s.setUpdatedBy(actor);
        s.setIpAddress(ip);
        SagaState saved = repo.save(s);
        if (secretStore != null && secrets != null && !secrets.isEmpty()) {
            // Stamped after the row is saved so we have an id; flushed by
            // the surrounding tx commit. Redis writes survive a rollback
            // (no participation in JTA) — that's acceptable: orphaned
            // secrets just age out via TTL and never re-attach to a new
            // saga because they're keyed by sagaId.
            secretStore.put(saved.getId(), secrets, secretTtl);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<SagaState> findById(long id) {
        return repo.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<SagaState> findByKey(String sagaName, String correlationKey) {
        return repo.findBySagaNameAndCorrelationKey(sagaName, correlationKey);
    }

    /**
     * Filtered listing for operator views and the admin dashboard. Pass
     * {@code null} for either filter to skip it. Pageable controls sort —
     * callers typically want {@code updatedAt,desc} so the freshest
     * failures show first.
     */
    @Transactional(readOnly = true)
    public Page<SagaState> search(SagaState.Status status, String name, Pageable pageable) {
        return repo.search(status, name, pageable);
    }

    /**
     * Aggregate health for the admin dashboard. One {@code count(*)} for
     * the RUNNING headline and one ranged scan over FAILED rows
     * transitioned in the last 24h, bucketed into 24 hourly slots in
     * Java. Saga rows are bounded (a few thousand at most over the
     * lifetime of a service) so the scan is cheap; if it ever isn't,
     * swap the second query for a {@code date_trunc('hour', ...)}
     * native aggregate.
     */
    @Transactional(readOnly = true)
    public SagaHealthSnapshot health() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minus(Duration.ofHours(24));
        long running = repo.countRunning();

        // Per-name initial state — seed entries for every name that has
        // RUNNING work, even if it had zero failures.
        Map<String, int[]> bucketsByName = new LinkedHashMap<>();
        for (Object[] row : repo.countRunningByName()) {
            bucketsByName.put((String) row[0], new int[24]);
        }

        // Bucket index = hours-from-now-23 backwards. Bucket 23 = current
        // hour, bucket 0 = 23 hours ago. Floor to whole hours so the
        // boundaries stay stable over the request.
        long nowHourEpoch = now.atZone(ZoneId.systemDefault()).toEpochSecond() / 3600L;
        int failed24h = 0;
        for (Object[] row : repo.findFailedSince(cutoff)) {
            String name = (String) row[0];
            LocalDateTime when = (LocalDateTime) row[1];
            long hourEpoch = when.atZone(ZoneId.systemDefault()).toEpochSecond() / 3600L;
            int idx = 23 - (int) (nowHourEpoch - hourEpoch);
            if (idx < 0 || idx > 23) continue;
            int[] arr = bucketsByName.computeIfAbsent(name, k -> new int[24]);
            arr[idx]++;
            failed24h++;
        }

        List<SagaHealthSnapshot.PerName> perName = new ArrayList<>(bucketsByName.size());
        for (Map.Entry<String, int[]> e : bucketsByName.entrySet()) {
            int[] arr = e.getValue();
            List<Integer> series = new ArrayList<>(24);
            int total = 0;
            for (int v : arr) { series.add(v); total += v; }
            String status = total == 0 ? "OK" : (total > 3 ? "INCIDENT" : "DEGRADED");
            perName.add(new SagaHealthSnapshot.PerName(e.getKey(), status, series));
        }
        return new SagaHealthSnapshot((int) Math.min(Integer.MAX_VALUE, running), failed24h, perName);
    }

    /**
     * Drop the saga row keyed by {@code (sagaName, correlationKey)} plus any
     * Redis secrets. Used by business operations that semantically wipe a
     * prior saga attempt and want the next {@link #start} to schedule fresh
     * work — payroll {@code reset()} is the canonical caller. Without this,
     * {@code start}'s {@code (sagaName, correlationKey)}-keyed idempotency
     * returns the terminal-state row from the prior attempt and the runner
     * has nothing to advance. Returns the number of rows deleted (0 if none).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int clear(String sagaName, String correlationKey) {
        Optional<SagaState> existing = repo.findBySagaNameAndCorrelationKey(sagaName, correlationKey);
        if (existing.isEmpty()) return 0;
        SagaState s = existing.get();
        if (secretStore != null) {
            try { secretStore.remove(s.getId()); } catch (RuntimeException ignored) { /* TTL covers it */ }
        }
        repo.delete(s);
        return 1;
    }

    /**
     * Reset a {@code FAILED} saga to {@code RUNNING} at its current step
     * with {@code attempt_count=0}. No-op for sagas in any other state.
     */
    @Transactional
    public SagaState retry(long id) {
        SagaState s = repo.findById(id).orElseThrow(
                () -> new IllegalStateException("Saga not found: " + id));
        if (s.getStatus() != SagaState.Status.FAILED) return s;
        s.setStatus(SagaState.Status.RUNNING);
        s.setAttemptCount(0);
        s.setLastError(null);
        s.setNextAttemptAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        s.setUpdatedBy(CurrentUserHolder.get().map(u -> u.username()).orElse("system"));
        return s;
    }
}

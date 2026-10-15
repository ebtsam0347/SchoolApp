package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.entity.PunchUnmatched;
import com.forward.hrms.attendance.repository.PunchUnmatchedRepository;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Drains the {@code attendance_punch_unmatched} queue. The poller writes
 * unknown-bridge punches into the queue at ingest time
 * ({@code PunchService#queueUnmatched}); this job picks up rows whose
 * bridge entry now exists, replays them as biometric punches, and
 * deletes them from the queue.
 *
 * <p>Rows past {@code hrms.attendance.poller.unmatched-max-attempts}
 * (default 8) are dropped + audit-logged so a permanently
 * misconfigured card doesn't grow the queue forever. Equivalent of the
 * old poller-side {@code RETRY_MAX_ATTEMPTS}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevicePunchReplayJob {

    private final PunchUnmatchedRepository unmatched;
    private final PunchService punches;
    private final AuditEmitter audit;

    @Value("${hrms.attendance.poller.unmatched-max-attempts:8}")
    private int maxAttempts;

    @Value("${hrms.attendance.poller.replay-batch-size:10000}")
    private int batchSize;

    /**
     * Every 60s — small enough that HR adding a missing bridge row
     * gets reflected on the next attendance day's projection, big
     * enough that a quiet system isn't doing repeated empty scans.
     * Defaults match {@code application.yml} so a stripped YAML
     * doesn't silently downgrade.
     */
    @Scheduled(fixedDelayString = "${hrms.attendance.poller.replay-interval-ms:60000}",
            initialDelayString = "${hrms.attendance.poller.replay-initial-delay-ms:60000}")
    @Transactional
    public void replay() {
        long t0 = System.currentTimeMillis();
        List<PunchUnmatched> resolvable = unmatched.findResolvable(PageRequest.of(0, batchSize));
        if (resolvable.isEmpty()) {
            log.debug("replay tick: no resolvable rows");
            return;
        }
        log.info("replay tick: starting, resolvable={} (batch_size={})", resolvable.size(), batchSize);

        List<PunchService.BiometricRow> rows = new ArrayList<>(resolvable.size());
        List<Long> idsToDelete = new ArrayList<>(resolvable.size());
        List<Long> idsToDrop = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        for (PunchUnmatched u : resolvable) {
            int next = u.getAttempts() + 1;
            if (next > maxAttempts) {
                idsToDrop.add(u.getId());
                continue;
            }
            u.setAttempts(next);
            u.setLastAttemptAt(now);
            rows.add(new PunchService.BiometricRow(
                    u.getEmployeeCode(), u.getPunchedAt(), u.getDeviceId()));
            idsToDelete.add(u.getId());
        }

        if (!rows.isEmpty()) {
            // The snapshot row is in place by the time findResolvable()
            // returns, so the replay should ingest cleanly. We still
            // call the same ingest path to apply duplicate detection +
            // recompute.
            var result = punches.ingestBiometricBatch(rows);
            log.info("unmatched replay: ingested={} skipped={} dropped={}",
                    result.ingested().size(), result.skipped().size(), idsToDrop.size());
            unmatched.deleteByIdIn(idsToDelete);
        }

        if (!idsToDrop.isEmpty()) {
            for (Long id : idsToDrop) {
                audit.emit("attendance_punch_unmatched", String.valueOf(id), "dropped",
                        Map.of("attempts", maxAttempts),
                        Map.of("reason", "max-attempts exceeded"), false);
            }
            unmatched.deleteByIdIn(idsToDrop);
        }

        log.info("replay tick: done in {}ms", System.currentTimeMillis() - t0);
    }
}

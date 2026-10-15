package com.forward.hrms.payroll.service;

import com.forward.hrms.payroll.entity.ReportJob;
import com.forward.hrms.payroll.enums.ReportJobStatus;
import com.forward.hrms.payroll.repository.ReportJobRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Polls {@code report_jobs} for QUEUED rows and dispatches each to the
 * matching {@link ReportJobHandler}. Same shape as
 * {@code commons-saga}'s {@code SagaRunner} and the outbox poller.
 *
 * <p>Single-poller-per-instance assumption: the handler runs on the
 * {@code scheduling} thread; multiple payroll-service instances would
 * each pick up jobs independently. The QUEUED → RUNNING transition
 * happens inside its own tiny tx ({@link #tryClaim}); a row claimed by
 * one instance is invisible to the next poll cycle on the other.
 *
 * <p>The handler call itself is OUTSIDE any transaction — Excel renders
 * for full pay registers can take tens of seconds and would otherwise
 * pin a Hikari connection.
 */
@Slf4j
@Service
public class ReportJobRunner {

    private final ReportJobRepository jobs;
    private final Map<String, ReportJobHandler> handlersByType;
    private final ObjectProvider<ReportJobRunner> self;

    public ReportJobRunner(ReportJobRepository jobs,
                           List<ReportJobHandler> handlers,
                           ObjectProvider<ReportJobRunner> self) {
        this.jobs = jobs;
        this.self = self;
        Map<String, ReportJobHandler> map = new HashMap<>(handlers.size());
        for (ReportJobHandler h : handlers) {
            ReportJobHandler prev = map.put(h.type(), h);
            if (prev != null) {
                throw new IllegalStateException(
                        "Duplicate ReportJobHandler for type=" + h.type()
                                + " (" + prev.getClass().getSimpleName()
                                + " vs " + h.getClass().getSimpleName() + ")");
            }
        }
        this.handlersByType = Map.copyOf(map);
    }

    @Scheduled(fixedDelayString = "${hrms.payroll.report-jobs.poll-delay:PT2S}")
    public void poll() {
        List<ReportJob> queued = jobs.findOldestByStatus(ReportJobStatus.QUEUED, PageRequest.of(0, 5));
        if (queued.isEmpty()) return;
        for (ReportJob row : queued) {
            try {
                self.getObject().runOne(row.getId());
            } catch (Exception ex) {
                log.error("ReportJob {} run threw — will retry on next poll", row.getId(), ex);
            }
        }
    }

    /**
     * Single-job life-cycle: claim, dispatch, persist outcome. The
     * claim flips QUEUED → RUNNING in a short tx, the handler runs
     * outside any tx, and the success / failure persist runs in a
     * second short tx.
     */
    public void runOne(long id) {
        ReportJob claimed = self.getObject().tryClaim(id);
        if (claimed == null) return;

        ReportJobHandler handler = handlersByType.get(claimed.getType());
        if (handler == null) {
            self.getObject().markFailed(id, "UNKNOWN_TYPE",
                    "No handler registered for report type=" + claimed.getType());
            return;
        }

        try {
            ReportJobHandler.Output out = handler.handle(claimed);
            self.getObject().markReady(id, out.objectKey(), out.size());
        } catch (RuntimeException ex) {
            log.warn("ReportJob {} ({}) failed: {}", id, claimed.getType(), ex.toString(), ex);
            self.getObject().markFailed(id, "HANDLER_FAILED", truncate(ex.getMessage(), 2000));
        }
    }

    /**
     * Atomic QUEUED → RUNNING transition. Returns the row when the
     * claim succeeded, {@code null} when another poller already
     * claimed it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReportJob tryClaim(long id) {
        ReportJob row = jobs.findById(id).orElse(null);
        if (row == null || row.getStatus() != ReportJobStatus.QUEUED) return null;
        row.setStatus(ReportJobStatus.RUNNING);
        row.setStartedAt(LocalDateTime.now());
        return jobs.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReady(long id, String objectKey, long size) {
        ReportJob row = jobs.findById(id).orElse(null);
        if (row == null) return;
        row.setStatus(ReportJobStatus.READY);
        row.setOutputKey(objectKey);
        row.setOutputSize(size);
        row.setCompletedAt(LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(long id, String code, String message) {
        ReportJob row = jobs.findById(id).orElse(null);
        if (row == null) return;
        row.setStatus(ReportJobStatus.FAILED);
        row.setErrorCode(code);
        row.setErrorMessage(message);
        row.setCompletedAt(LocalDateTime.now());
    }

    private static String truncate(String s, int max) {
        if (s == null) return "(no message)";
        return s.length() <= max ? s : s.substring(0, max);
    }
}

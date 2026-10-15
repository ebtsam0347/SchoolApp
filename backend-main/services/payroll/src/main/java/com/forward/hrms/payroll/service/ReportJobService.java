package com.forward.hrms.payroll.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.payroll.entity.ReportJob;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.ReportJobStatus;
import com.forward.hrms.payroll.repository.ReportJobRepository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;

/**
 * Enqueues async-report jobs and serves status / download requests off
 * the {@code report_jobs} table. Type-specific param validation lives
 * inside each report controller (or its service); this layer just
 * persists the row + audit and signs the download URL.
 */
@Service
public class ReportJobService {

    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(10);

    private final ReportJobRepository jobs;
    private final ObjectStorage storage;
    private final AuditEmitter audit;
    private final ObjectMapper json;

    public ReportJobService(ReportJobRepository jobs,
                            ObjectStorage storage,
                            AuditEmitter audit,
                            @Qualifier("outboxObjectMapper") ObjectMapper json) {
        this.jobs = jobs;
        this.storage = storage;
        this.audit = audit;
        this.json = json;
    }

    @Transactional
    public ReportJob enqueue(String type, Map<String, Object> params) {
        ReportJob job = new ReportJob();
        job.setType(type);
        job.setParams(serialize(params));
        job.setStatus(ReportJobStatus.QUEUED);
        job.setRequestedBy(CurrentUserHolder.usernameOrSystem());
        ReportJob saved = jobs.save(job);
        audit.emit("report", String.valueOf(saved.getId()), type + ".enqueued",
                null, Map.of("type", type, "params", params), false);
        return saved;
    }

    @Transactional(readOnly = true)
    public ReportJob get(long id) {
        return jobs.findById(id).orElseThrow(() ->
                new NotFoundError(PayrollErrorCode.REPORT_JOB_NOT_FOUND,
                        "report job not found: " + id));
    }

    /**
     * Returns a short-lived signed GET URL for a READY job's output.
     * Bucket is the {@code reports} bucket the storage subsystem
     * ensures on startup; per-service prefixes keep collisions out.
     */
    public String signedDownloadUrl(ReportJob job) {
        if (job.getStatus() != ReportJobStatus.READY || job.getOutputKey() == null) {
            return null;
        }
        return storage.signedGetUrl("reports", job.getOutputKey(), DOWNLOAD_TTL);
    }

    private String serialize(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "{}";
        try {
            return json.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to serialize report-job params", e);
        }
    }
}

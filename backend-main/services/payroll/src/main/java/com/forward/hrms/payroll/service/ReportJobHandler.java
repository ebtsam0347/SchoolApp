package com.forward.hrms.payroll.service;

import com.forward.hrms.payroll.entity.ReportJob;

/**
 * Strategy implemented per async report type. The runner dispatches to
 * the handler whose {@link #type()} equals {@code job.getType()}.
 *
 * <p>Implementations render the report and upload the bytes to MinIO
 * themselves (so memory usage stays bounded for large outputs);
 * {@link #handle} returns the resulting object key + size, which the
 * runner persists onto the job row.
 *
 * <p>Handlers run on the {@code @Scheduled} thread, NOT inside a
 * transaction. Open / close JPA transactions internally for any DB
 * reads they need; do not block waiting on long-running tx.
 */
public interface ReportJobHandler {

    /** Discriminator written to {@code report_jobs.type}. */
    String type();

    /** Render + upload, return the resulting object key + size. */
    Output handle(ReportJob job);

    record Output(String objectKey, long size) {}
}

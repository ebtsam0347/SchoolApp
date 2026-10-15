package com.forward.hrms.payroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Service-level knobs.
 * <ul>
 *   <li>{@link #payslips()} — bucket name + signed URL TTL for payslip PDFs.</li>
 *   <li>{@link #standardMonthlyHours()} — divisor used to derive an
 *       hourly rate from monthly basic for OT calc. Pakistan SME default
 *       is 8 hours × 26 working days = 208.</li>
 * </ul>
 */
@ConfigurationProperties("hrms.payroll")
public record PayrollProperties(
        Payslips payslips,
        BigDecimal standardMonthlyHours
) {
    public PayrollProperties {
        if (payslips == null) payslips = new Payslips(null, null);
        if (standardMonthlyHours == null) standardMonthlyHours = new BigDecimal("208");
        // §7.payroll #7 — fail fast if the operator dropped 0 (or negative)
        // into application.yml; the OT divide would otherwise crash mid-run
        // on every employee. Better to refuse to start.
        if (standardMonthlyHours.signum() <= 0) {
            throw new IllegalStateException(
                    "hrms.payroll.standard-monthly-hours must be > 0; got " + standardMonthlyHours);
        }
    }

    public record Payslips(String bucket, Duration downloadUrlTtl) {
        public Payslips {
            if (bucket == null || bucket.isBlank()) bucket = "payslips";
            if (downloadUrlTtl == null) downloadUrlTtl = Duration.ofMinutes(10);
        }
    }
}

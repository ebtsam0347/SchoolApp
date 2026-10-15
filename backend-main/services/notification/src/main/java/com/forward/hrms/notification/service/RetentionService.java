package com.forward.hrms.notification.service;

import com.forward.hrms.notification.config.NotificationProperties;
import com.forward.hrms.notification.repository.NotificationRepository;
import com.forward.hrms.notification.repository.ProcessedEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Daily retention sweep. Two passes:
 * <ul>
 *   <li>PAYROLL category — kept for {@code payrollRetention} (default 4y,
 *       matches audit retention per {@code docs/policy.md} #9/#10).</li>
 *   <li>Everything else — kept for {@code retention} (default 180d).</li>
 * </ul>
 * Processed-event rows older than 7d are also pruned (Kafka redelivery
 * is bounded; the durable dedupe table doesn't need to grow forever).
 * Runs at 03:00 UTC, off the request path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final NotificationRepository notifications;
    private final ProcessedEventRepository processed;
    private final NotificationProperties props;

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void sweep() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime defaultCutoff = now.minus(props.retention());
        LocalDateTime payrollCutoff = now.minus(props.payrollRetention());

        int deletedDefault = notifications.deleteNonPayrollOlderThan(defaultCutoff);
        int deletedPayroll = notifications.deletePayrollOlderThan(payrollCutoff);

        LocalDateTime procCutoff = now.minusSeconds(7L * 24 * 3600);
        int deletedProcessed = processed.deleteOlderThan(procCutoff);

        log.info("retention sweep: deleted {} non-PAYROLL notifications older than {}, "
                        + "{} PAYROLL notifications older than {}, "
                        + "{} processed-event rows older than {}",
                deletedDefault, defaultCutoff,
                deletedPayroll, payrollCutoff,
                deletedProcessed, procCutoff);
    }
}

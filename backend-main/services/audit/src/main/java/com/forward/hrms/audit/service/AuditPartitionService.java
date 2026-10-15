package com.forward.hrms.audit.service;

import com.forward.hrms.audit.config.AuditProperties;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * §6.6 — declarative monthly partition maintenance for {@code audit_events}.
 *
 * <p>Two responsibilities, both idempotent:
 * <ul>
 *   <li><b>Forward extension.</b> Make sure partitions exist for the
 *       current month and every month within the configured look-ahead
 *       window. Without this, an ingest at the rollover boundary would
 *       fall into the {@code audit_events_default} catch-all (and log
 *       a warning).</li>
 *   <li><b>Retention sweep.</b> Drop any partition whose upper bound is
 *       at or before {@code today − retentionMonths}. Partitions are
 *       dropped wholesale (one DDL per partition) — orders of magnitude
 *       cheaper than wide DELETEs against the parent.</li>
 * </ul>
 *
 * <p>Partition naming convention is {@code audit_events_YYYYMM} (matches
 * the migration that originally pre-created them). The retention sweep
 * relies on the convention to find drop candidates without consulting
 * the catalog about pg_class oids.
 *
 * <p>Runs daily at 03:30 UTC — half an hour after notification's
 * retention sweep so the two don't compete for IO. Each run takes
 * milliseconds when nothing changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditPartitionService {

    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String PARTITION_PREFIX = "audit_events_";

    @PersistenceContext
    private final EntityManager em;
    private final AuditProperties props;

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    @Transactional
    public void maintain() {
        int created = ensureForward();
        int dropped = sweepExpired();
        long defaultRows = countDefaultPartitionRows();
        log.info("audit partition maintenance: created={}, dropped={}, "
                        + "defaultPartitionRows={} (retentionMonths={}, lookaheadMonths={})",
                created, dropped, defaultRows,
                props.partition().retentionMonths(),
                props.partition().lookaheadMonths());
        if (defaultRows > 0) {
            log.warn("audit_events_default holds {} rows — these slipped past the "
                    + "pre-created window. Investigate the upstream occurred_at clock "
                    + "and backfill a partition retroactively if needed.", defaultRows);
        }
    }

    /**
     * Create partitions for [current month, current month + lookaheadMonths]
     * if they don't exist yet. Returns the number of new partitions
     * created this run.
     */
    int ensureForward() {
        LocalDate startMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endMonth = startMonth.plusMonths(props.partition().lookaheadMonths());
        int created = 0;
        for (LocalDate m = startMonth; !m.isAfter(endMonth); m = m.plusMonths(1)) {
            if (createPartitionIfMissing(m)) created++;
        }
        return created;
    }

    /**
     * Drop partitions whose upper bound is at or before the retention
     * cutoff (today − retentionMonths, snapped to month start). The
     * cutoff is exclusive on the upper bound so the partition holding
     * "today − retentionMonths" itself stays — the rule reads "drop
     * once the entire month is past retention."
     */
    int sweepExpired() {
        LocalDate cutoff = LocalDate.now()
                .withDayOfMonth(1)
                .minusMonths(props.partition().retentionMonths());
        int dropped = 0;
        for (String partition : findManagedPartitions()) {
            LocalDate month = parseMonth(partition);
            if (month == null) continue;
            LocalDate upperBound = month.plusMonths(1);
            if (!upperBound.isAfter(cutoff)) {
                em.createNativeQuery("drop table if exists " + quote(partition)).executeUpdate();
                dropped++;
                log.info("dropped expired audit partition {} (covered {} → {}, cutoff {})",
                        partition, month, upperBound, cutoff);
            }
        }
        return dropped;
    }

    private boolean createPartitionIfMissing(LocalDate month) {
        String name = PARTITION_PREFIX + month.format(MONTH_KEY);
        if (partitionExists(name)) return false;
        String from = month.toString();
        String to = month.plusMonths(1).toString();
        em.createNativeQuery(
                "create table " + quote(name)
                        + " partition of audit_events"
                        + " for values from ('" + from + "') to ('" + to + "')")
                .executeUpdate();
        return true;
    }

    private boolean partitionExists(String name) {
        return !em.createNativeQuery("""
                select 1
                  from pg_class c
                  join pg_namespace n on n.oid = c.relnamespace
                 where n.nspname = 'audit'
                   and c.relname = :name
                """)
                .setParameter("name", name)
                .getResultList()
                .isEmpty();
    }

    @SuppressWarnings("unchecked")
    List<String> findManagedPartitions() {
        return em.createNativeQuery("""
                select c.relname
                  from pg_class c
                  join pg_namespace n on n.oid = c.relnamespace
                  join pg_inherits i on i.inhrelid = c.oid
                  join pg_class p on p.oid = i.inhparent
                 where n.nspname = 'audit'
                   and p.relname = 'audit_events'
                   and c.relname like 'audit_events_%'
                   and c.relname <> 'audit_events_default'
                 order by c.relname
                """).getResultList();
    }

    long countDefaultPartitionRows() {
        Object n = em.createNativeQuery("select count(*) from audit_events_default")
                .getSingleResult();
        return ((Number) n).longValue();
    }

    private static LocalDate parseMonth(String partition) {
        if (!partition.startsWith(PARTITION_PREFIX)) return null;
        String key = partition.substring(PARTITION_PREFIX.length());
        if (key.length() != 6) return null;
        try {
            int year = Integer.parseInt(key.substring(0, 4));
            int month = Integer.parseInt(key.substring(4, 6));
            return LocalDate.of(year, month, 1);
        } catch (NumberFormatException | java.time.DateTimeException e) {
            return null;
        }
    }

    /** Identifier safety — the only inputs we ever quote are partition
     *  names we generate ourselves from {@code yyyyMM}, but the helper
     *  enforces that no upstream input ever lands in DDL unquoted. */
    private static String quote(String identifier) {
        if (!identifier.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("refusing to quote identifier: " + identifier);
        }
        return "\"" + identifier + "\"";
    }
}

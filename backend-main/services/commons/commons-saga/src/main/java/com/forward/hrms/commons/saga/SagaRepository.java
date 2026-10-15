package com.forward.hrms.commons.saga;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SagaRepository extends JpaRepository<SagaState, Long> {

    Optional<SagaState> findBySagaNameAndCorrelationKey(String sagaName, String correlationKey);

    /**
     * Batched lookup for assemblers that resolve a saga state across a page
     * of business rows (e.g. {@code essProvisioningStatus} on the employee
     * directory). Returns at most {@code keys.size()} rows because of the
     * unique constraint on {@code (saga_name, correlation_key)}.
     */
    @Query("""
           select s from SagaState s
            where s.sagaName = :name
              and s.correlationKey in :keys
           """)
    List<SagaState> findByNameAndCorrelationKeyIn(@Param("name") String name,
                                                  @Param("keys") Collection<String> keys);

    /**
     * Returns ids of {@code RUNNING} rows whose {@code next_attempt_at} is
     * due. Single-poller-per-instance assumption: rows are processed
     * serially per instance, so we don't need {@code FOR UPDATE SKIP
     * LOCKED}. Switch to a native query with skip-locked if we ever run
     * multiple poller instances.
     */
    @Query("""
           select s.id from SagaState s
            where s.status = com.forward.hrms.commons.saga.SagaState$Status.RUNNING
              and (s.nextAttemptAt is null or s.nextAttemptAt <= :now)
            order by s.id asc
           """)
    List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable pageable);

    /**
     * Filtered listing for operator views and the admin dashboard.
     * {@code status} and {@code name} are independently optional.
     */
    @Query("""
           select s from SagaState s
            where (:status is null or s.status = :status)
              and (:name is null or s.sagaName = :name)
           """)
    Page<SagaState> search(@Param("status") SagaState.Status status,
                           @Param("name") String name,
                           Pageable pageable);

    /** Total RUNNING sagas — headline figure on the admin dashboard. */
    @Query("select count(s) from SagaState s where s.status = com.forward.hrms.commons.saga.SagaState$Status.RUNNING")
    long countRunning();

    /** Per-name running totals — feeds the perName slice when a saga has live work. */
    @Query("""
           select s.sagaName, count(s) from SagaState s
            where s.status = com.forward.hrms.commons.saga.SagaState$Status.RUNNING
            group by s.sagaName
           """)
    List<Object[]> countRunningByName();

    /**
     * (sagaName, updatedAt) tuples for FAILED rows transitioned in the
     * last 24h. The substrate stamps {@code updatedAt} on every status
     * change, so a FAILED row's {@code updatedAt} marks the moment it
     * failed. Caller buckets the timestamps into hourly slots.
     */
    @Query("""
           select s.sagaName, s.updatedAt from SagaState s
            where s.status = com.forward.hrms.commons.saga.SagaState$Status.FAILED
              and s.updatedAt >= :cutoff
           """)
    List<Object[]> findFailedSince(@Param("cutoff") LocalDateTime cutoff);
}

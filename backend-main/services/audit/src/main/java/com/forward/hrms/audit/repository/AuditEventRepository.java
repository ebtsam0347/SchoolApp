package com.forward.hrms.audit.repository;

import com.forward.hrms.audit.entity.AuditEvent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {

    Optional<AuditEvent> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<AuditEvent> findAllByCorrelationIdOrderByOccurredAtAsc(String correlationId);

    /** §7.audit — paginated variant; the unbounded list above can run to
     *  thousands of rows on long-running sagas with many emit sites. */
    Page<AuditEvent> findAllByCorrelationIdOrderByOccurredAtAsc(String correlationId, Pageable pageable);

    /*
     * Optional filter parameters use `cast(:param as Type)`. Without the
     * cast, Postgres receives an untyped `?` for the `is null` check and
     * fails with `42P18 — could not determine data type of parameter $N`.
     * The cast makes Hibernate bind a typed null, which Postgres accepts.
     */
    @Query("""
            select e from AuditEvent e
             where e.entityType = :entityType
               and e.entityId   = :entityId
               and (cast(:from as LocalDateTime) is null or e.occurredAt >= :from)
               and (cast(:to as LocalDateTime) is null or e.occurredAt <  :to)
             order by e.occurredAt desc, e.id desc
            """)
    List<AuditEvent> findByEntity(@Param("entityType") String entityType,
                                  @Param("entityId") String entityId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  Pageable pageable);

    /**
     * Cursor-paginated entity history. Same shape as {@link #findTimeline}
     * minus the {@code userVisible = true} filter. Drives both the admin
     * login-history view (auth proxies through this) and the self
     * sign-in-events feed.
     */
    @Query("""
            select e from AuditEvent e
             where e.entityType = :entityType
               and e.entityId   = :entityId
               and (cast(:from as LocalDateTime) is null or e.occurredAt >= :from)
               and (cast(:to as LocalDateTime) is null or e.occurredAt <  :to)
               and (cast(:cursorTs as LocalDateTime) is null
                    or e.occurredAt < :cursorTs
                    or (e.occurredAt = :cursorTs and e.id < :cursorId))
             order by e.occurredAt desc, e.id desc
            """)
    List<AuditEvent> findByEntityCursor(@Param("entityType") String entityType,
                                        @Param("entityId") String entityId,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        @Param("cursorTs") LocalDateTime cursorTs,
                                        @Param("cursorId") Long cursorId,
                                        Pageable pageable);

    @Query("""
            select e from AuditEvent e
             where e.entityType = :entityType
               and e.entityId   = :entityId
               and e.userVisible = true
               and (cast(:from as LocalDateTime)     is null or e.occurredAt >= :from)
               and (cast(:to as LocalDateTime)     is null or e.occurredAt <  :to)
               and (cast(:cursorTs as LocalDateTime) is null
                    or e.occurredAt < :cursorTs
                    or (e.occurredAt = :cursorTs and e.id < :cursorId))
             order by e.occurredAt desc, e.id desc
            """)
    List<AuditEvent> findTimeline(@Param("entityType") String entityType,
                                  @Param("entityId") String entityId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  @Param("cursorTs") LocalDateTime cursorTs,
                                  @Param("cursorId") Long cursorId,
                                  Pageable pageable);

    @Query("""
            select e from AuditEvent e
             where e.actorUserId = :actorUserId
               and (cast(:from as LocalDateTime) is null or e.occurredAt >= :from)
               and (cast(:to as LocalDateTime) is null or e.occurredAt <  :to)
             order by e.occurredAt desc, e.id desc
            """)
    Page<AuditEvent> findByActor(@Param("actorUserId") Long actorUserId,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 Pageable pageable);

    /*
     * Search migrated to JpaSpecificationExecutor — see
     * AuditEventSpecifications. The previous JPQL approach hit the
     * cast(?as boolean) trap when adding the userVisible filter; rather
     * than maintain two paths, the whole search uses Specifications
     * now. Pageable's sort is preserved by Specifications too.
     */

    /*
     * Facet queries for the explorer's filter dropdowns. Order by frequency
     * desc, then alphabetically for stable display. Returns {value, count}
     * pairs as Object[] — service-side maps them to a record. Cheap on a
     * dev DB (few thousand rows); for production we'll add a 5-min cache.
     */
    @Query("""
            select e.entityType, count(e) from AuditEvent e
             group by e.entityType
             order by count(e) desc, e.entityType asc
            """)
    List<Object[]> facetEntityTypes();

    @Query("""
            select e.action, count(e) from AuditEvent e
             group by e.action
             order by count(e) desc, e.action asc
            """)
    List<Object[]> facetActions();

    @Query("""
            select e.sourceService, count(e) from AuditEvent e
             group by e.sourceService
             order by count(e) desc, e.sourceService asc
            """)
    List<Object[]> facetSourceServices();

    /**
     * §6.8 — single-round-trip dedupe + insert.
     *
     * <p>Replaces the {@code existsByEventId} pre-check + {@code save()}
     * pattern (two round-trips on the happy path, two + an exception
     * unwind on the race path) with a native
     * {@code INSERT … ON CONFLICT … DO NOTHING}. Returns the affected
     * row count: 1 = inserted, 0 = duplicate (silently skipped by
     * Postgres, no exception).
     *
     * <p>§6.6 — the conflict target is {@code (event_id, occurred_at)}
     * rather than {@code (event_id)}. After the table was partitioned
     * by {@code occurred_at}, Postgres requires every unique constraint
     * on a partitioned table to include the partition key. The producer
     * envelope sets both fields deterministically, so duplicate
     * deliveries still collide on the same pair.
     *
     * <p>The {@code ip_address} column uses a {@code ::inet} cast for
     * Postgres's {@code inet} type — same trick as the
     * {@code @ColumnTransformer} on the entity.
     */
    @Modifying
    @Query(value = """
            insert into audit.audit_events (
                event_id, source_service, entity_type, entity_id, action,
                actor_user_id, actor_username, actor_label,
                before_json, after_json, changes, metadata,
                correlation_id, ip_address, user_visible, message,
                occurred_at, ingested_at
            ) values (
                :eventId, :sourceService, :entityType, :entityId, :action,
                :actorUserId, :actorUsername, :actorLabel,
                cast(:beforeJson as jsonb), cast(:afterJson as jsonb),
                cast(:changes as jsonb), cast(:metadata as jsonb),
                :correlationId, cast(:ipAddress as inet), :userVisible, :message,
                :occurredAt, :ingestedAt
            )
            on conflict (event_id, occurred_at) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId,
                       @Param("sourceService") String sourceService,
                       @Param("entityType") String entityType,
                       @Param("entityId") String entityId,
                       @Param("action") String action,
                       @Param("actorUserId") Long actorUserId,
                       @Param("actorUsername") String actorUsername,
                       @Param("actorLabel") String actorLabel,
                       @Param("beforeJson") String beforeJson,
                       @Param("afterJson") String afterJson,
                       @Param("changes") String changes,
                       @Param("metadata") String metadata,
                       @Param("correlationId") String correlationId,
                       @Param("ipAddress") String ipAddress,
                       @Param("userVisible") boolean userVisible,
                       @Param("message") String message,
                       @Param("occurredAt") LocalDateTime occurredAt,
                       @Param("ingestedAt") LocalDateTime ingestedAt);
}

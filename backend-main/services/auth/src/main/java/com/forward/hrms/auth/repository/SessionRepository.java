package com.forward.hrms.auth.repository;

import com.forward.hrms.auth.entity.Session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findByRefreshTokenId(String refreshTokenId);

    List<Session> findAllByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(Long userId);

    /**
     * Active sessions (revokedAt is null) PLUS recently-revoked rows
     * (revokedAt within the last {@code revokedSince} cutoff). Drives
     * the "you revoked your phone 2 hours ago" tail on the sessions
     * page. Active rows sort first by lastSeenAt desc, then revoked
     * rows by revokedAt desc — the JPQL handles both ordering keys.
     */
    @Query("""
            select s from Session s
             where s.userId = :userId
               and (s.revokedAt is null or s.revokedAt >= :revokedSince)
             order by case when s.revokedAt is null then 0 else 1 end asc,
                      s.lastSeenAt desc,
                      s.revokedAt desc
            """)
    List<Session> findAllActiveOrRecentlyRevoked(@Param("userId") Long userId,
                                                 @Param("revokedSince") LocalDateTime revokedSince);

    /*
     * `cast(:exceptId as Long)` so Postgres gets a typed null on the
     * `is null` placeholder — without it: `42P18 — could not determine
     * data type of parameter`.
     */
    @Modifying
    @Query("""
            update Session s
               set s.revokedAt = :now,
                   s.revokedBy = :by,
                   s.revokedReason = :reason
             where s.userId = :userId
               and s.revokedAt is null
               and (cast(:exceptId as Long) is null or s.id <> :exceptId)
            """)
    int revokeAllForUser(@Param("userId") Long userId,
                         @Param("now") LocalDateTime now,
                         @Param("by") String by,
                         @Param("reason") com.forward.hrms.auth.enums.RevokeReason reason,
                         @Param("exceptId") Long exceptId);
}

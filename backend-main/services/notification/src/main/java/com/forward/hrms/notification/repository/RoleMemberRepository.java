package com.forward.hrms.notification.repository;

import com.forward.hrms.notification.entity.RoleMember;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RoleMemberRepository extends JpaRepository<RoleMember, RoleMember.Key> {

    /**
     * Idempotent upsert keyed on the natural pk. Touches {@code granted_at}
     * on every replay so a recovered consumer can verify it processed
     * the latest snapshot. Native because JPA doesn't model ON CONFLICT
     * cleanly and we want a single round trip.
     *
     * <p>Schema-qualified explicitly: {@code hibernate.default_schema} only
     * qualifies entity-mapped SQL, not native queries. The DB user's
     * {@code search_path} is {@code "$user", public} — {@code notification}
     * is not on it, so an unqualified {@code role_members} fails with
     * "relation does not exist" at runtime even though the migration
     * landed in {@code notification.role_members} (Flyway sets its own
     * default-schema during DDL).
     */
    @Modifying
    @Query(value = """
            insert into notification.role_members (role_code, user_id, granted_at)
            values (:roleCode, :userId, :grantedAt)
            on conflict (role_code, user_id)
              do update set granted_at = excluded.granted_at
            """, nativeQuery = true)
    int upsert(@Param("roleCode") String roleCode,
               @Param("userId") long userId,
               @Param("grantedAt") LocalDateTime grantedAt);

    @Modifying
    @Query("delete from RoleMember rm where rm.id.roleCode = :roleCode and rm.id.userId = :userId")
    int deleteOne(@Param("roleCode") String roleCode, @Param("userId") long userId);

    @Query("select rm.id.userId from RoleMember rm where rm.id.roleCode = :roleCode")
    List<Long> findUserIdsByRoleCode(@Param("roleCode") String roleCode);
}

package com.forward.hrms.auth.repository;

import com.forward.hrms.auth.dto.response.UserStatsResponse;
import com.forward.hrms.auth.entity.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findByUsername(String username);

    /*
     * The login refresh path and /auth/me both call findById and then iterate
     * roles → permissions to assemble the JWT/MeResponse permission set.
     * Without an EntityGraph this becomes one query per role on every refresh.
     * Mirror findByUsername above so the JPA default fetch graph picks roles
     * and their permissions in one shot.
     */
    @Override
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findById(Long id);

    /*
     * §B14 — /admin/users list path. Without this override, Hibernate
     * runs the spec query then lazy-loads {@code roles} per row through
     * the @ManyToMany association. The list page rendered only the
     * single role from the user's first {@code user_roles} row (admin
     * 158 / 5 roles → 1 visible). Fetching {@code roles} via EntityGraph
     * ensures the same set of role codes that {@code findById} and the
     * JWT claims see lands on the list response.
     */
    @Override
    @EntityGraph(attributePaths = {"roles"})
    Page<User> findAll(Specification<User> spec, Pageable pageable);

    boolean existsByUsername(String username);

    /*
     * Case-insensitive uniqueness — backed by the lower(email) unique index
     * in V0014. Service callers are still expected to normalise on write,
     * but the repository contract reflects what the DB actually enforces.
     */
    boolean existsByEmailIgnoreCase(String email);

    /*
     * Server-side filtered list — see UserSearchSpecifications.search(...).
     * The previous @Query approach hit a Postgres "cannot cast bytea to
     * boolean" on the nullable employeeLinked param: the cast(:p as ...)
     * trick that fixes nullable String/Instant params doesn't work for
     * Boolean (Hibernate binds the NULL as bytea). JPA Specifications
     * sidestep the issue entirely — null filters are simply not added
     * to the predicate list.
     */

    /**
     * One-shot aggregate counts for the /admin overview tile. A single
     * scan beats four paginated round-trips (the previous approach
     * called {@code list(status=…, size=1)} four times just to read
     * {@code totalElements}). H2 + Postgres both fold these into one
     * sequential pass — no individual count queries are issued.
     */
    @Query("""
            select new com.forward.hrms.auth.dto.response.UserStatsResponse(
                count(u),
                coalesce(sum(case when u.status = com.forward.hrms.auth.enums.UserStatus.ACTIVE
                                   and (u.lockedUntil is null or u.lockedUntil <= current_timestamp)
                                  then 1L else 0L end), 0L),
                coalesce(sum(case when u.status = com.forward.hrms.auth.enums.UserStatus.ACTIVE
                                   and u.lockedUntil is not null
                                   and u.lockedUntil > current_timestamp
                                  then 1L else 0L end), 0L),
                coalesce(sum(case when u.status = com.forward.hrms.auth.enums.UserStatus.DISABLED then 1L else 0L end), 0L),
                coalesce(sum(case when u.mustChangePw   = true then 1L else 0L end), 0L),
                coalesce(sum(case when u.employeeLinked = true then 1L else 0L end), 0L),
                coalesce(sum(case when u.employeeLinked = false then 1L else 0L end), 0L))
            from User u
            """)
    UserStatsResponse stats();

    /**
     * Identity-scoped substring search for the chat people-picker.
     * ACTIVE-only; case-insensitive match on username or fullName.
     * Result is bounded by the pageable; relevance sort is left to
     * the JVM (most pickers want alphabetic anyway).
     */
    @Query("""
            select u from User u
            where u.status = com.forward.hrms.auth.enums.UserStatus.ACTIVE
              and (lower(u.username) like lower(concat('%', :q, '%'))
                   or lower(u.fullName) like lower(concat('%', :q, '%')))
            order by lower(u.fullName)
            """)
    List<User> searchActive(String q, Pageable pageable);

    /**
     * §7.auth — count ACTIVE users carrying a role with the given code.
     * Used by the last-ADMIN guard so the UI can't disable / strip-role
     * the only remaining ADMIN account.
     */
    @Query("""
            select count(distinct u) from User u join u.roles r
            where u.status = com.forward.hrms.auth.enums.UserStatus.ACTIVE
              and r.code = :roleCode
            """)
    long countActiveByRoleCode(String roleCode);
}

package com.forward.hrms.employee.repository.travel;

import com.forward.hrms.employee.entity.travel.TravelPolicyVersion;
import com.forward.hrms.employee.enums.travel.TravelPolicyVersionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TravelPolicyVersionRepository extends JpaRepository<TravelPolicyVersion, Long> {

    Optional<TravelPolicyVersion> findByPolicyCodeAndVersionNo(String policyCode, Integer versionNo);

    boolean existsByPolicyCodeAndVersionNo(String policyCode, Integer versionNo);

    List<TravelPolicyVersion> findByPolicyCodeOrderByVersionNoDesc(String policyCode);

    /**
     * Every version of {@code policyCode} in {@code status}. Used by the
     * activation guard: at most one ACTIVE version per policy code is
     * permitted, so the ACTIVE lookup is expected to return zero rows
     * before an activation and exactly one after it.
     */
    List<TravelPolicyVersion> findByPolicyCodeAndStatus(String policyCode, TravelPolicyVersionStatus status);

    @Query("""
            select p from TravelPolicyVersion p
            where (:policyCode is null or p.policyCode = :policyCode)
              and (:status is null or p.status = :status)
            order by p.policyCode asc, p.versionNo desc
            """)
    Page<TravelPolicyVersion> search(String policyCode,
                                     TravelPolicyVersionStatus status,
                                     Pageable pageable);

    /**
     * ACTIVE versions of a policy whose effective window covers
     * {@code onDate}, newest business version first.
     *
     * <p>Business rule: at most one ACTIVE version may exist per
     * {@code policyCode}, so a healthy database returns zero or one row.
     * The return type stays a {@code List} deliberately — the service
     * treats more than one row as inconsistent master data and raises a
     * conflict rather than silently taking {@code get(0)}. Collapsing this
     * to a single-result query would make duplicate ACTIVE rows surface as
     * an opaque {@code NonUniqueResultException} instead.
     */
    @Query("""
            select p from TravelPolicyVersion p
            where p.policyCode = :policyCode
              and p.status = com.forward.hrms.employee.enums.travel.TravelPolicyVersionStatus.ACTIVE
              and p.effectiveFrom <= :onDate
              and (p.effectiveTo is null or p.effectiveTo >= :onDate)
            order by p.versionNo desc
            """)
    List<TravelPolicyVersion> findApplicable(String policyCode, LocalDate onDate);
}

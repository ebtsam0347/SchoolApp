package com.forward.hrms.employee.repository.travel;

import com.forward.hrms.employee.entity.travel.TravelFuelPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface TravelFuelPolicyRepository extends JpaRepository<TravelFuelPolicy, Long> {

    Optional<TravelFuelPolicy> findByCode(String code);

    // boolean existsByCode(String code);

    // much better method to check for active policies
    boolean existsByCodeAndIsActiveTrue(String code);

    @Query("""
            select p from TravelFuelPolicy p
            where (:code is null or p.code = :code)
              and (:isActive is null or p.isActive = :isActive)
            order by p.code asc
            """)
    Page<TravelFuelPolicy> search(String code, Boolean isActive, Pageable pageable);

    /**
     * The ACTIVE fuel policy with {@code code} whose effective window covers
     * {@code onDate}. Transport verification resolves the applicable policy
     * this way rather than trusting a client-supplied fuel average.
     */
    @Query("""
            select p from TravelFuelPolicy p
            where p.code = :code
              and p.isActive = true
              and p.effectiveFrom <= :onDate
              and (p.effectiveTo is null or p.effectiveTo >= :onDate)
            """)
    Optional<TravelFuelPolicy> findApplicable(String code, LocalDate onDate);
}

package com.forward.hrms.commons.approval.repository;

import com.forward.hrms.commons.approval.entity.ApprovalDelegation;
import com.forward.hrms.commons.approval.enums.DelegationStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface ApprovalDelegationRepository extends JpaRepository<ApprovalDelegation, Long> {

    /**
     * All ACTIVE delegations the caller (delegate) currently holds for a
     * given request type on a date. Native because the {@code request_types}
     * filter walks a Postgres {@code varchar(24)[]} — JPQL {@code member of}
     * doesn't apply to a basic-typed {@code List<String>} mapped via
     * {@code @JdbcTypeCode(SqlTypes.ARRAY)}.
     */
    @Query(value = """
            select * from approval_delegations d
            where d.delegate_id = :delegateId
              and d.status = 'ACTIVE'
              and d.from_date <= :on
              and (d.to_date is null or d.to_date >= :on)
              and (d.request_types is null or :type = any(d.request_types))
            """, nativeQuery = true)
    List<ApprovalDelegation> findActiveForDelegate(Long delegateId, LocalDate on, String type);

    /** All delegations the caller (delegator) has issued by status. */
    List<ApprovalDelegation> findAllByDelegatorIdAndStatus(Long delegatorId, DelegationStatus status);

    List<ApprovalDelegation> findAllByDelegateIdAndStatus(Long delegateId, DelegationStatus status);

    /** Overlap probe — same delegator, ACTIVE delegations whose date range touches [from..to]. */
    @Query("""
            select d from ApprovalDelegation d
            where d.delegatorId = :delegatorId
              and d.status = com.forward.hrms.commons.approval.enums.DelegationStatus.ACTIVE
              and d.fromDate <= :to
              and (d.toDate is null or d.toDate >= :from)
              and (:excludeId is null or d.id <> :excludeId)
            """)
    List<ApprovalDelegation> findActiveOverlapping(Long delegatorId, LocalDate from, LocalDate to, Long excludeId);
}

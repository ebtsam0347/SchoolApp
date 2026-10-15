package com.forward.hrms.commons.approval.repository;

import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.enums.ResolverKind;
import com.forward.hrms.commons.approval.enums.StepStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {

    List<ApprovalStep> findAllByRequestTypeAndRequestIdOrderByStageNoAsc(String requestType, Long requestId);

    /** Pending step for a single request — at most one. */
    @Query("""
            select s from ApprovalStep s
            where s.requestType = :requestType
              and s.requestId = :requestId
              and s.status = com.forward.hrms.commons.approval.enums.StepStatus.PENDING
            """)
    List<ApprovalStep> findPendingForRequest(String requestType, Long requestId);

    /** Inbox: rows the assignee is direct on. */
    @Query("""
            select s from ApprovalStep s
            where s.assignedTo = :userId
              and s.status = com.forward.hrms.commons.approval.enums.StepStatus.PENDING
              and s.requestType = :requestType
            """)
    List<ApprovalStep> findPendingForAssignee(String requestType, Long userId);

    /** Inbox: role-only rows (assignedTo null) — picked up by anyone holding the stage's permission. */
    @Query("""
            select s from ApprovalStep s
            where s.assignedTo is null
              and s.status = com.forward.hrms.commons.approval.enums.StepStatus.PENDING
              and s.requestType = :requestType
            """)
    List<ApprovalStep> findPendingRoleOnly(String requestType);

    /** All assignees in a delegate's inbox — used to expand delegations. */
    @Query("""
            select s from ApprovalStep s
            where s.assignedTo in :assigneeIds
              and s.status = com.forward.hrms.commons.approval.enums.StepStatus.PENDING
              and s.requestType = :requestType
            """)
    List<ApprovalStep> findPendingForAssignees(String requestType, List<Long> assigneeIds);

    /** Reroute on manager change: all PENDING/NOT_YET MANAGER stages snapshotted to {@code oldManagerId}. */
    @Query("""
            select s from ApprovalStep s
            where s.assignedTo = :oldManagerId
              and s.resolverKind = com.forward.hrms.commons.approval.enums.ResolverKind.MANAGER
              and s.status in (com.forward.hrms.commons.approval.enums.StepStatus.PENDING,
                               com.forward.hrms.commons.approval.enums.StepStatus.NOT_YET)
            """)
    List<ApprovalStep> findOpenManagerStepsAssignedTo(Long oldManagerId);

    /** Bulk status update used by manager-changed reroute and termination cascades. */
    @Modifying
    @Query("update ApprovalStep s set s.status = :status where s.id in :ids")
    int updateStatusForIds(List<Long> ids, StepStatus status);

    /**
     * Approver's decision history. Returns the rows the supplied
     * decider closed (APPROVED or REJECTED) within the date window,
     * optionally narrowed by request type and decision.
     */
    @Query("""
            select s from ApprovalStep s
            where s.decidedBy = :deciderId
              and s.decidedAt is not null
              and s.decidedAt >= :from
              and s.decidedAt < :toExclusive
              and s.status in (com.forward.hrms.commons.approval.enums.StepStatus.APPROVED,
                               com.forward.hrms.commons.approval.enums.StepStatus.REJECTED)
              and (:requestType is null or s.requestType = :requestType)
              and (:decision is null or s.status = :decision)
            """)
    Page<ApprovalStep> findDecisionsByDecider(Long deciderId,
                                              LocalDateTime from,
                                              LocalDateTime toExclusive,
                                              String requestType,
                                              StepStatus decision,
                                              Pageable pageable);
}

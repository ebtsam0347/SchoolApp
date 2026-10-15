package com.forward.hrms.commons.approval.service;

import com.forward.hrms.commons.approval.entity.ApprovalDelegation;
import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.enums.ResolverKind;
import com.forward.hrms.commons.approval.repository.ApprovalDelegationRepository;
import com.forward.hrms.commons.approval.repository.ApprovalStepRepository;
import com.forward.hrms.commons.approval.spi.GradeResolver;
import com.forward.hrms.commons.core.context.CurrentUser;

import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes the set of {@link ApprovalStep}s in the caller's inbox for a
 * given request type. Combines:
 * <ul>
 *   <li>steps directly assigned to the caller;</li>
 *   <li>role-only steps whose snapshotted {@code approve_permission} the
 *       caller holds, or (for {@code GRADE} kind) whose snapshotted
 *       {@code resolver_target} matches the caller's employee grade;</li>
 *   <li>steps assigned to delegators who have an ACTIVE delegation to
 *       the caller covering this request type today.</li>
 * </ul>
 */
@RequiredArgsConstructor
public class ApprovalInboxService {

    private final ApprovalStepRepository steps;
    private final ApprovalDelegationRepository delegations;
    private final GradeResolver gradeResolver;

    @Transactional(readOnly = true)
    public List<Long> requestIdsInInbox(String requestType, CurrentUser caller) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ApprovalStep s : steps.findPendingForAssignee(requestType, caller.userId())) {
            ids.add(s.getRequestId());
        }
        String callerGradeCode = gradeResolver.resolveGradeCode(caller.userId());
        for (ApprovalStep s : steps.findPendingRoleOnly(requestType)) {
            if (s.getResolverKind() == ResolverKind.GRADE) {
                if (callerGradeCode != null && callerGradeCode.equals(s.getResolverTarget())) {
                    ids.add(s.getRequestId());
                }
            } else if (s.getApprovePermission() != null && caller.hasPermission(s.getApprovePermission())) {
                ids.add(s.getRequestId());
            }
        }
        List<Long> delegators = delegations
                .findActiveForDelegate(caller.userId(), LocalDate.now(), requestType)
                .stream().map(ApprovalDelegation::getDelegatorId).toList();
        if (!delegators.isEmpty()) {
            for (ApprovalStep s : steps.findPendingForAssignees(requestType, delegators)) {
                ids.add(s.getRequestId());
            }
        }
        return List.copyOf(ids);
    }
}

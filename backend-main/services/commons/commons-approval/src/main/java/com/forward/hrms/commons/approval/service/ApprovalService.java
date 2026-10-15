package com.forward.hrms.commons.approval.service;

import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.entity.ApprovalWorkflowStage;
import com.forward.hrms.commons.approval.enums.ResolverKind;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.commons.approval.repository.ApprovalDelegationRepository;
import com.forward.hrms.commons.approval.repository.ApprovalStepRepository;
import com.forward.hrms.commons.approval.repository.ApprovalWorkflowStageRepository;
import com.forward.hrms.commons.approval.spi.ApprovalOutcomeHandler;
import com.forward.hrms.commons.approval.spi.DepartmentHeadResolver;
import com.forward.hrms.commons.approval.spi.GradeResolver;
import com.forward.hrms.commons.approval.spi.ManagerResolver;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ErrorCode;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The state machine that drives multi-stage approvals. Built around
 * {@link ApprovalStep}: each stage in the chain has its own row and status.
 *
 * <p>Delegation is dynamic — {@code assigned_to} is never rewritten. The
 * "can act" predicate is evaluated against the live
 * {@code ApprovalDelegation} table.
 *
 * <p>All write methods use {@code Propagation.MANDATORY} — callers must
 * supply an outer transaction.
 */
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalStepRepository steps;
    private final ApprovalWorkflowStageRepository workflowStages;
    private final ApprovalDelegationRepository delegations;
    private final ManagerResolver managerResolver;
    private final DepartmentHeadResolver departmentHeadResolver;
    private final GradeResolver gradeResolver;
    private final AuditEmitter audit;
    private final ObjectProvider<List<ApprovalOutcomeHandler>> outcomeHandlers;
    private final ErrorCode workflowNotConfiguredCode;
    private final ErrorCode noPendingStagCode;
    private final ErrorCode notAuthorizedCode;
    private final String overridePermission;

    /**
     * Insert the chain rows for a freshly-submitted request. Stage 1
     * starts PENDING; later stages start NOT_YET. Returns the rows in
     * stage order so callers can embed the snapshot in their event payloads.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ApprovalStep> openChain(String requestType, long requestId, long employeeId) {
        List<ApprovalWorkflowStage> chain =
                workflowStages.findByRequestTypeAndActiveTrueOrderByStageNoAsc(requestType);
        if (chain.isEmpty()) {
            throw new ConflictError(workflowNotConfiguredCode,
                    "no approval workflow configured for request type " + requestType);
        }
        List<ApprovalStep> opened = new ArrayList<>(chain.size());
        for (int i = 0; i < chain.size(); i++) {
            ApprovalWorkflowStage cfg = chain.get(i);
            ApprovalStep s = new ApprovalStep();
            s.setRequestType(requestType);
            s.setRequestId(requestId);
            s.setStageNo(i + 1);
            s.setResolverKind(cfg.getResolverKind());
            s.setApprovePermission(cfg.getApprovePermission());
            s.setResolverTarget(cfg.getResolverTarget());
            s.setLabel(cfg.getLabel());
            s.setAssignedTo(resolveAssignee(cfg.getResolverKind(), employeeId, cfg.getResolverTarget()));
            s.setStatus(i == 0 ? StepStatus.PENDING : StepStatus.NOT_YET);
            opened.add(steps.save(s));
        }
        return opened;
    }

    public List<ApprovalStep> findChain(String requestType, long requestId) {
        return steps.findAllByRequestTypeAndRequestIdOrderByStageNoAsc(requestType, requestId);
    }

    public Optional<ApprovalStep> currentPending(String requestType, long requestId) {
        return steps.findPendingForRequest(requestType, requestId).stream().findFirst();
    }

    /**
     * Approve at the caller's current PENDING stage. Triggers
     * {@link ApprovalOutcomeHandler#onApproved} when this is the final stage.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Decision approve(String requestType, long requestId, String note) {
        ApprovalStep current = requirePending(requestType, requestId);
        CurrentUser user = requireCanAct(requestType, current);
        current.setStatus(StepStatus.APPROVED);
        current.setDecidedBy(user.userId());
        current.setDecidedAt(LocalDateTime.now());
        current.setNote(note);

        Optional<ApprovalStep> next = activateNextStage(requestType, requestId, current.getStageNo());
        Decision decision = new Decision(current, next.orElse(null), user);
        dispatchApproved(requestType, requestId, decision);
        return decision;
    }

    /**
     * Reject at the caller's current PENDING stage. Cascade: every NOT_YET
     * step becomes SKIPPED. Triggers {@link ApprovalOutcomeHandler#onRejected}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Decision reject(String requestType, long requestId, String reason) {
        ApprovalStep current = requirePending(requestType, requestId);
        CurrentUser user = requireCanAct(requestType, current);
        current.setStatus(StepStatus.REJECTED);
        current.setDecidedBy(user.userId());
        current.setDecidedAt(LocalDateTime.now());
        current.setNote(reason);

        skipLaterStages(requestType, requestId, current.getStageNo());
        Decision decision = new Decision(current, null, user);
        dispatchRejected(requestType, requestId, reason, decision);
        return decision;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelChain(String requestType, long requestId) {
        for (ApprovalStep s : findChain(requestType, requestId)) {
            if (s.getStatus() == StepStatus.PENDING || s.getStatus() == StepStatus.NOT_YET) {
                s.setStatus(StepStatus.SKIPPED);
            }
        }
    }

    /** Reroute every open MANAGER stage assigned to {@code oldManagerId} → {@code newManagerId}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int rerouteManager(Long oldManagerId, Long newManagerId) {
        List<ApprovalStep> open = steps.findOpenManagerStepsAssignedTo(oldManagerId);
        for (ApprovalStep s : open) s.setAssignedTo(newManagerId);
        return open.size();
    }

    /**
     * Returns true iff the caller can act on the supplied step right now.
     * Direct assignment, active delegation, role-permission for role-only
     * stages, or the override permission.
     */
    public boolean canAct(String requestType, ApprovalStep step) {
        return CurrentUserHolder.get().filter(u -> canAct(requestType, step, u)).isPresent();
    }

    public boolean canAct(String requestType, ApprovalStep step, CurrentUser user) {
        if (!overridePermission.isBlank() && user.hasPermission(overridePermission)) return true;
        if (step.getAssignedTo() != null && step.getAssignedTo().equals(user.userId())) return true;
        if (step.getAssignedTo() == null) {
            if (step.getResolverKind() == ResolverKind.GRADE) {
                String target = step.getResolverTarget();
                return target != null && target.equals(gradeResolver.resolveGradeCode(user.userId()));
            }
            String perm = step.getApprovePermission();
            return perm != null && user.hasPermission(perm);
        }
        return hasActiveDelegation(user.userId(), step.getAssignedTo(), requestType, LocalDate.now());
    }

    public boolean isAtFinalStage(String requestType, long requestId) {
        List<ApprovalStep> chain = findChain(requestType, requestId);
        if (chain.isEmpty()) return false;
        ApprovalStep last = chain.get(chain.size() - 1);
        return last.getStatus() == StepStatus.PENDING;
    }

    public boolean isAtResolverKind(String requestType, long requestId, ResolverKind kind) {
        return currentPending(requestType, requestId)
                .map(s -> s.getResolverKind() == kind)
                .orElse(false);
    }

    public boolean isFinalized(String requestType, long requestId) {
        return findChain(requestType, requestId).stream()
                .noneMatch(s -> s.getStatus() == StepStatus.PENDING || s.getStatus() == StepStatus.NOT_YET);
    }

    /**
     * Close the current PENDING stage with the given outcome without going
     * through the normal approve/reject flow. Used by callers that derive
     * the stage outcome from domain logic (e.g. the per-day leave mix).
     * Does NOT invoke {@link ApprovalOutcomeHandler} — callers own that
     * dispatch when they use this variant.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Decision closeCurrentStage(String requestType, long requestId,
                                      StepStatus outcome, CurrentUser actor) {
        return closeCurrentStage(requestType, requestId, outcome, actor, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Decision closeCurrentStage(String requestType, long requestId,
                                      StepStatus outcome, CurrentUser actor, String note) {
        if (outcome != StepStatus.APPROVED && outcome != StepStatus.REJECTED) {
            throw new IllegalArgumentException("outcome must be APPROVED or REJECTED");
        }
        ApprovalStep current = requirePending(requestType, requestId);
        current.setStatus(outcome);
        current.setDecidedBy(actor.userId());
        current.setDecidedAt(LocalDateTime.now());
        if (note != null) current.setNote(note);
        if (outcome == StepStatus.APPROVED) {
            return new Decision(current,
                    activateNextStage(requestType, requestId, current.getStageNo()).orElse(null), actor);
        }
        skipLaterStages(requestType, requestId, current.getStageNo());
        return new Decision(current, null, actor);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private Long resolveAssignee(ResolverKind kind, long employeeId, String resolverTarget) {
        return switch (kind) {
            case MANAGER -> managerResolver.resolveManagerUserId(employeeId);
            case PERMISSION -> null;
            case DEPARTMENT_HEAD -> departmentHeadResolver.resolveDepartmentHeadUserId(resolverTarget);
            case GRADE -> null;
        };
    }

    private boolean hasActiveDelegation(long delegateId, long delegatorId, String requestType, LocalDate on) {
        return delegations.findActiveForDelegate(delegateId, on, requestType).stream()
                .anyMatch(d -> d.getDelegatorId().equals(delegatorId));
    }

    private Optional<ApprovalStep> activateNextStage(String requestType, long requestId, int currentStageNo) {
        for (ApprovalStep s : findChain(requestType, requestId)) {
            if (s.getStageNo() == currentStageNo + 1 && s.getStatus() == StepStatus.NOT_YET) {
                s.setStatus(StepStatus.PENDING);
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    private void skipLaterStages(String requestType, long requestId, int currentStageNo) {
        for (ApprovalStep s : findChain(requestType, requestId)) {
            if (s.getStageNo() > currentStageNo
                    && (s.getStatus() == StepStatus.NOT_YET || s.getStatus() == StepStatus.PENDING)) {
                s.setStatus(StepStatus.SKIPPED);
            }
        }
    }

    private ApprovalStep requirePending(String requestType, long requestId) {
        return steps.findPendingForRequest(requestType, requestId).stream().findFirst()
                .orElseThrow(() -> new ConflictError(noPendingStagCode,
                        "no pending stage for this request"));
    }

    private CurrentUser requireCanAct(String requestType, ApprovalStep step) {
        CurrentUser user = CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("no current user"));
        if (!canAct(requestType, step, user)) {
            audit.emitOnFailure("approval_step", String.valueOf(step.getId()), "denied",
                    null,
                    Map.of(
                            "requestType", requestType,
                            "requestId", step.getRequestId(),
                            "stageNo", step.getStageNo(),
                            "stageLabel", step.getLabel() == null ? "" : step.getLabel(),
                            "byUserId", user.userId(),
                            "byUsername", user.username() == null ? "" : user.username()),
                    false);
            throw new ForbiddenError(notAuthorizedCode,
                    "not authorized to act on this stage");
        }
        return user;
    }

    private void dispatchApproved(String requestType, long requestId, Decision decision) {
        List<ApprovalOutcomeHandler> handlers = outcomeHandlers.getIfAvailable();
        if (handlers == null) return;
        for (ApprovalOutcomeHandler h : handlers) {
            if (requestType.equals(h.requestType())) {
                h.onApproved(requestId, decision);
            }
        }
    }

    private void dispatchRejected(String requestType, long requestId, String reason, Decision decision) {
        List<ApprovalOutcomeHandler> handlers = outcomeHandlers.getIfAvailable();
        if (handlers == null) return;
        for (ApprovalOutcomeHandler h : handlers) {
            if (requestType.equals(h.requestType())) {
                h.onRejected(requestId, reason, decision);
            }
        }
    }

    /** Outcome of one approve/reject call. */
    public record Decision(ApprovalStep step, ApprovalStep nextPending, CurrentUser actor) {
        public boolean isFinal() { return nextPending == null; }
    }
}

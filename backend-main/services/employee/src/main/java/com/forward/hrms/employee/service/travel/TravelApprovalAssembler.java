package com.forward.hrms.employee.service.travel;

import com.forward.hrms.attendance.dto.response.ApprovalStepDto;
import com.forward.hrms.attendance.dto.response.CurrentStageDto;
import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the approval-aware {@code currentStage} / {@code steps} /
 * {@code actions} shape for the Travel response DTOs — the same contract
 * attendance's {@code LeaveDtoAssembler}, payroll's
 * {@code PermanentAdvanceService} and employee's
 * {@code FactoryTourDtoAssembler} expose, so the FE gets one JSON shape
 * across every approval-having request type.
 *
 * <p>{@code ApprovalStep.assignedTo} / {@code decidedBy} are auth
 * {@code userId} values (see {@code ApprovalService.approve} /
 * {@code ManagerResolver.resolveManagerUserId}), not employee ids.
 * {@code assignedToName} / {@code decidedByName} are resolved with a single
 * batched {@link EmployeeRepository#findAllByUserIdIn} lookup against
 * employee-service's own {@code employees} table (this service owns that
 * data — no cross-service call needed); an id with no linked employee
 * record resolves to {@code null}, same as a step with no assignee at all.
 */
@Component
@RequiredArgsConstructor
public class TravelApprovalAssembler {

    private final ApprovalService approvals;
    private final EmployeeRepository employees;

    /** The full chain view for one request: {@code currentStage}, {@code steps} and the caller's {@code actions}. */
    public View view(String requestType, long requestId, boolean cancellable) {
        List<ApprovalStep> chain = approvals.findChain(requestType, requestId);
        Map<Long, String> namesByUserId = namesByUserId(chain);
        return new View(currentStage(chain, namesByUserId), stepsDto(chain, namesByUserId),
                actionsForCaller(requestType, chain, cancellable));
    }

    private Map<Long, String> namesByUserId(List<ApprovalStep> chain) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (ApprovalStep s : chain) {
            if (s.getAssignedTo() != null) {
                userIds.add(s.getAssignedTo());
            }
            if (s.getDecidedBy() != null) {
                userIds.add(s.getDecidedBy());
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return employees.findAllByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(Employee::getUserId, Employee::getFullName, (a, b) -> a));
    }

    private static CurrentStageDto currentStage(List<ApprovalStep> chain, Map<Long, String> namesByUserId) {
        return chain.stream()
                .filter(s -> s.getStatus() == StepStatus.PENDING)
                .findFirst()
                .map(s -> new CurrentStageDto(s.getStageNo(), s.getLabel(), s.getAssignedTo(),
                        nameOf(namesByUserId, s.getAssignedTo())))
                .orElse(null);
    }

    private static List<ApprovalStepDto> stepsDto(List<ApprovalStep> chain, Map<Long, String> namesByUserId) {
        List<ApprovalStepDto> out = new ArrayList<>(chain.size());
        for (ApprovalStep s : chain) {
            out.add(new ApprovalStepDto(
                    s.getStageNo(), s.getLabel(), s.getAssignedTo(), nameOf(namesByUserId, s.getAssignedTo()),
                    s.getStatus().name(), s.getDecidedBy(), nameOf(namesByUserId, s.getDecidedBy()),
                    s.getDecidedAt(), s.getNote()));
        }
        return out;
    }

    /**
     * {@code namesByUserId} may be the immutable {@link Map#of()} (no
     * resolvable actor in the whole chain), whose {@code get} throws on a
     * {@code null} key instead of returning {@code null} — every
     * {@code PERMISSION}-resolver step's {@code assignedTo} is exactly that
     * before it's decided.
     */
    private static String nameOf(Map<Long, String> namesByUserId, Long userId) {
        return userId == null ? null : namesByUserId.get(userId);
    }

    private List<String> actionsForCaller(String requestType, List<ApprovalStep> chain, boolean cancellable) {
        CurrentUser user = CurrentUserHolder.get().orElse(null);
        if (user == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(3);
        ApprovalStep current = chain.stream()
                .filter(s -> s.getStatus() == StepStatus.PENDING)
                .findFirst().orElse(null);
        if (current != null && approvals.canAct(requestType, current, user)) {
            out.add("APPROVE");
            out.add("REJECT");
        }
        if (cancellable && user.hasPermission("travel.write")) {
            out.add("CANCEL");
        }
        return out;
    }

    /** Immutable carrier for the three chain-view fields threaded into the Travel response DTOs. */
    public record View(CurrentStageDto currentStage, List<ApprovalStepDto> steps, List<String> actions) {

        /** No chain has been opened yet (DRAFT). */
        public static final View EMPTY = new View(null, List.of(), List.of());
    }
}

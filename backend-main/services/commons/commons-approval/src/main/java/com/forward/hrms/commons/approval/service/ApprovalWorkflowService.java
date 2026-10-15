package com.forward.hrms.commons.approval.service;

import com.forward.hrms.commons.approval.dto.request.SetWorkflowChainRequest;
import com.forward.hrms.commons.approval.dto.response.WorkflowChainDto;
import com.forward.hrms.commons.approval.dto.response.WorkflowStageDto;
import com.forward.hrms.commons.approval.entity.ApprovalWorkflowStage;
import com.forward.hrms.commons.approval.enums.ResolverKind;
import com.forward.hrms.commons.approval.repository.ApprovalWorkflowStageRepository;
import com.forward.hrms.commons.approval.spi.RequestTypeRegistration;
import com.forward.hrms.commons.core.error.ErrorCode;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;

import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * HR-facing config for the approval chains that {@link ApprovalService}
 * snapshots at submit. Editing a chain is data, not a deploy. In-flight
 * requests are unaffected — they run on the steps snapshotted when opened.
 */
@RequiredArgsConstructor
public class ApprovalWorkflowService {

    private final ApprovalWorkflowStageRepository workflowStages;
    private final AuditEmitter audit;
    private final List<RequestTypeRegistration> registrations;
    private final ErrorCode workflowStageInvalidCode;

    @Transactional(readOnly = true)
    public List<WorkflowChainDto> listChains() {
        Map<String, List<WorkflowStageDto>> byType = new LinkedHashMap<>();
        for (ApprovalWorkflowStage s : workflowStages.findByActiveTrueOrderByRequestTypeAscStageNoAsc()) {
            byType.computeIfAbsent(s.getRequestType(), k -> new ArrayList<>())
                    .add(toStageDto(s));
        }
        Set<String> allTypes = new TreeSet<>();
        for (RequestTypeRegistration r : registrations) allTypes.addAll(r.requestTypes());
        List<WorkflowChainDto> out = new ArrayList<>(allTypes.size());
        for (String t : allTypes) {
            out.add(new WorkflowChainDto(t, byType.getOrDefault(t, List.of())));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public WorkflowChainDto getChain(String requestType) {
        List<WorkflowStageDto> stages = workflowStages
                .findByRequestTypeAndActiveTrueOrderByStageNoAsc(requestType).stream()
                .map(this::toStageDto)
                .toList();
        return new WorkflowChainDto(requestType, stages);
    }

    /** Atomically replace the chain for one request type with the supplied ordered stages. */
    @Transactional
    public WorkflowChainDto replaceChain(String requestType, SetWorkflowChainRequest req) {
        List<Map<String, Object>> before = snapshot(
                workflowStages.findByRequestTypeAndActiveTrueOrderByStageNoAsc(requestType));

        workflowStages.deleteByRequestType(requestType);

        List<ApprovalWorkflowStage> saved = new ArrayList<>(req.stages().size());
        int stageNo = 1;
        for (SetWorkflowChainRequest.Stage in : req.stages()) {
            String perm = in.resolverKind() == ResolverKind.PERMISSION
                    ? trimToNull(in.approvePermission())
                    : null;
            if (in.resolverKind() == ResolverKind.PERMISSION && perm == null) {
                throw new UnprocessableError(workflowStageInvalidCode,
                        "PERMISSION stage at position " + stageNo + " requires an approvePermission");
            }
            boolean needsTarget = in.resolverKind() == ResolverKind.DEPARTMENT_HEAD
                    || in.resolverKind() == ResolverKind.GRADE;
            String target = needsTarget ? trimToNull(in.resolverTarget()) : null;
            if (needsTarget && target == null) {
                throw new UnprocessableError(workflowStageInvalidCode,
                        in.resolverKind() + " stage at position " + stageNo + " requires a resolverTarget");
            }
            ApprovalWorkflowStage s = new ApprovalWorkflowStage();
            s.setRequestType(requestType);
            s.setStageNo(stageNo++);
            s.setResolverKind(in.resolverKind());
            s.setApprovePermission(perm);
            s.setResolverTarget(target);
            s.setLabel(in.label().trim());
            s.setActive(true);
            saved.add(workflowStages.save(s));
        }

        audit.emit("approval_workflow", requestType, "updated", before, snapshot(saved), false);
        return new WorkflowChainDto(requestType,
                saved.stream().map(this::toStageDto).toList());
    }

    private WorkflowStageDto toStageDto(ApprovalWorkflowStage s) {
        return new WorkflowStageDto(
                s.getStageNo(),
                s.getResolverKind().name(),
                s.getApprovePermission(),
                s.getResolverTarget(),
                s.getLabel(),
                s.isActive());
    }

    private static List<Map<String, Object>> snapshot(List<ApprovalWorkflowStage> stages) {
        List<Map<String, Object>> out = new ArrayList<>(stages.size());
        for (ApprovalWorkflowStage s : stages) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stageNo", s.getStageNo());
            m.put("resolverKind", s.getResolverKind().name());
            m.put("approvePermission", s.getApprovePermission());
            m.put("resolverTarget", s.getResolverTarget());
            m.put("label", s.getLabel());
            out.add(m);
        }
        return out;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

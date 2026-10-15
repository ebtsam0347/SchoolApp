package com.forward.hrms.commons.approval.service;

import com.forward.hrms.commons.approval.dto.request.CreateDelegationOnBehalfRequest;
import com.forward.hrms.commons.approval.dto.request.CreateDelegationRequest;
import com.forward.hrms.commons.approval.dto.request.UpdateDelegationRequest;
import com.forward.hrms.commons.approval.dto.response.DelegationDto;
import com.forward.hrms.commons.approval.entity.ApprovalDelegation;
import com.forward.hrms.commons.approval.enums.DelegationStatus;
import com.forward.hrms.commons.approval.repository.ApprovalDelegationRepository;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ErrorCode;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;

import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public class ApprovalDelegationService {

    private final ApprovalDelegationRepository delegations;
    private final AuditEmitter audit;
    private final ErrorCode delegationNotFoundCode;
    private final ErrorCode delegationOverlapCode;
    private final ErrorCode delegationInvalidCode;
    private final ErrorCode notAuthorizedCode;
    private final String overridePermission;

    @Transactional
    public DelegationDto create(CreateDelegationRequest req) {
        CurrentUser caller = require();
        return write(caller.userId(), req.delegateId(), req.requestTypes(),
                req.fromDate(), req.toDate(), req.reason());
    }

    @Transactional
    public DelegationDto createOnBehalf(CreateDelegationOnBehalfRequest req) {
        CurrentUser caller = require();
        if (!caller.hasPermission(overridePermission)) {
            throw new ForbiddenError(notAuthorizedCode,
                    "approval override permission required");
        }
        return write(req.delegatorId(), req.delegateId(), req.requestTypes(),
                req.fromDate(), req.toDate(), req.reason());
    }

    @Transactional
    public DelegationDto update(long id, UpdateDelegationRequest req) {
        CurrentUser caller = require();
        ApprovalDelegation d = delegations.findById(id)
                .orElseThrow(() -> new NotFoundError(delegationNotFoundCode, "delegation not found"));
        if (!d.getDelegatorId().equals(caller.userId())
                && !caller.hasPermission(overridePermission)) {
            throw new ForbiddenError(notAuthorizedCode, "not the delegator");
        }
        Map<String, Object> before = snapshot(d);
        if (req.toDate() != null) {
            if (req.toDate().isBefore(d.getFromDate())) {
                throw new UnprocessableError(delegationInvalidCode,
                        "to_date is before from_date");
            }
            d.setToDate(req.toDate());
        }
        if (req.reason() != null) d.setReason(req.reason());
        audit.emit("approval_delegation", String.valueOf(d.getId()), "updated", before, snapshot(d), false);
        return toDto(d);
    }

    @Transactional
    public DelegationDto revoke(long id) {
        CurrentUser caller = require();
        ApprovalDelegation d = delegations.findById(id)
                .orElseThrow(() -> new NotFoundError(delegationNotFoundCode, "delegation not found"));
        if (!d.getDelegatorId().equals(caller.userId())
                && !caller.hasPermission(overridePermission)) {
            throw new ForbiddenError(notAuthorizedCode, "not the delegator");
        }
        Map<String, Object> before = snapshot(d);
        d.setStatus(DelegationStatus.REVOKED);
        d.setRevokedAt(LocalDateTime.now());
        d.setRevokedBy(caller.userId());
        audit.emit("approval_delegation", String.valueOf(d.getId()), "revoked", before, snapshot(d), false);
        return toDto(d);
    }

    @Transactional(readOnly = true)
    public List<DelegationDto> myDelegations(DelegationStatus status, LocalDate activeOn) {
        CurrentUser caller = require();
        Set<ApprovalDelegation> rows = new HashSet<>();
        if (status != null) {
            rows.addAll(delegations.findAllByDelegatorIdAndStatus(caller.userId(), status));
            rows.addAll(delegations.findAllByDelegateIdAndStatus(caller.userId(), status));
        } else {
            rows.addAll(delegations.findAllByDelegatorIdAndStatus(caller.userId(), DelegationStatus.ACTIVE));
            rows.addAll(delegations.findAllByDelegatorIdAndStatus(caller.userId(), DelegationStatus.REVOKED));
            rows.addAll(delegations.findAllByDelegateIdAndStatus(caller.userId(), DelegationStatus.ACTIVE));
            rows.addAll(delegations.findAllByDelegateIdAndStatus(caller.userId(), DelegationStatus.REVOKED));
        }
        return rows.stream()
                .filter(d -> activeOn == null || isActiveOn(d, activeOn))
                .map(this::toDto)
                .toList();
    }

    private DelegationDto write(long delegatorId, long delegateId, Set<String> requestTypes,
                                LocalDate fromDate, LocalDate toDate, String reason) {
        if (delegatorId == delegateId) {
            throw new UnprocessableError(delegationInvalidCode,
                    "cannot delegate to yourself");
        }
        if (toDate != null && toDate.isBefore(fromDate)) {
            throw new UnprocessableError(delegationInvalidCode,
                    "to_date is before from_date");
        }
        for (ApprovalDelegation existing : delegations.findActiveOverlapping(delegatorId, fromDate,
                toDate == null ? LocalDate.of(9999, 12, 31) : toDate, null)) {
            if (intersects(existing.getRequestTypes(), requestTypes)) {
                throw new ConflictError(delegationOverlapCode,
                        "an active delegation already covers the requested types over an overlapping range");
            }
        }

        ApprovalDelegation d = new ApprovalDelegation();
        d.setDelegatorId(delegatorId);
        d.setDelegateId(delegateId);
        d.setRequestTypes(requestTypes == null ? null : List.copyOf(requestTypes));
        d.setFromDate(fromDate);
        d.setToDate(toDate);
        d.setReason(reason);
        d.setStatus(DelegationStatus.ACTIVE);
        d = delegations.save(d);
        audit.emit("approval_delegation", String.valueOf(d.getId()), "created", null, snapshot(d), false);
        return toDto(d);
    }

    private boolean isActiveOn(ApprovalDelegation d, LocalDate on) {
        return d.getStatus() == DelegationStatus.ACTIVE
                && !on.isBefore(d.getFromDate())
                && (d.getToDate() == null || !on.isAfter(d.getToDate()));
    }

    private static boolean intersects(List<String> existing, Set<String> requested) {
        if (existing == null || existing.isEmpty()) return true;
        if (requested == null || requested.isEmpty()) return true;
        for (String t : requested) if (existing.contains(t)) return true;
        return false;
    }

    private static Map<String, Object> snapshot(ApprovalDelegation d) {
        Map<String, Object> m = new HashMap<>();
        m.put("delegatorId", d.getDelegatorId());
        m.put("delegateId", d.getDelegateId());
        m.put("requestTypes", d.getRequestTypes() == null ? List.<String>of() : d.getRequestTypes());
        m.put("fromDate", d.getFromDate() == null ? null : d.getFromDate().toString());
        m.put("toDate", d.getToDate() == null ? null : d.getToDate().toString());
        m.put("status", d.getStatus().name());
        return m;
    }

    private DelegationDto toDto(ApprovalDelegation d) {
        return new DelegationDto(
                d.getId(), d.getDelegatorId(), d.getDelegateId(),
                d.getRequestTypes(), d.getFromDate(), d.getToDate(),
                d.getReason(), d.getStatus().name(),
                d.getRevokedAt(), d.getRevokedBy());
    }

    private CurrentUser require() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("no current user"));
    }
}

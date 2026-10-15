package com.forward.hrms.employee.service.travel;

import com.forward.hrms.commons.approval.service.ApprovalInboxQueries;
import com.forward.hrms.commons.approval.service.ApprovalInboxService;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.approval.spi.ManagerResolver;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.core.money.Money;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.approval.TravelApprovalTypes;
import com.forward.hrms.employee.dto.request.travel.request.*;
import com.forward.hrms.employee.entity.City;
import com.forward.hrms.employee.entity.travel.*;
import com.forward.hrms.employee.repository.travel.*;
import com.forward.hrms.employee.service.ManagerHodChecker;
import com.forward.hrms.employee.dto.response.TravelRequestLegResponse;
import com.forward.hrms.employee.dto.response.TravelRequestParticipantResponse;
import com.forward.hrms.employee.dto.response.TravelRequestResponse;
import com.forward.hrms.attendance.dto.response.ApprovalStepDto;
import com.forward.hrms.attendance.dto.response.CurrentStageDto;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.travel.TravelErrorCode;
import com.forward.hrms.employee.enums.travel.TransportDecision;
import com.forward.hrms.employee.enums.travel.TravelLimitType;
import com.forward.hrms.employee.enums.travel.TravelParticipantRole;
import com.forward.hrms.employee.enums.travel.TravelRequestLegStatus;
import com.forward.hrms.employee.enums.travel.TravelRequestStatus;
import com.forward.hrms.employee.enums.travel.TravelType;
import com.forward.hrms.employee.enums.travel.VehicleSource;
import com.forward.hrms.employee.repository.CityRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates a Travel Request through its lifecycle:
 *
 * <pre>
 * DRAFT ─ submit ─▶ TRANSPORT_PENDING ─▶ DD_PENDING ─▶ APPROVED ─ start ─▶ IN_PROGRESS ─ complete ─▶ COMPLETED
 *   │                    │                   │
 *   └─── cancel ─────────┴─── reject ─▶ TRANSPORT_REJECTED / DD_REJECTED   (each terminal)
 * </pre>
 *
 * <p>The approval hierarchy is TRANSPORT ({@code travel.transport.verify})
 * &rarr; DD/Requester's Head (the requester's manager, final stage), run on
 * the commons-approval {@code TRAVEL_REQUEST} chain — there is no HR stage.
 * Domain status flips happen in
 * {@code TravelRequestApprovalOutcomeHandler}, inside the engine's
 * transaction — this service only opens / advances / cancels the chain.
 * {@link #transportVerify} is the Transport-stage entry point: it records
 * per-leg distance / vehicle verification and then approves or rejects the
 * first stage through the same engine.
 *
 * <p>SETTLEMENT_PENDING / SETTLED are driven by the settlement service.
 *
 * <p>The caller (from the JWT) is always the requester; {@code participants}
 * on the creation payload are co-travelers. HR-on-behalf submission would
 * need an explicit {@code onBehalfOf} field on the request DTO.
 */
@Service
@RequiredArgsConstructor
public class TravelRequestService {

    private final TravelRequestRepository requests;
    private final TravelRequestParticipantRepository participants;
    private final TravelRequestLegRepository legs;
    private final TravelRequestEntitlementRepository entitlements;
    private final TravelPolicyVersionRepository policyVersions;
    private final TravelPolicyGradeLimitRepository gradeLimits;
    private final TravelFuelPolicyRepository fuelPolicies;
    private final EmployeeRepository employees;
    private final CityRepository cities;
    private final TravelNumberService travelNumbers;
    private final TravelParticipantSnapshotResolver snapshots;
    private final ApprovalService approvals;
    private final ApprovalInboxService approvalInbox;
    private final ManagerResolver managerResolver;
    private final ManagerHodChecker managerHodChecker;
    private final TravelApprovalAssembler travelApprovals;
    private final AuditEmitter audit;

    // ── create ───────────────────────────────────────────────────────────────

    @Transactional
    public TravelRequestResponse create(CreateTravelRequest req) {
        CurrentUser caller = requireCaller();
        Employee requester = requireRequesterEmployee(caller);

        // Company rule (for now): only a manager / department head may
        // raise a travel request. This is a CREATION guard, not an approval
        // stage — the approval chain still starts at the creator's own manager.
        if (!managerHodChecker.isManagerOrHod(requester.getId())) {
            throw new ForbiddenError(CommonErrorCode.FORBIDDEN,
                    "Only a manager/department head may raise a travel request");
        }

        if (req.endDatetime().isBefore(req.startDatetime())) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE,
                    "End datetime cannot be before start datetime");
        }

        TravelPolicyVersion version = resolveApplicablePolicy(
                req.travelTypeCode(), req.startDatetime().toLocalDate());

        TravelRequest t = new TravelRequest();
        t.setRequestNo(travelNumbers.nextTravelRequestNumber());
        t.setRequesterEmployeeId(requester.getId());
        t.setTravelTypeCode(req.travelTypeCode());
        t.setCurrencyCode(req.currencyCode());
        t.setPurpose(req.purpose());
        t.setStartDatetime(req.startDatetime());
        t.setEndDatetime(req.endDatetime());
        t.setPolicyVersionId(version.getId());
        t.setStatus(TravelRequestStatus.DRAFT);
        requests.save(t);

        List<TravelRequestParticipant> saved = buildParticipants(t, req.participants(), requester);
        buildLegs(t, req.legs());
        t.setEstimatedAmount(regenerateEntitlements(t, saved, version));

        audit.emit("travel_request", String.valueOf(t.getId()), "created",
                null, snapshot(t), false);
        return toResponse(t);
    }

    // ── edit (DRAFT only, creator only) ──────────────────────────────────────

    /**
     * Pre-submission correction by the creator: replace the editable header
     * fields, participants and legs of a DRAFT request and recompute the
     * entitlement snapshot / estimate. Full-replace semantics — the payload
     * carries the complete participant and leg lists, same as {@code create}.
     *
     * <p>Only the requester may edit, and only while {@code DRAFT}; once
     * {@code SUBMITTED} the chain owns the request and this path is closed.
     */
    @Transactional
    public TravelRequestResponse update(Long id, CreateTravelRequest req) {
        TravelRequest t = requireRequest(id);
        requireStatus(t, TravelRequestStatus.DRAFT);
        requireCreator(t);

        if (req.endDatetime().isBefore(req.startDatetime())) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE,
                    "End datetime cannot be before start datetime");
        }

        Map<String, Object> before = snapshot(t);
        Employee requester = employees.findById(t.getRequesterEmployeeId())
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND,
                        "requester employee not found"));
        TravelPolicyVersion version = resolveApplicablePolicy(
                req.travelTypeCode(), req.startDatetime().toLocalDate());

        t.setTravelTypeCode(req.travelTypeCode());
        t.setCurrencyCode(req.currencyCode());
        t.setPurpose(req.purpose());
        t.setStartDatetime(req.startDatetime());
        t.setEndDatetime(req.endDatetime());
        t.setPolicyVersionId(version.getId());

        // Entitlements FK the participants, so clear them first; flush the
        // deletes before the re-inserts so the participant unique key
        // (uq_travel_request_participants_employee) doesn't collide.
        entitlements.deleteByTravelRequestId(t.getId());
        participants.deleteByTravelRequestId(t.getId());
        legs.deleteByTravelRequestId(t.getId());
        entitlements.flush();

        List<TravelRequestParticipant> saved = buildParticipants(t, req.participants(), requester);
        buildLegs(t, req.legs());
        t.setEstimatedAmount(regenerateEntitlements(t, saved, version));

        audit.emit("travel_request", String.valueOf(t.getId()), "updated",
                before, snapshot(t), false);
        return toResponse(t);
    }

    // ── reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TravelRequestResponse get(Long id) {
        return toResponse(requireRequest(id));
    }

    @Transactional(readOnly = true)
    public Page<TravelRequestResponse> search(TravelRequestStatus status,
                                              String travelTypeCode,
                                              Long requesterEmployeeId,
                                              Long policyVersionId,
                                              Pageable pageable) {
        return requests.search(status, travelTypeCode, requesterEmployeeId, policyVersionId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<TravelRequestResponse> listMine(Pageable pageable) {
        Employee me = requireRequesterEmployee(requireCaller());
        return requests.findByRequesterEmployeeId(me.getId(), pageable).map(this::toResponse);
    }

    /**
     * Stage-aware "travel requests awaiting my approval" inbox — same
     * contract as {@code /leaves/pending-approvals} and
     * {@code /requisitions/pending-approvals}: only rows the caller can act
     * on right now, windowed on the trip dates, newest first.
     */
    @Transactional(readOnly = true)
    public PageResponse<TravelRequestResponse> pendingApprovals(LocalDate from, LocalDate to, String q,
                                                                String travelTypeCode, Pageable pageable) {
        CurrentUser caller = requireCaller();
        return ApprovalInboxQueries.findPending(
                approvalInbox, TravelApprovalTypes.TRAVEL_REQUEST, caller,
                requests::findAllById, rows -> filterInbox(rows, q, travelTypeCode), TravelRequest::getId,
                t -> t.getStartDatetime().toLocalDate(), t -> t.getEndDatetime().toLocalDate(),
                from, to, pageable, this::toResponse);
    }

    /**
     * Inbox filter: {@code q} matches the request id exactly (numeric
     * {@code q} only), or a case-insensitive substring of the request number,
     * requester employee code, or requester name; {@code travelTypeCode} is
     * exact, case-insensitive. Both optional, ANDed. Requesters are loaded in
     * one query, and only when {@code q} is set.
     */
    private List<TravelRequest> filterInbox(List<TravelRequest> rows, String q, String travelTypeCode) {
        String needle = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        String type = travelTypeCode == null ? "" : travelTypeCode.trim();
        if (needle.isEmpty() && type.isEmpty()) return rows;
        Long idNeedle = !needle.isEmpty() && needle.length() <= 18 && needle.chars().allMatch(Character::isDigit)
                ? Long.valueOf(needle) : null;
        Map<Long, Employee> requesters = new HashMap<>();
        if (!needle.isEmpty()) {
            for (Employee e : employees.findAllById(
                    rows.stream().map(TravelRequest::getRequesterEmployeeId).distinct().toList())) {
                requesters.put(e.getId(), e);
            }
        }
        return rows.stream()
                .filter(t -> type.isEmpty() || type.equalsIgnoreCase(t.getTravelTypeCode()))
                .filter(t -> {
                    if (needle.isEmpty()) return true;
                    if (idNeedle != null && idNeedle.equals(t.getId())) return true;
                    if (containsIgnoreCase(t.getRequestNo(), needle)) return true;
                    Employee r = requesters.get(t.getRequesterEmployeeId());
                    return r != null && (containsIgnoreCase(r.getEmployeeCode(), needle)
                            || containsIgnoreCase(r.getFullName(), needle));
                })
                .toList();
    }

    private static boolean containsIgnoreCase(String haystack, String lowerNeedle) {
        return haystack != null && haystack.toLowerCase(java.util.Locale.ROOT).contains(lowerNeedle);
    }

    // ── lifecycle ────────────────────────────────────────────────────────────

    @Transactional
    public TravelRequestResponse submit(Long id) {
        TravelRequest t = requireRequest(id);
        requireStatus(t, TravelRequestStatus.DRAFT);
        requireCreator(t);

        // Stage 2 of the chain is the requester's DD/HOD (MANAGER resolver). If
        // that can't be resolved the request would wedge with an unassignable
        // PENDING step once Transport approves, so refuse the submit up front.
        if (managerResolver.resolveManagerUserId(t.getRequesterEmployeeId()) == null) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE,
                    "cannot submit: the requester has no manager/HOD to run the DD/HOD approval stage");
        }

        List<TravelRequestParticipant> members = participants.findByTravelRequestId(id);
        if (members.isEmpty()) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_REQUEST_NO_PARTICIPANTS,
                    "a travel request needs at least one participant before it can be submitted");
        }
        if (legs.findByTravelRequestIdOrderByLegSequenceAsc(id).isEmpty()) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE,
                    "a travel request needs at least one journey leg before it can be submitted");
        }

        TravelPolicyVersion version = resolveApplicablePolicy(
                t.getTravelTypeCode(), t.getStartDatetime().toLocalDate());
        t.setPolicyVersionId(version.getId());
        t.setEstimatedAmount(regenerateEntitlements(t, members, version));
        t.setSubmittedAt(LocalDateTime.now());
        t.setApprovalReference(TravelApprovalTypes.TRAVEL_REQUEST + "#" + id);

        // DRAFT -> SUBMITTED -> TRANSPORT_PENDING: open the chain (stage 1,
        // TRANSPORT, becomes PENDING) then rest on the Transport stage.
        approvals.openChain(TravelApprovalTypes.TRAVEL_REQUEST, id, t.getRequesterEmployeeId());
        t.setStatus(TravelRequestStatus.TRANSPORT_PENDING);

        audit.emit("travel_request", String.valueOf(id), "submitted",
                null, Map.of("status", "TRANSPORT_PENDING"), true);
        return toResponse(t);
    }

    /**
     * Approve the current pending approval stage (DD/Requester's Head —
     * Transport uses {@link #transportVerify}, though this path also works
     * for a Transport approve with no leg changes). The generic engine enforces
     * that only the actor for the single {@code PENDING} step may act and
     * that the step is still open; the domain status flip is done by
     * {@code TravelRequestApprovalOutcomeHandler}.
     */
    @Transactional
    public TravelRequestResponse approve(Long id, String note) {
        TravelRequest t = requireRequest(id);
        requireActionableApprovalState(t);
        approvals.approve(TravelApprovalTypes.TRAVEL_REQUEST, id, note);
        return toResponse(requireRequest(id));
    }

    /**
     * Reject the current pending approval stage. A rejection is terminal:
     * the engine skips the remaining stages and the outcome handler sets the
     * stage-specific {@code *_REJECTED} status. No later stage is created.
     */
    @Transactional
    public TravelRequestResponse reject(Long id, String reason) {
        TravelRequest t = requireRequest(id);
        requireActionableApprovalState(t);
        approvals.reject(TravelApprovalTypes.TRAVEL_REQUEST, id, reason);
        return toResponse(requireRequest(id));
    }

    /**
     * Transport-stage action ({@code travel.transport.verify}). On
     * {@link TransportDecision#APPROVE}, every leg's {@code actualDistanceKm}
     * is deterministically finalized — Transport's supplied value when it is
     * positive, otherwise the requester's {@code plannedDistanceKm} (never
     * left {@code null}; {@code plannedDistanceKm} itself is never
     * overwritten) — the confirmed vehicle fields are applied, and the fuel
     * policy is resolved server-side from the leg's (just-finalized)
     * vehicle source ({@code travel_fuel_policies.code == vehicleSourceCode}
     * — there is no separate fuel-policy identifier); a vehicle source with
     * no matching active policy fails verification rather than silently
     * issuing no petrol. The entitlement snapshot is regenerated on the
     * finalized distance, and stage 1 is approved so the request advances to
     * DD/Head. On {@link TransportDecision#REJECT} stage 1 is rejected and
     * the chain terminates.
     */
    @Transactional
    public TravelRequestResponse transportVerify(Long id, TransportVerifyRequest req) {
        TravelRequest t = requireRequest(id);
        requireStatus(t, TravelRequestStatus.TRANSPORT_PENDING);

        if (req.decision() == TransportDecision.REJECT) {
            approvals.reject(TravelApprovalTypes.TRAVEL_REQUEST, id, req.remarks());
            return toResponse(requireRequest(id));
        }

        List<TravelRequestLeg> chainLegs = legs.findByTravelRequestIdOrderByLegSequenceAsc(id);
        Map<Long, TravelRequestLeg> legsById = chainLegs.stream()
                .collect(Collectors.toMap(TravelRequestLeg::getId, l -> l));
        Map<Long, TravelVerifyLegRequest> overridesById = new LinkedHashMap<>();
        if (req.legs() != null) {
            for (TravelVerifyLegRequest lv : req.legs()) {
                if (!legsById.containsKey(lv.legId())) {
                    throw new NotFoundError(TravelErrorCode.TRAVEL_LEG_NOT_FOUND,
                            "leg " + lv.legId() + " does not belong to travel request " + id);
                }
                overridesById.put(lv.legId(), lv);
            }
        }

        // Every leg is finalized on APPROVE, not only the ones Transport
        // explicitly listed: a leg Transport left out of `legs` still needs
        // its actualDistanceKm settled to the planned fallback and its fuel
        // policy resolved from whatever vehicle source it already carries.
        // plannedDistanceKm is never written to here.
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (TravelRequestLeg leg : chainLegs) {
            TravelVerifyLegRequest lv = overridesById.get(leg.getId());

            before.put("leg#" + leg.getLegSequence() + ".actualDistanceKm",
                    String.valueOf(leg.getActualDistanceKm()));
            before.put("leg#" + leg.getLegSequence() + ".vehicleSourceCode",
                    String.valueOf(leg.getVehicleSourceCode()));
            before.put("leg#" + leg.getLegSequence() + ".travelFuelPolicyId",
                    String.valueOf(leg.getTravelFuelPolicyId()));
            before.put("leg#" + leg.getLegSequence() + ".petrolIssued",
                    String.valueOf(leg.getPetrolIssued()));

            // A supplied actualDistanceKm > 0 wins; 0, negative or absent
            // means "not verified" and the leg's actual distance settles on
            // the requester's plannedDistanceKm — never left null.
            BigDecimal supplied = lv == null ? null : lv.actualDistanceKm();
            BigDecimal finalActualDistance = supplied != null && supplied.compareTo(BigDecimal.ZERO) > 0
                    ? supplied : leg.getPlannedDistanceKm();
            leg.setActualDistanceKm(finalActualDistance);

            if (lv != null) {
                if (lv.vehicleSourceCode() != null) {
                    leg.setVehicleSourceCode(parseVehicleSource(lv.vehicleSourceCode()));
                }
                if (lv.vehicleNumber() != null) {
                    leg.setVehicleNumber(lv.vehicleNumber());
                }
                if (lv.vehicleDescription() != null) {
                    leg.setVehicleDescription(lv.vehicleDescription());
                }
            }

            // Fuel/petrol issuance: there is no separate fuel-policy
            // identifier — the leg's own (just-finalized) vehicleSourceCode
            // IS the travel_fuel_policies.code lookup key, resolved
            // server-side against the just-finalized actualDistanceKm above
            // (never plannedDistanceKm directly, though they now coincide
            // when Transport didn't override). A leg with no vehicle source
            // (e.g. air/train/public-transport legs) has no fuel issuance.
            // A leg WITH a vehicle source but no matching active policy is a
            // real misconfiguration, not a silent zero — Transport
            // verification is rejected rather than issuing no petrol
            // unnoticed.
            VehicleSource finalSource = leg.getVehicleSourceCode();
            if (finalSource != null) {
                TravelFuelPolicy policy = fuelPolicies
                        .findApplicable(finalSource.name(), LocalDate.now())
                        .orElseThrow(() -> new UnprocessableError(TravelErrorCode.TRAVEL_NO_APPLICABLE_FUEL_POLICY,
                                "no applicable fuel policy for vehicle source " + finalSource));
                leg.setTravelFuelPolicyId(policy.getId());
                leg.setPetrolIssued(calculatePetrolIssued(leg.getActualDistanceKm(), policy.getFuelAverage()));
            } else {
                leg.setTravelFuelPolicyId(null);
                leg.setPetrolIssued(BigDecimal.ZERO);
            }

            after.put("leg#" + leg.getLegSequence() + ".actualDistanceKm",
                    String.valueOf(leg.getActualDistanceKm()));
            after.put("leg#" + leg.getLegSequence() + ".vehicleSourceCode",
                    String.valueOf(leg.getVehicleSourceCode()));
            after.put("leg#" + leg.getLegSequence() + ".travelFuelPolicyId",
                    String.valueOf(leg.getTravelFuelPolicyId()));
            after.put("leg#" + leg.getLegSequence() + ".petrolIssued",
                    String.valueOf(leg.getPetrolIssued()));
        }

        // Request-level summary is derived from the persisted legs, never
        // recomputed independently: total distance and petrol are each a
        // straight SUM over every leg's actualDistanceKm / petrolIssued, and
        // the fuel policy is the single one shared by every leg that has one
        // (null when legs carry different policies). actualDistanceKm stays
        // the sole authoritative Transport-approved distance — this summary
        // never overrides it.
        BigDecimal totalDistance = BigDecimal.ZERO;
        BigDecimal totalPetrol = BigDecimal.ZERO;
        Long sharedFuelPolicyId = null;
        boolean sharedFuelPolicyIdConflicts = false;
        for (TravelRequestLeg leg : chainLegs) {
            if (leg.getActualDistanceKm() != null) {
                totalDistance = totalDistance.add(leg.getActualDistanceKm());
            }
            totalPetrol = totalPetrol.add(leg.getPetrolIssued());
            if (leg.getTravelFuelPolicyId() != null) {
                if (sharedFuelPolicyId == null) {
                    sharedFuelPolicyId = leg.getTravelFuelPolicyId();
                } else if (!sharedFuelPolicyId.equals(leg.getTravelFuelPolicyId())) {
                    sharedFuelPolicyIdConflicts = true;
                }
            }
        }
        t.setTotalDistanceKm(totalDistance);
        t.setPetrolIssued(totalPetrol);
        t.setTravelFuelPolicyId(sharedFuelPolicyIdConflicts ? null : sharedFuelPolicyId);

        // Recompute distance-based entitlements on the effective distance,
        // against the policy version already attached to the request — a
        // historical request never switches to a newer policy version.
        List<TravelRequestParticipant> members = participants.findByTravelRequestId(id);
        TravelPolicyVersion version = policyVersions.findById(t.getPolicyVersionId())
                .orElseThrow(() -> new UnprocessableError(TravelErrorCode.TRAVEL_NO_APPLICABLE_POLICY,
                        "the policy version attached to this request no longer exists"));
        t.setEstimatedAmount(regenerateEntitlements(t, members, version));

        approvals.approve(TravelApprovalTypes.TRAVEL_REQUEST, id, req.remarks());

        // chainLegs is never empty here — submit() requires at least one leg
        // — so every APPROVE finalizes at least one leg's distance and audits it.
        audit.emit("travel_request", String.valueOf(id), "transport_verified", before, after, true);
        return toResponse(requireRequest(id));
    }

    @Transactional
    public TravelRequestResponse cancel(Long id, String reason) {
        TravelRequest t = requireRequest(id);
        if (!isCancellable(t)) {
            throw new ConflictError(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE,
                    "only a DRAFT or in-approval travel request can be cancelled");
        }
        if (t.getStatus() != TravelRequestStatus.DRAFT) {
            approvals.cancelChain(TravelApprovalTypes.TRAVEL_REQUEST, id);
        }
        t.setStatus(TravelRequestStatus.CANCELLED);
        t.setCancelledAt(LocalDateTime.now());
        t.setCancellationReason(reason);
        audit.emit("travel_request", String.valueOf(id), "cancelled",
                null, Map.of("status", "CANCELLED", "reason", reason == null ? "" : reason), true);
        return toResponse(t);
    }

    @Transactional
    public TravelRequestResponse startTravel(Long id) {
        TravelRequest t = requireRequest(id);
        requireStatus(t, TravelRequestStatus.APPROVED);
        t.setStatus(TravelRequestStatus.IN_PROGRESS);
        t.setStartedAt(LocalDateTime.now());
        audit.emit("travel_request", String.valueOf(id), "started",
                null, Map.of("status", "IN_PROGRESS"), true);
        return toResponse(t);
    }

    @Transactional
    public TravelRequestResponse completeTravel(Long id) {
        TravelRequest t = requireRequest(id);
        requireStatus(t, TravelRequestStatus.IN_PROGRESS);
        t.setStatus(TravelRequestStatus.COMPLETED);
        t.setCompletedAt(LocalDateTime.now());
        audit.emit("travel_request", String.valueOf(id), "completed",
                null, Map.of("status", "COMPLETED"), true);
        return toResponse(t);
    }

    // ── build helpers ────────────────────────────────────────────────────────

    private List<TravelRequestParticipant> buildParticipants(TravelRequest t,
                                                             List<CreateTravelParticipantRequest> supplied,
                                                             Employee requester) {
        // requester first (REQUESTER), then each distinct co-traveler (PARTICIPANT)
        LinkedHashMap<Long, TravelParticipantRole> roleById = new LinkedHashMap<>();
        roleById.put(requester.getId(), TravelParticipantRole.REQUESTER);

        List<TravelRequestParticipant> out = new ArrayList<>();
        Map<Long, Employee> resolved = new LinkedHashMap<>();
        resolved.put(requester.getId(), requester);

        if (supplied != null) {
            for (CreateTravelParticipantRequest p : supplied) {
                Employee e = employees.findByEmployeeCode(p.employeeCode())
                        .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND,
                                "employee not found: " + p.employeeCode()));
                roleById.putIfAbsent(e.getId(), TravelParticipantRole.PARTICIPANT);
                resolved.putIfAbsent(e.getId(), e);
            }
        }

        roleById.forEach((employeeId, role) -> {
            TravelRequestParticipant participant = new TravelRequestParticipant();
            participant.setTravelRequestId(t.getId());
            snapshots.apply(participant, resolved.get(employeeId));
            participant.setParticipantRole(role);
            participant.setTravelling(true);
            participant.setEntitlementEligible(true);
            out.add(participants.save(participant));
        });
        return out;
    }

    private void buildLegs(TravelRequest t, List<CreateTravelLegRequest> supplied) {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (CreateTravelLegRequest l : supplied) {
            if (!seen.add(l.legSequence())) {
                throw new UnprocessableError(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE,
                        "duplicate leg sequence " + l.legSequence());
            }
            TravelRequestLeg leg = new TravelRequestLeg();
            leg.setTravelRequestId(t.getId());
            leg.setLegSequence(l.legSequence());
            leg.setOriginLocationCode(l.originLocationCode());
            leg.setOriginLocationNameSnapshot(locationName(l.originLocationCode()));
            leg.setDestinationLocationCode(l.destinationLocationCode());
            leg.setDestinationLocationNameSnapshot(locationName(l.destinationLocationCode()));
            leg.setPlannedDepartureAt(l.plannedDepartureAt());
            leg.setPlannedArrivalAt(l.plannedArrivalAt());
            leg.setTransportationTypeCode(l.transportationTypeCode());
            leg.setVehicleSourceCode(parseVehicleSource(l.vehicleSourceCode()));
            leg.setVehicleNumber(l.vehicleNumber());
            leg.setVehicleDescription(l.vehicleDescription());
            leg.setPlannedDistanceKm(l.plannedDistanceKm());
            leg.setLegStatus(TravelRequestLegStatus.PLANNED);
            leg.setRemarks(l.remarks());
            legs.save(leg);
        }
    }

    /**
     * Delete-and-recreate the entitlement snapshot for every eligible
     * participant from the active grade limits of {@code version}. Returns
     * the summed {@code totalLimitAmount} for use as the request estimate.
     */
    private BigDecimal regenerateEntitlements(TravelRequest t,
                                              List<TravelRequestParticipant> members,
                                              TravelPolicyVersion version) {
        // Flush to delete before the re-inserts: Hibernate orders INSERTs
        // ahead of Deletes at commit, so on a re-run (submit / edit) the new
        // rows would collide with the still-present old ones under the
        // partial unique index uq_travel_request_entitlements_active_type
        // (travel_request_participant_id, entitlement_type_code) WHERE is_active.
        entitlements.deleteByTravelRequestId(t.getId());
        entitlements.flush();

        List<TravelPolicyGradeLimit> limits = gradeLimits.findActiveByPolicyVersionId(version.getId());
        long days = ChronoUnit.DAYS.between(
                t.getStartDatetime().toLocalDate(), t.getEndDatetime().toLocalDate()) + 1;
        BigDecimal travelDays = BigDecimal.valueOf(Math.max(days, 1));
        // Effective distance per leg: the Transport-verified actual distance
        // when set (> 0), otherwise the requester's planned distance. Only
        // PER_KM entitlements consume this.
        BigDecimal totalDistanceKm = legs.findByTravelRequestIdOrderByLegSequenceAsc(t.getId()).stream()
                .map(TravelRequestLeg::effectiveDistanceKm)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Money estimate = Money.ZERO;
        for (TravelRequestParticipant p : members) {
            if (!p.isEntitlementEligible() || p.getGradeIdSnapshot() == null) {
                continue;
            }
            for (TravelPolicyGradeLimit limit : limits) {
                if (!limit.getGradeId().equals(p.getGradeIdSnapshot())) {
                    continue;
                }
                BigDecimal quantity = entitlementQuantity(limit.getLimitType(), travelDays, totalDistanceKm);
                BigDecimal total = Money.of(limit.getLimitAmount()).multiply(quantity).amount();

                TravelRequestEntitlement e = new TravelRequestEntitlement();
                e.setTravelRequestId(t.getId());
                e.setTravelRequestParticipantId(p.getId());
                e.setPolicyVersionId(version.getId());
                e.setPolicyGradeLimitId(limit.getId());
                e.setEmployeeId(p.getEmployeeId());
                e.setGradeIdSnapshot(p.getGradeIdSnapshot());
                e.setGradeCodeSnapshot(p.getGradeCodeSnapshot());
                e.setGradeNameSnapshot(p.getGradeNameSnapshot());
                e.setEntitlementTypeCode(limit.getExpenseTypeCode());
                e.setQuantityAllowed(quantity);
                e.setUnitLimitAmount(limit.getLimitAmount());
                e.setTotalLimitAmount(total);
                e.setCurrencyCode(limit.getCurrencyCode());
                e.setCalculationBasisCode(calculationBasis(limit.getLimitType()));
                e.setTravelDays(travelDays);
                e.setPolicyUnitLimitAmount(limit.getLimitAmount());
                e.setActive(true);
                e.setEffectiveAt(LocalDateTime.now());
                entitlements.save(e);

                estimate = estimate.add(Money.of(total));
            }
        }
        return estimate.amount();
    }

    private static BigDecimal entitlementQuantity(TravelLimitType type,
                                                  BigDecimal travelDays,
                                                  BigDecimal totalDistanceKm) {
        return switch (type) {
            case PER_DAY -> travelDays;
            case PER_NIGHT -> travelDays.subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
            case PER_KM -> totalDistanceKm;
            case PER_TRIP, PER_CLAIM, FIXED -> BigDecimal.ONE;
        };
    }

    private static String calculationBasis(TravelLimitType type) {
        return switch (type) {
            case PER_DAY -> "POLICY_PER_DAY";
            case PER_NIGHT -> "POLICY_PER_NIGHT";
            default -> "POLICY";
        };
    }

    private String locationName(String code) {
        return cities.findByCode(code).map(City::getName).orElse(code);
    }

    /**
     * {@code null}/blank means "not supplied" and leaves the field
     * untouched by the caller. Any non-blank value must be a real
     * {@link VehicleSource} code — an unknown code is a validation error,
     * never silently discarded to {@code null} (that previously hid typos
     * and mismatched values from both the requester and Transport).
     */
    private static VehicleSource parseVehicleSource(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return VehicleSource.valueOf(code);
        } catch (IllegalArgumentException ex) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_INVALID_VEHICLE_SOURCE,
                    "unknown vehicle source code: " + code);
        }
    }

    /**
     * {@code actualDistanceKm / fuelAverage}, CEILING at scale 2 via the
     * shared {@link Money} rounding convention — never plannedDistanceKm.
     * {@code fuelAverage == 0} (a valid "no petrol issued" policy) and a
     * {@code null} distance (not yet Transport-verified) both yield zero;
     * neither is a division-by-zero error.
     */
    private static BigDecimal calculatePetrolIssued(BigDecimal actualDistanceKm, BigDecimal fuelAverage) {
        if (actualDistanceKm == null || fuelAverage.compareTo(BigDecimal.ZERO) <= 0) {
            return Money.ZERO.amount();
        }
        return Money.of(actualDistanceKm).divide(fuelAverage).amount();
    }

    // ── lookups / guards ─────────────────────────────────────────────────────

    /**
     * Resolves the policy version a Travel Request's entitlements are
     * generated from:
     *
     * <pre>
     * travelTypeCode -&gt; policyCode -&gt; the one ACTIVE version covering onDate
     * </pre>
     *
     * <p>The travel type is the selector (LOCAL -&gt; LOCAL_FACTORY_TRAVEL,
     * INTERNATIONAL -&gt; INTERNATIONAL_FACTORY_TRAVEL via {@link TravelType}); an
     * unknown code is rejected rather than resolving an arbitrary policy.
     * DRAFT, INACTIVE and RETIRED versions are never selected, and a trip
     * date outside the ACTIVE version's effective window is a 422 rather
     * than a silent fall-back to an older version.
     *
     * <p>More than one ACTIVE version covering {@code onDate} is
     * inconsistent master data, not a version race — it raises a conflict
     * instead of "highest version wins", which would hide the duplicate.
     */
    private TravelPolicyVersion resolveApplicablePolicy(String travelTypeCode, LocalDate onDate) {
        String code = TravelType.parse(travelTypeCode).policyCode();
        List<TravelPolicyVersion> applicable = policyVersions.findApplicable(code, onDate);
        if (applicable.isEmpty()) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_NO_APPLICABLE_POLICY,
                    "No ACTIVE '" + code + "' policy version covers " + onDate);
        }
        if (applicable.size() > 1) {
            String versions = applicable.stream()
                    .map(v -> "v" + v.getVersionNo()).collect(Collectors.joining(", "));
            throw new ConflictError(TravelErrorCode.TRAVEL_MULTIPLE_ACTIVE_POLICIES,
                    "policy code '" + code + "' has " + applicable.size() + " ACTIVE versions covering "
                            + onDate + " (" + versions + "); exactly one is required");
        }
        return applicable.getFirst();
    }

    private TravelRequest requireRequest(Long id) {
        return requests.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        TravelErrorCode.TRAVEL_REQUEST_NOT_FOUND, "travel request not found"));
    }

    private static void requireStatus(TravelRequest t, TravelRequestStatus expected) {
        if (t.getStatus() != expected) {
            throw new ConflictError(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE,
                    "travel request is " + t.getStatus() + ", expected " + expected);
        }
    }

    /**
     * Statuses at which an approval stage is open and can be acted on.
     * {@code SUBMITTED} is kept for rows raised before the staged statuses
     * existed. The engine still enforces which single stage is actionable
     * and by whom.
     */
    private static final Set<TravelRequestStatus> ACTIONABLE_APPROVAL_STATES = EnumSet.of(
            TravelRequestStatus.SUBMITTED,
            TravelRequestStatus.TRANSPORT_PENDING,
            TravelRequestStatus.DD_PENDING);

    private static void requireActionableApprovalState(TravelRequest t) {
        if (!ACTIONABLE_APPROVAL_STATES.contains(t.getStatus())) {
            throw new ConflictError(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE,
                    "travel request is " + t.getStatus() + " and is not at an actionable approval stage");
        }
    }

    private static CurrentUser requireCaller() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("Authentication required"));
    }

    private Employee requireRequesterEmployee(CurrentUser caller) {
        return employees.findByUserId(caller.userId())
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND,
                        "no employee record linked to the current user"));
    }

    /**
     * Guard for the pre-submission (DRAFT) operations: edit and submit are
     * the creator's own verification steps, so only the requester may run
     * them. Anyone else — including a later approver — is 403.
     */
    private void requireCreator(TravelRequest t) {
        Employee me = requireRequesterEmployee(requireCaller());
        if (!t.getRequesterEmployeeId().equals(me.getId())) {
            throw new ForbiddenError(CommonErrorCode.FORBIDDEN,
                    "only the creator may edit or submit this travel request");
        }
    }

    private TravelRequestResponse toResponse(TravelRequest t) {
        List<TravelRequestLegResponse> legDtos = legs.findByTravelRequestIdOrderByLegSequenceAsc(t.getId())
                .stream().map(TravelRequestService::legDto).toList();
        List<TravelRequestParticipantResponse> participantDtos = participants.findByTravelRequestId(t.getId())
                .stream().map(TravelRequestService::participantDto).toList();
        TravelApprovalAssembler.View chain = travelApprovals.view(
                TravelApprovalTypes.TRAVEL_REQUEST, t.getId(), isCancellable(t));
        // The attached policy version is looked up for its real, persisted
        // versionNo — the snapshot itself (policyVersionId) never changes to
        // a newer version, this only reads its business version number.
        Integer policyVersionNo = t.getPolicyVersionId() == null ? null
                : policyVersions.findById(t.getPolicyVersionId())
                        .map(TravelPolicyVersion::getVersionNo).orElse(null);
        return buildResponse(t, legDtos, participantDtos, policyVersionNo, chain.currentStage(), chain.steps(),
                chain.actions());
    }

    /**
     * {@code TravelRequestLeg} → {@link TravelRequestLegResponse}. Kept here
     * (rather than a static factory on the DTO) because the DTO lives in the
     * shared {@code api} module and can't depend on the employee-service
     * entity — same split as {@code FactoryTourDtoAssembler.dto}.
     */
    private static TravelRequestLegResponse legDto(TravelRequestLeg e) {
        return new TravelRequestLegResponse(
                e.getId(), e.getTravelRequestId(), e.getLegSequence(),
                e.getOriginLocationCode(), e.getOriginLocationNameSnapshot(),
                e.getDestinationLocationCode(), e.getDestinationLocationNameSnapshot(),
                e.getPlannedDepartureAt(), e.getPlannedArrivalAt(),
                e.getActualDepartureAt(), e.getActualArrivalAt(),
                e.getLegStatus() == null ? null : e.getLegStatus().name(),
                e.getVehicleSourceCode() == null ? null : e.getVehicleSourceCode().name(),
                e.getVehicleNumber(), e.getVehicleDescription(),
                e.getPlannedDistanceKm(), e.getActualDistanceKm(), e.effectiveDistanceKm(),
                e.getTravelFuelPolicyId(), e.getPetrolIssued(),
                e.getRemarks(), e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
    }

    /** {@code TravelRequestParticipant} → {@link TravelRequestParticipantResponse}. See {@link #legDto}. */
    private static TravelRequestParticipantResponse participantDto(TravelRequestParticipant e) {
        return new TravelRequestParticipantResponse(
                e.getId(), e.getEmployeeNoSnapshot(), e.getEmployeeNameSnapshot(),
                e.getParticipantRole() == null ? null : e.getParticipantRole().name(),
                e.getGradeIdSnapshot(), e.getGradeCodeSnapshot(), e.getGradeNameSnapshot(),
                e.getDesignationIdSnapshot(), e.getDesignationNameSnapshot(),
                e.getDepartmentIdSnapshot(), e.getDepartmentNameSnapshot(),
                e.getSectionIdSnapshot(), e.getSectionNameSnapshot(),
                e.isTravelling(), e.getRemovalReason(), e.getRemovedAt(),
                e.isEntitlementEligible(), e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
    }

    /**
     * {@code TravelRequest} + its already-built children → {@link TravelRequestResponse}.
     * The root-level requester code/name are derived from the REQUESTER
     * participant snapshot — the single source of truth already carried in
     * {@code participants}. No extra lookup, no denormalized column.
     */
    private static TravelRequestResponse buildResponse(TravelRequest e,
                                                       List<TravelRequestLegResponse> legs,
                                                       List<TravelRequestParticipantResponse> participants,
                                                       Integer policyVersionNo,
                                                       CurrentStageDto currentStage,
                                                       List<ApprovalStepDto> steps,
                                                       List<String> actions) {
        TravelRequestParticipantResponse requester = participants == null ? null
                : participants.stream()
                        .filter(p -> "REQUESTER".equals(p.participantRole()))
                        .findFirst().orElse(null);
        return new TravelRequestResponse(
                e.getId(), e.getRequestNo(),
                requester == null ? null : requester.employeeCode(),
                requester == null ? null : requester.employeeName(),
                e.getTravelTypeCode(), e.getCurrencyCode(), e.getPurpose(),
                e.getStartDatetime(), e.getEndDatetime(), legs,
                e.getPolicyVersionId(), policyVersionNo,
                e.getStatus() == null ? null : e.getStatus().name(),
                e.getApprovalReference(), e.getEstimatedAmount(),
                e.getTravelFuelPolicyId(), e.getPetrolIssued(), e.getTotalDistanceKm(),
                e.getSubmittedAt(), e.getApprovedAt(), e.getStartedAt(), e.getCompletedAt(),
                e.getCancelledAt(), e.getCancellationReason(), participants,
                e.getCreatedAt(), e.getUpdatedAt(), e.getVersion(),
                currentStage, steps, actions);
    }

    private static boolean isCancellable(TravelRequest t) {
        return t.getStatus() == TravelRequestStatus.DRAFT
                || ACTIONABLE_APPROVAL_STATES.contains(t.getStatus());
    }

    private static Map<String, Object> snapshot(TravelRequest t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestNo", t.getRequestNo());
        m.put("requesterEmployeeId", t.getRequesterEmployeeId());
        m.put("travelTypeCode", t.getTravelTypeCode());
        m.put("currencyCode", t.getCurrencyCode());
        m.put("startDatetime", String.valueOf(t.getStartDatetime()));
        m.put("endDatetime", String.valueOf(t.getEndDatetime()));
        m.put("policyVersionId", t.getPolicyVersionId());
        m.put("status", String.valueOf(t.getStatus()));
        m.put("estimatedAmount", String.valueOf(t.getEstimatedAmount()));
        return m;
    }
}

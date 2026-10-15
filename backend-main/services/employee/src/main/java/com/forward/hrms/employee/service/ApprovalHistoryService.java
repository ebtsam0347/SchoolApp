package com.forward.hrms.employee.service;

import com.forward.hrms.attendance.dto.response.ApprovalDecisionRow;
import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.commons.approval.repository.ApprovalStepRepository;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeRequisition;
import com.forward.hrms.employee.entity.EmployeeRequisitionDetail;
import com.forward.hrms.employee.entity.FactoryTour;
import com.forward.hrms.employee.entity.travel.TravelRequest;
import com.forward.hrms.employee.entity.travel.TravelRequestAdvance;
import com.forward.hrms.employee.entity.travel.TravelRequestParticipant;
import com.forward.hrms.employee.entity.travel.TravelRequestSettlement;
import com.forward.hrms.employee.enums.RequestType;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.EmployeeRequisitionDetailRepository;
import com.forward.hrms.employee.repository.EmployeeRequisitionRepository;
import com.forward.hrms.employee.repository.FactoryTourRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestAdvanceRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestParticipantRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestSettlementRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Approvals I have decided" history, mirroring attendance's
 * {@code ApprovalHistoryService} — unified across requisitions
 * (REQUISITION_NEW_HIRE / REQUISITION_REPLACEMENT), factory tours
 * (FACTORY_TOUR_LOCAL / FACTORY_TOUR_INTL) and travel
 * (TRAVEL_REQUEST / TRAVEL_ADVANCE / TRAVEL_SETTLEMENT). Requisitions
 * (a role, not a person) and factory tours (denormalized cardNo/name, no
 * employeeId column) do not always resolve to a real employee id —
 * {@code employeeId = 0} with null employeeCode/fullName means "no subject
 * employee", the same convention payroll's short-term-advance-batch
 * history rows use. Travel advances / settlements carry the subject on the
 * participant snapshot (employeeNoSnapshot / employeeNameSnapshot).
 */
@Service
@RequiredArgsConstructor
public class ApprovalHistoryService {

    private static final int MAX_WINDOW_DAYS = 366;

    private final ApprovalStepRepository steps;
    private final EmployeeRequisitionRepository requisitions;
    private final EmployeeRequisitionDetailRepository requisitionDetails;
    private final FactoryTourRepository factoryTours;
    private final TravelRequestRepository travelRequests;
    private final TravelRequestAdvanceRepository travelAdvances;
    private final TravelRequestSettlementRepository travelSettlements;
    private final TravelRequestParticipantRepository travelParticipants;
    private final EmployeeRepository employees;

    @Transactional(readOnly = true)
    public PageResponse<ApprovalDecisionRow> history(
            LocalDate from, LocalDate to,
            RequestType type, StepStatus decision,
            Pageable pageable) {
        if (from == null || to == null) {
            throw new UnprocessableError(EmployeeErrorCode.APPROVAL_HISTORY_DATE_RANGE_INVALID,
                    "from and to are required");
        }
        if (to.isBefore(from)) {
            throw new UnprocessableError(EmployeeErrorCode.APPROVAL_HISTORY_DATE_RANGE_INVALID,
                    "to must be on or after from");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > MAX_WINDOW_DAYS) {
            throw new UnprocessableError(EmployeeErrorCode.APPROVAL_HISTORY_DATE_RANGE_INVALID,
                    "search window must be at most " + MAX_WINDOW_DAYS + " days");
        }

        CurrentUser actor = currentUser();
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();
        Page<ApprovalStep> page = steps.findDecisionsByDecider(
                actor.userId(), fromTs, toExclusive, type == null ? null : type.name(), decision, pageable);
        if (page.isEmpty()) {
            return PageResponse.from(page, List.of());
        }

        Set<Long> requisitionIds = new HashSet<>();
        Set<Long> tourIds = new HashSet<>();
        Set<Long> travelRequestIds = new HashSet<>();
        Set<Long> travelAdvanceIds = new HashSet<>();
        Set<Long> travelSettlementIds = new HashSet<>();
        for (ApprovalStep s : page.getContent()) {
            switch (s.getRequestType()) {
                case "REQUISITION_NEW_HIRE", "REQUISITION_REPLACEMENT" -> requisitionIds.add(s.getRequestId());
                case "FACTORY_TOUR_LOCAL", "FACTORY_TOUR_INTL" -> tourIds.add(s.getRequestId());
                case "TRAVEL_REQUEST" -> travelRequestIds.add(s.getRequestId());
                case "TRAVEL_ADVANCE" -> travelAdvanceIds.add(s.getRequestId());
                case "TRAVEL_SETTLEMENT" -> travelSettlementIds.add(s.getRequestId());
                default -> { /* unreachable — this service only decides the types above */ }
            }
        }
        Map<Long, EmployeeRequisition> requisitionMap = indexById(requisitions.findAllById(requisitionIds), EmployeeRequisition::getId);
        Map<Long, EmployeeRequisitionDetail> requisitionDetailMap = firstDetailsByRequisitionId(requisitionIds);
        Map<Long, FactoryTour> tourMap = indexById(factoryTours.findAllById(tourIds), FactoryTour::getId);
        Map<Long, TravelRequest> travelRequestMap = indexById(travelRequests.findAllById(travelRequestIds), TravelRequest::getId);
        Map<Long, TravelRequestAdvance> travelAdvanceMap = indexById(travelAdvances.findAllById(travelAdvanceIds), TravelRequestAdvance::getId);
        Map<Long, TravelRequestSettlement> travelSettlementMap = indexById(travelSettlements.findAllById(travelSettlementIds), TravelRequestSettlement::getId);

        Set<Long> employeeIds = new HashSet<>();
        for (EmployeeRequisitionDetail detail : requisitionDetailMap.values()) {
            if (detail.getReplacingEmployeeId() != null) employeeIds.add(detail.getReplacingEmployeeId());
        }
        for (TravelRequest tr : travelRequestMap.values()) {
            if (tr.getRequesterEmployeeId() != null) employeeIds.add(tr.getRequesterEmployeeId());
        }
        Map<Long, Employee> employeeMap = employeeIds.isEmpty()
                ? Map.of() : indexById(employees.findAllById(employeeIds), Employee::getId);

        Set<Long> participantIds = new HashSet<>();
        for (TravelRequestAdvance a : travelAdvanceMap.values()) participantIds.add(a.getTravelRequestParticipantId());
        for (TravelRequestSettlement st : travelSettlementMap.values()) participantIds.add(st.getTravelRequestParticipantId());
        Map<Long, TravelRequestParticipant> participantMap = participantIds.isEmpty()
                ? Map.of() : indexById(travelParticipants.findAllById(participantIds), TravelRequestParticipant::getId);

        List<ApprovalDecisionRow> rows = page.getContent().stream()
                .map(s -> toRow(s, requisitionMap, requisitionDetailMap, tourMap, employeeMap,
                        travelRequestMap, travelAdvanceMap, travelSettlementMap, participantMap))
                .filter(java.util.Objects::nonNull)
                .toList();
        return PageResponse.from(page, rows);
    }

    private ApprovalDecisionRow toRow(ApprovalStep s,
                                      Map<Long, EmployeeRequisition> requisitionMap,
                                      Map<Long, EmployeeRequisitionDetail> requisitionDetailMap,
                                      Map<Long, FactoryTour> tourMap,
                                      Map<Long, Employee> employeeMap,
                                      Map<Long, TravelRequest> travelRequestMap,
                                      Map<Long, TravelRequestAdvance> travelAdvanceMap,
                                      Map<Long, TravelRequestSettlement> travelSettlementMap,
                                      Map<Long, TravelRequestParticipant> participantMap) {
        return switch (s.getRequestType()) {
            case "REQUISITION_NEW_HIRE", "REQUISITION_REPLACEMENT" -> {
                EmployeeRequisition r = requisitionMap.get(s.getRequestId());
                if (r == null) yield null;
                EmployeeRequisitionDetail detail = requisitionDetailMap.get(r.getId());
                Long replacingId = detail == null ? null : detail.getReplacingEmployeeId();
                Employee emp = replacingId == null ? null : employeeMap.get(replacingId);
                yield new ApprovalDecisionRow(
                        s.getRequestType(),
                        r.getId(),
                        null,
                        replacingId == null ? 0 : replacingId,
                        emp == null ? null : emp.getEmployeeCode(),
                        emp == null ? null : emp.getFullName(),
                        s.getDecidedAt(),
                        r.getCreatedAt(),
                        s.getStatus().name(),
                        s.getStageNo(),
                        s.getLabel(),
                        s.getNote(),
                        detail == null ? null : detail.getRequiredUntil(),
                        null,
                        null,
                        null,
                        r.getRequestReason());
            }
            case "FACTORY_TOUR_LOCAL", "FACTORY_TOUR_INTL" -> {
                FactoryTour t = tourMap.get(s.getRequestId());
                if (t == null) yield null;
                yield new ApprovalDecisionRow(
                        s.getRequestType(),
                        t.getId(),
                        t.getRequestId(),
                        0,
                        t.getCardNo(),
                        t.getName(),
                        s.getDecidedAt(),
                        t.getCreatedAt(),
                        s.getStatus().name(),
                        s.getStageNo(),
                        s.getLabel(),
                        s.getNote(),
                        t.getTourStart() == null ? null : t.getTourStart().toLocalDate(),
                        t.getTourEnd() == null ? null : t.getTourEnd().toLocalDate(),
                        null,
                        null,
                        t.getPurpose());
            }
            case "TRAVEL_REQUEST" -> {
                TravelRequest tr = travelRequestMap.get(s.getRequestId());
                if (tr == null) yield null;
                Employee emp = tr.getRequesterEmployeeId() == null
                        ? null : employeeMap.get(tr.getRequesterEmployeeId());
                yield new ApprovalDecisionRow(
                        s.getRequestType(),
                        tr.getId(),
                        tr.getRequestNo(),
                        tr.getRequesterEmployeeId() == null ? 0 : tr.getRequesterEmployeeId(),
                        emp == null ? null : emp.getEmployeeCode(),
                        emp == null ? null : emp.getFullName(),
                        s.getDecidedAt(),
                        tr.getCreatedAt(),
                        s.getStatus().name(),
                        s.getStageNo(),
                        s.getLabel(),
                        s.getNote(),
                        tr.getStartDatetime() == null ? null : tr.getStartDatetime().toLocalDate(),
                        tr.getEndDatetime() == null ? null : tr.getEndDatetime().toLocalDate(),
                        null,
                        null,
                        tr.getPurpose());
            }
            case "TRAVEL_ADVANCE" -> {
                TravelRequestAdvance a = travelAdvanceMap.get(s.getRequestId());
                if (a == null) yield null;
                TravelRequestParticipant p = participantMap.get(a.getTravelRequestParticipantId());
                yield new ApprovalDecisionRow(
                        s.getRequestType(),
                        a.getId(),
                        a.getAdvanceNumber(),
                        p == null ? 0 : p.getEmployeeId(),
                        p == null ? null : p.getEmployeeNoSnapshot(),
                        p == null ? null : p.getEmployeeNameSnapshot(),
                        s.getDecidedAt(),
                        a.getCreatedAt(),
                        s.getStatus().name(),
                        s.getStageNo(),
                        s.getLabel(),
                        s.getNote(),
                        null,
                        null,
                        null,
                        null,
                        a.getPurpose());
            }
            case "TRAVEL_SETTLEMENT" -> {
                TravelRequestSettlement st = travelSettlementMap.get(s.getRequestId());
                if (st == null) yield null;
                TravelRequestParticipant p = participantMap.get(st.getTravelRequestParticipantId());
                yield new ApprovalDecisionRow(
                        s.getRequestType(),
                        st.getId(),
                        st.getSettlementNumber(),
                        p == null ? 0 : p.getEmployeeId(),
                        p == null ? null : p.getEmployeeNoSnapshot(),
                        p == null ? null : p.getEmployeeNameSnapshot(),
                        s.getDecidedAt(),
                        st.getCreatedAt(),
                        s.getStatus().name(),
                        s.getStageNo(),
                        s.getLabel(),
                        s.getNote(),
                        null,
                        null,
                        null,
                        null,
                        st.getSettlementSummary());
            }
            default -> null;
        };
    }

    private Map<Long, EmployeeRequisitionDetail> firstDetailsByRequisitionId(Set<Long> requisitionIds) {
        if (requisitionIds.isEmpty()) return Map.of();
        Map<Long, EmployeeRequisitionDetail> out = new HashMap<>();
        for (EmployeeRequisitionDetail detail : requisitionDetails.findAllByRequisitionHeadIdIn(requisitionIds)) {
            out.putIfAbsent(detail.getRequisitionHeadId(), detail);
        }
        return out;
    }

    private static <T> Map<Long, T> indexById(Iterable<T> rows, java.util.function.ToLongFunction<T> idOf) {
        Map<Long, T> out = new HashMap<>();
        for (T row : rows) out.put(idOf.applyAsLong(row), row);
        return out;
    }

    private CurrentUser currentUser() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("no current user"));
    }
}

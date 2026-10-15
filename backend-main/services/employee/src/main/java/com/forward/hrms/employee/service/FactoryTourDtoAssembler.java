package com.forward.hrms.employee.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.forward.hrms.attendance.dto.response.ApprovalStepDto;
import com.forward.hrms.attendance.dto.response.CurrentStageDto;
import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.employee.dto.response.DesignationDto;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.employee.dto.response.FactoryTourDto;
import com.forward.hrms.employee.dto.response.SectionDto;
import com.forward.hrms.employee.dto.response.TravelPersonDto;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.FactoryTour;
import com.forward.hrms.employee.enums.TourStatus;
import com.forward.hrms.employee.enums.TourType;
import com.forward.hrms.employee.repository.EmployeeRepository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the approval-aware {@link FactoryTourDto} shape (currentStage,
 * steps, actions) and resolves requester {@link EmployeeSummary} rows —
 * mirrors attendance's {@code LeaveDtoAssembler} so the FE gets the same
 * JSON contract across request types.
 */
@Component
public class FactoryTourDtoAssembler {

    private final ApprovalService approvals;
    private final EmployeeRepository employees;
    private final DepartmentService departments;
    private final SectionService sections;
    private final DesignationService designations;
    private final ObjectMapper json;

    public FactoryTourDtoAssembler(ApprovalService approvals, EmployeeRepository employees,
            DepartmentService departments, SectionService sections, DesignationService designations,
            @Qualifier("outboxObjectMapper") ObjectMapper json) {
        this.approvals = approvals;
        this.employees = employees;
        this.departments = departments;
        this.sections = sections;
        this.designations = designations;
        this.json = json;
    }

    public static String requestTypeFor(TourType type) {
        return type == TourType.INTERNATIONAL ? "FACTORY_TOUR_INTL" : "FACTORY_TOUR_LOCAL";
    }

    public FactoryTourDto dto(FactoryTour t) {
        String requestType = requestTypeFor(t.getTravelType());
        List<ApprovalStep> chain = approvals.findChain(requestType, t.getId());
        return new FactoryTourDto(
                t.getId(), t.getRequestId(), "Factory Tour", t.getRequestDate(), t.getStatus(),
                t.getCardNo(), t.getName(), t.getDepartmentId(), t.getSectionId(),
                t.getDesignationId(), t.getGradeId(),
                t.getTravelType(), t.getTourStart(), t.getTourEnd(),
                t.getOrigin(), t.getDestination(), t.getCity(), t.getCountry(), t.getCurrency(),
                t.getVehicleSource(), t.getTransportationType(), t.getVehicleNo(),
                t.getPurpose(), t.getTotalDistanceKm(), readTravelPersons(t.getAdditionalTravelPersons()),
                t.getApprovedByUserId(), t.getApprovedAt(), t.getRejectReason(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getCreatedBy(),
                currentStage(chain), stepsDto(chain),
                actionsForCaller(requestType, chain, t));
    }

    public String writeTravelPersons(List<TravelPersonDto> persons) {
        if (persons == null || persons.isEmpty()) return null;
        try {
            return json.writeValueAsString(persons);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize additionalTravelPersons", e);
        }
    }

    private List<TravelPersonDto> readTravelPersons(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return json.readValue(raw, new TypeReference<List<TravelPersonDto>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse additionalTravelPersons", e);
        }
    }

    /** Batch-resolves requester summaries for an inbox page — one catalog snapshot for the whole page. */
    public Map<Long, EmployeeSummary> employeeSummariesByUserId(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        List<Employee> rows = employees.findAllByUserIdIn(userIds);
        if (rows.isEmpty()) return Map.of();

        Map<Long, DepartmentDto> deptById = indexBy(departments.list(null), DepartmentDto::id);
        Map<Long, SectionDto> sectionById = indexBy(sections.list(null, null), SectionDto::id);
        Map<Long, DesignationDto> designationById = indexBy(designations.list(null), DesignationDto::id);

        Map<Long, EmployeeSummary> out = new HashMap<>(rows.size() * 2);
        for (Employee e : rows) {
            DepartmentDto dept = e.getDepartmentId() == null ? null : deptById.get(e.getDepartmentId());
            SectionDto section = e.getSectionId() == null ? null : sectionById.get(e.getSectionId());
            DesignationDto designation = e.getDesignationId() == null ? null : designationById.get(e.getDesignationId());
            out.put(e.getUserId(), new EmployeeSummary(
                    e.getId(), e.getFullName(), e.getEmployeeCode(),
                    e.getDepartmentId(), dept == null ? null : dept.name(),
                    e.getSectionId(), section == null ? null : section.name(),
                    e.getDesignationId(), designation == null ? null : designation.title(),
                    null));
        }
        return out;
    }

    private CurrentStageDto currentStage(List<ApprovalStep> chain) {
        return chain.stream()
                .filter(s -> s.getStatus() == StepStatus.PENDING)
                .findFirst()
                .map(s -> new CurrentStageDto(s.getStageNo(), s.getLabel(), s.getAssignedTo(), null))
                .orElse(null);
    }

    private List<ApprovalStepDto> stepsDto(List<ApprovalStep> chain) {
        List<ApprovalStepDto> out = new ArrayList<>(chain.size());
        for (ApprovalStep s : chain) {
            out.add(new ApprovalStepDto(
                    s.getStageNo(), s.getLabel(), s.getAssignedTo(), null,
                    s.getStatus().name(), s.getDecidedBy(), null, s.getDecidedAt(), s.getNote()));
        }
        return out;
    }

    private List<String> actionsForCaller(String requestType, List<ApprovalStep> chain, FactoryTour t) {
        return CurrentUserHolder.get()
                .map(user -> actionsFor(requestType, chain, user, t))
                .orElse(List.of());
    }

    private List<String> actionsFor(String requestType, List<ApprovalStep> chain, CurrentUser user, FactoryTour t) {
        List<String> out = new ArrayList<>(3);
        ApprovalStep current = chain.stream()
                .filter(s -> s.getStatus() == StepStatus.PENDING).findFirst().orElse(null);
        if (current != null && approvals.canAct(requestType, current, user)) {
            out.add("APPROVE");
            out.add("REJECT");
        }
        if (cancellable(t) && (user.hasPermission("employee.admin") || Objects.equals(user.userId(), t.getRequestedByUserId()))) {
            out.add("CANCEL");
        }
        return out;
    }

    private static boolean cancellable(FactoryTour t) {
        return t.getStatus() == TourStatus.DRAFT || t.getStatus() == TourStatus.PENDING_APPROVAL;
    }

    private static <T> Map<Long, T> indexBy(List<T> rows, java.util.function.ToLongFunction<T> idOf) {
        if (rows == null || rows.isEmpty()) return Map.of();
        Map<Long, T> out = new HashMap<>(rows.size() * 2);
        for (T r : rows) out.put(idOf.applyAsLong(r), r);
        return out;
    }
}

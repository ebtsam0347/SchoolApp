package com.forward.hrms.employee.service;

import com.forward.hrms.commons.approval.service.ApprovalInboxService;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.request.CreateTourRequest;
import com.forward.hrms.employee.dto.request.RejectTourRequest;
import com.forward.hrms.employee.dto.request.TravelPersonRequest;
import com.forward.hrms.employee.dto.request.UpdateTourRequest;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.employee.dto.response.FactoryTourDto;
import com.forward.hrms.employee.dto.response.FactoryToursInbox;
import com.forward.hrms.employee.dto.response.TravelPersonDto;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.FactoryTour;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.TourStatus;
import com.forward.hrms.employee.enums.TourType;
import com.forward.hrms.employee.enums.VehicleSource;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.FactoryTourRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FactoryTourServiceOld {

    private final FactoryTourRepository repo;
    private final EmployeeRepository employees;
    private final ApprovalService approvals;
    private final ApprovalInboxService approvalInbox;
    private final FactoryTourDtoAssembler assembler;
    private final FactoryTourCodeService codes;
    private final AuditEmitter audit;

    @Transactional
    public FactoryTourDto create(CreateTourRequest req) {
        CurrentUser caller = requireCaller();
        Employee requester = requireRequesterEmployee(caller);

        FactoryTour t = new FactoryTour();
        t.setRequestId(codes.allocate());
        t.setRequestDate(LocalDate.now());
        t.setStatus(TourStatus.DRAFT);
        t.setCardNo(requester.getEmployeeCode());
        t.setName(requester.getFullName());
        t.setDepartmentId(requester.getDepartmentId());
        t.setSectionId(requester.getSectionId());
        t.setDesignationId(requester.getDesignationId());
        t.setGradeId(requester.getGradeId());
        t.setRequestedByUserId(caller.userId());
        applyCreate(t, req);
        repo.save(t);

        audit.emit("factory_tour", String.valueOf(t.getId()), "created",
                null, snapshot(t), true);
        return assembler.dto(t);
    }

    @Transactional
    public FactoryTourDto update(long id, UpdateTourRequest req) {
        FactoryTour t = requireDraft(id);
        Map<String, Object> before = snapshot(t);

        if (req.travelType() != null) t.setTravelType(req.travelType());
        if (req.tourStart() != null) t.setTourStart(req.tourStart());
        if (req.tourEnd() != null) t.setTourEnd(req.tourEnd());
        if (req.origin() != null && !req.origin().isBlank()) t.setOrigin(req.origin());
        if (req.destination() != null && !req.destination().isBlank()) t.setDestination(req.destination());
        if (req.city() != null) t.setCity(req.city());
        if (req.country() != null) t.setCountry(req.country());
        if (req.currency() != null) t.setCurrency(req.currency());
        if (req.vehicleSource() != null) t.setVehicleSource(req.vehicleSource());
        if (req.transportationType() != null) t.setTransportationType(req.transportationType());
        if (req.vehicleNo() != null) t.setVehicleNo(req.vehicleNo());
        if (req.purpose() != null) t.setPurpose(req.purpose());
        if (req.totalDistanceKm() != null) t.setTotalDistanceKm(req.totalDistanceKm());
        if (req.additionalTravelPersons() != null) {
            t.setAdditionalTravelPersons(assembler.writeTravelPersons(toDto(req.additionalTravelPersons())));
        }

        validateTravelFields(t.getTravelType(), t.getTourStart(), t.getTourEnd(),
                t.getCity(), t.getVehicleSource(), t.getCountry(), t.getCurrency());

        audit.emit("factory_tour", String.valueOf(t.getId()), "updated",
                before, snapshot(t), false);
        return assembler.dto(t);
    }

    @Transactional
    public FactoryTourDto submit(long id) {
        FactoryTour t = requireDraft(id);
        CurrentUser caller = requireCaller();

        t.setStatus(TourStatus.PENDING_APPROVAL);

        long employeeId = employees.findByUserId(caller.userId())
                .map(Employee::getId)
                .orElse(0L);
        approvals.openChain(FactoryTourDtoAssembler.requestTypeFor(t.getTravelType()), id, employeeId);

        audit.emit("factory_tour", String.valueOf(t.getId()), "submitted",
                null, Map.of("status", "PENDING_APPROVAL"), true);
        return assembler.dto(t);
    }

    @Transactional
    public FactoryTourDto approve(long id) {
        FactoryTour t = requireStatus(id, TourStatus.PENDING_APPROVAL);
        approvals.approve(FactoryTourDtoAssembler.requestTypeFor(t.getTravelType()), id, null);
        return assembler.dto(find(id));
    }

    @Transactional
    public FactoryTourDto reject(long id, RejectTourRequest req) {
        FactoryTour t = requireStatus(id, TourStatus.PENDING_APPROVAL);
        approvals.reject(FactoryTourDtoAssembler.requestTypeFor(t.getTravelType()), id, req.reason());
        return assembler.dto(find(id));
    }

    @Transactional
    public FactoryTourDto cancel(long id) {
        FactoryTour t = find(id);
        if (t.getStatus() != TourStatus.DRAFT && t.getStatus() != TourStatus.PENDING_APPROVAL) {
            throw new ConflictError(EmployeeErrorCode.TOUR_INVALID_STATE,
                    "only DRAFT or PENDING_APPROVAL tours can be cancelled");
        }
        CurrentUser caller = requireCaller();
        if (!caller.hasPermission("employee.admin")
                && !Objects.equals(caller.userId(), t.getRequestedByUserId())) {
            throw new ForbiddenError(EmployeeErrorCode.TOUR_INVALID_STATE,
                    "only the requester or an admin can cancel this tour");
        }
        if (t.getStatus() == TourStatus.PENDING_APPROVAL) {
            approvals.cancelChain(FactoryTourDtoAssembler.requestTypeFor(t.getTravelType()), id);
        }
        t.setStatus(TourStatus.CANCELLED);
        audit.emit("factory_tour", String.valueOf(t.getId()), "cancelled",
                null, Map.of("status", "CANCELLED"), true);
        return assembler.dto(t);
    }

    @Transactional(readOnly = true)
    public FactoryTourDto get(long id) {
        return assembler.dto(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<FactoryTourDto> list(TourStatus status, TourType travelType, String cardNo,
                                             Long departmentId, Pageable pageable) {
        return PageResponse.from(repo.search(status, travelType, cardNo, departmentId, pageable).map(assembler::dto));
    }

    /**
     * Pending-approval inbox for the caller across both tour request types —
     * mirrors attendance's {@code /approvals/inbox} shape (request + employee,
     * plus a total count) so the FE gets a consistent contract.
     */
    @Transactional(readOnly = true)
    public FactoryToursInbox pendingApprovals() {
        CurrentUser caller = requireCaller();
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(approvalInbox.requestIdsInInbox("FACTORY_TOUR_LOCAL", caller));
        ids.addAll(approvalInbox.requestIdsInInbox("FACTORY_TOUR_INTL", caller));
        if (ids.isEmpty()) return new FactoryToursInbox(List.of(), new FactoryToursInbox.Counts(0, 0));

        List<FactoryTour> tours = repo.findAllById(ids);
        tours.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        Set<Long> requesterUserIds = new LinkedHashSet<>();
        for (FactoryTour t : tours) {
            if (t.getRequestedByUserId() != null) requesterUserIds.add(t.getRequestedByUserId());
        }
        Map<Long, EmployeeSummary> summaries = assembler.employeeSummariesByUserId(requesterUserIds);

        List<FactoryToursInbox.Row> rows = new ArrayList<>(tours.size());
        for (FactoryTour t : tours) {
            EmployeeSummary employee = t.getRequestedByUserId() == null
                    ? null : summaries.get(t.getRequestedByUserId());
            rows.add(new FactoryToursInbox.Row(assembler.dto(t), employee));
        }
        return new FactoryToursInbox(rows, new FactoryToursInbox.Counts(rows.size(), rows.size()));
    }

    private void applyCreate(FactoryTour t, CreateTourRequest req) {
        t.setTravelType(req.travelType());
        t.setTourStart(req.tourStart());
        t.setTourEnd(req.tourEnd());
        t.setOrigin(req.origin());
        t.setDestination(req.destination());
        t.setCity(req.city());
        t.setCountry(req.country());
        t.setCurrency(req.currency());
        t.setVehicleSource(req.vehicleSource());
        t.setTransportationType(req.transportationType());
        t.setVehicleNo(req.vehicleNo());
        t.setPurpose(req.purpose());
        t.setTotalDistanceKm(req.totalDistanceKm());
        t.setAdditionalTravelPersons(assembler.writeTravelPersons(toDto(req.additionalTravelPersons())));

        validateTravelFields(t.getTravelType(), t.getTourStart(), t.getTourEnd(),
                t.getCity(), t.getVehicleSource(), t.getCountry(), t.getCurrency());
    }

    /**
     * {@link CreateTourRequest}'s {@code @AssertTrue} methods already cover this
     * on create; update() applies partial patches field-by-field, so the
     * combined post-patch state needs the same check re-run here.
     */
    private void validateTravelFields(TourType travelType, LocalDateTime tourStart,
                                      LocalDateTime tourEnd, String city,
                                      VehicleSource vehicleSource,
                                      String country, String currency) {
        if (tourStart != null && tourEnd != null && !tourEnd.isAfter(tourStart)) {
            throw new UnprocessableError(EmployeeErrorCode.TOUR_VALIDATION_FAILED, "tourEnd must be after tourStart");
        }
        if (travelType == TourType.LOCAL && (city == null || city.isBlank() || vehicleSource == null)) {
            throw new UnprocessableError(EmployeeErrorCode.TOUR_VALIDATION_FAILED,
                    "city and vehicleSource are required when travelType is LOCAL");
        }
        if (travelType == TourType.INTERNATIONAL
                && (country == null || country.isBlank() || currency == null || currency.isBlank())) {
            throw new UnprocessableError(EmployeeErrorCode.TOUR_VALIDATION_FAILED,
                    "country and currency are required when travelType is INTERNATIONAL");
        }
    }

    private static List<TravelPersonDto> toDto(List<TravelPersonRequest> persons) {
        if (persons == null) return null;
        return persons.stream().map(p -> new TravelPersonDto(p.cardNo(), p.name())).collect(Collectors.toList());
    }

    private Employee requireRequesterEmployee(CurrentUser caller) {
        return employees.findByUserId(caller.userId())
                .orElseThrow(() -> new UnprocessableError(EmployeeErrorCode.TOUR_VALIDATION_FAILED,
                        "caller has no linked employee record"));
    }

    private FactoryTour find(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.TOUR_NOT_FOUND, "factory tour not found"));
    }

    private FactoryTour requireDraft(long id) {
        FactoryTour t = find(id);
        if (t.getStatus() != TourStatus.DRAFT) {
            throw new ConflictError(EmployeeErrorCode.TOUR_INVALID_STATE,
                    "tour is not in DRAFT status");
        }
        return t;
    }

    private FactoryTour requireStatus(long id, TourStatus expected) {
        FactoryTour t = find(id);
        if (t.getStatus() != expected) {
            throw new ConflictError(EmployeeErrorCode.TOUR_INVALID_STATE,
                    "tour status is " + t.getStatus() + ", expected " + expected);
        }
        return t;
    }

    private static CurrentUser requireCaller() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("Authentication required"));
    }

    private static Map<String, Object> snapshot(FactoryTour t) {
        return Map.of(
                "travelType", t.getTravelType(),
                "status", t.getStatus(),
                "tourStart", String.valueOf(t.getTourStart()),
                "tourEnd", String.valueOf(t.getTourEnd()),
                "origin", t.getOrigin(),
                "destination", t.getDestination()
        );
    }
}

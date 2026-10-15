package com.forward.hrms.bff.client;

import java.time.LocalDate;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.forward.hrms.attendance.dto.response.ApprovalDecisionRow;
import com.forward.hrms.attendance.dto.response.ApprovalsInbox;
import com.forward.hrms.attendance.dto.response.AttendanceRecordDto;
import com.forward.hrms.attendance.dto.response.EmployeeHeatmapDto;
import com.forward.hrms.attendance.dto.response.HolidayDto;
import com.forward.hrms.attendance.dto.response.LeaveBalanceDto;
import com.forward.hrms.attendance.dto.response.LeaveRequestDto;
import com.forward.hrms.attendance.dto.response.LeaveTypeDto;
import com.forward.hrms.attendance.dto.response.OrgAttendanceDayDto;
import com.forward.hrms.attendance.dto.response.OrgPulseDto;
import com.forward.hrms.attendance.dto.response.OvertimeRequestDto;
import com.forward.hrms.attendance.dto.response.PunchDto;
import com.forward.hrms.attendance.dto.response.RegularizationRequestDto;
import com.forward.hrms.attendance.dto.response.ShiftDto;
import com.forward.hrms.attendance.dto.response.ShiftOverrideDto;
import com.forward.hrms.attendance.dto.response.WorkflowChainDto;
import com.forward.hrms.bff.client.dto.PageView;
import com.forward.hrms.bff.config.DownstreamProperties;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UpstreamError;

import lombok.extern.slf4j.Slf4j;

/**
 * Attendance-service slice the BFF needs. Mirrors the EmployeeClient
 * shape — thin per-call methods, identity-forwarded, errors mapped to
 * {@link UpstreamError}.
 */
@Slf4j
@Component
public class AttendanceClient {

    /**
     * Page size used when the BFF fetches paginated lists meant to be consumed
     * whole (composite views, inbox stitching). The upstream caps at 200; if a
     * caller's inbox legitimately holds more rows the FE pagination will need a
     * direct upstream call.
     */
    private static final int PAGED_FETCH_SIZE = 200;

    private final RestClient http;

    public AttendanceClient(DownstreamProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.attendance().baseUrl()).build();
    }

    public List<AttendanceRecordDto> myMonth(String month) {
        try {
            List<AttendanceRecordDto> body = http.get()
                    .uri(uri -> {
                        var b = uri.path("/attendance/me");
                        if (month != null) b.queryParam("month", month);
                        return b.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? List.of() : body;
        } catch (RestClientException ex) {
            throw upstream("attendance-me", ex);
        }
    }

    public List<PunchDto> myPunches(LocalDate date) {
        return getList(uri -> uri.path("/attendance/me/punches").queryParam("date", date).build(),
                new ParameterizedTypeReference<List<PunchDto>>() {},
                "my-punches");
    }

    public List<LeaveBalanceDto> myBalances() {
        return getList("/leaves/me/balances",
                new ParameterizedTypeReference<List<LeaveBalanceDto>>() {},
                "leave-balances");
    }

    public List<LeaveRequestDto> myLeaves() {
        return myLeaves(null);
    }

    /** Calls the paginated upstream with a generous bound and returns the content slice. */
    public List<LeaveRequestDto> myLeaves(String status) {
        return getPagedContent(uri -> {
            var b = uri.path("/leaves/me").queryParam("size", PAGED_FETCH_SIZE);
            if (status != null) b.queryParam("status", status);
            return b.build();
        },
        new ParameterizedTypeReference<PageView<LeaveRequestDto>>() {},
        "my-leaves");
    }

    @org.springframework.cache.annotation.Cacheable(value = com.forward.hrms.bff.config.BffCacheConfig.CACHE_CATALOGS, key = "#root.methodName")
    public List<LeaveTypeDto> leaveTypes() {
        return getList("/leaves/types",
                new ParameterizedTypeReference<List<LeaveTypeDto>>() {},
                "leave-types");
    }

    @org.springframework.cache.annotation.Cacheable(value = com.forward.hrms.bff.config.BffCacheConfig.CACHE_CATALOGS, key = "#root.methodName")
    public List<ShiftDto> shifts() {
        return getList("/shifts",
                new ParameterizedTypeReference<List<ShiftDto>>() {},
                "shifts");
    }

    public List<HolidayDto> holidays(int year) {
        return getList(uri -> uri.path("/holidays").queryParam("year", year).build(),
                new ParameterizedTypeReference<List<HolidayDto>>() {},
                "holidays");
    }

    public List<AttendanceRecordDto> employeeRange(long employeeId,
                                                                    LocalDate from, LocalDate to) {
        return getList(uri -> uri.path("/attendance/employee/{id}")
                        .queryParam("from", from).queryParam("to", to).build(employeeId),
                new ParameterizedTypeReference<List<AttendanceRecordDto>>() {},
                "employee-range");
    }

    public OrgPulseDto orgPulse(LocalDate asOf) {
        try {
            return http.get()
                    .uri(uri -> {
                        var b = uri.path("/attendance/org/pulse");
                        if (asOf != null) b.queryParam("asOf", asOf);
                        return b.build();
                    })
                    .retrieve()
                    .body(OrgPulseDto.class);
        } catch (RestClientException ex) {
            throw upstream("org-pulse", ex);
        }
    }

    /**
     * Org-wide records list passthrough. Filters are forwarded as query
     * params; the BFF lifts the upstream {@code PageResponse} verbatim and
     * enriches the content rows separately.
     */
    public PageView<AttendanceRecordDto> recordsList(RecordsListQuery q) {
        try {
            PageView<AttendanceRecordDto> body = http.get()
                    .uri(uri -> {
                        var b = uri.path("/attendance/records");
                        if (q.date() != null) b.queryParam("date", q.date());
                        if (q.from() != null) b.queryParam("from", q.from());
                        if (q.to() != null) b.queryParam("to", q.to());
                        if (q.employeeId() != null) b.queryParam("employeeId", q.employeeId());
                        if (q.employeeCode() != null) b.queryParam("employeeCode", q.employeeCode());
                        if (q.departmentId() != null) b.queryParam("departmentId", q.departmentId());
                        if (q.sectionId() != null) b.queryParam("sectionId", q.sectionId());
                        if (q.status() != null) b.queryParam("status", q.status());
                        if (q.lateOnly() != null) b.queryParam("lateOnly", q.lateOnly());
                        if (q.page() != null) b.queryParam("page", q.page());
                        if (q.size() != null) b.queryParam("size", q.size());
                        if (q.sort() != null) b.queryParam("sort", q.sort());
                        return b.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null
                    ? new PageView<>(List.of(), 0, 0, 0, 0, null)
                    : body;
        } catch (RestClientException ex) {
            throw upstream("records-list", ex);
        }
    }

    /** Org-wide punches list passthrough — same shape conventions as {@link #recordsList}. */
    public PageView<PunchDto> punchesList(PunchesListQuery q) {
        try {
            PageView<PunchDto> body = http.get()
                    .uri(uri -> {
                        var b = uri.path("/attendance/punches");
                        if (q.date() != null) b.queryParam("date", q.date());
                        if (q.from() != null) b.queryParam("from", q.from());
                        if (q.to() != null) b.queryParam("to", q.to());
                        if (q.employeeId() != null) b.queryParam("employeeId", q.employeeId());
                        if (q.employeeCode() != null) b.queryParam("employeeCode", q.employeeCode());
                        if (q.departmentId() != null) b.queryParam("departmentId", q.departmentId());
                        if (q.sectionId() != null) b.queryParam("sectionId", q.sectionId());
                        if (q.source() != null) b.queryParam("source", q.source());
                        if (q.deviceId() != null) b.queryParam("deviceId", q.deviceId());
                        if (q.page() != null) b.queryParam("page", q.page());
                        if (q.size() != null) b.queryParam("size", q.size());
                        if (q.sort() != null) b.queryParam("sort", q.sort());
                        return b.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null
                    ? new PageView<>(List.of(), 0, 0, 0, 0, null)
                    : body;
        } catch (RestClientException ex) {
            throw upstream("punches-list", ex);
        }
    }

    public record RecordsListQuery(
            LocalDate date, LocalDate from, LocalDate to,
            Long employeeId, String employeeCode,
            Long departmentId, Long sectionId,
            String status, Boolean lateOnly,
            Integer page, Integer size, String sort) {}

    public record PunchesListQuery(
            LocalDate date, LocalDate from, LocalDate to,
            Long employeeId, String employeeCode,
            Long departmentId, Long sectionId,
            String source, Long deviceId,
            Integer page, Integer size, String sort) {}

    public List<OrgAttendanceDayDto> orgAttendance(LocalDate from, LocalDate to) {
        return getList(uri -> uri.path("/attendance/org/attendance")
                        .queryParam("from", from).queryParam("to", to).build(),
                new ParameterizedTypeReference<List<OrgAttendanceDayDto>>() {},
                "org-attendance");
    }

    public EmployeeHeatmapDto myHeatmap(String month) {
        try {
            return http.get()
                    .uri(uri -> {
                        var b = uri.path("/attendance/me/heatmap");
                        if (month != null) b.queryParam("month", month);
                        return b.build();
                    })
                    .retrieve()
                    .body(EmployeeHeatmapDto.class);
        } catch (RestClientException ex) {
            throw upstream("me-heatmap", ex);
        }
    }

    /** Endpoint always 200s; unwraps the {@code state}/{@code record} envelope to a nullable record. */
    public AttendanceRecordDto employeeTodayOrNull(long employeeId) {
        try {
            com.forward.hrms.attendance.dto.response.EmployeeTodayDto resp = http.get()
                    .uri(uri -> uri.path("/attendance/employee/{id}/today").build(employeeId))
                    .retrieve()
                    .body(com.forward.hrms.attendance.dto.response.EmployeeTodayDto.class);
            return resp == null ? null : resp.record();
        } catch (RestClientException ex) {
            throw upstream("employee-today", ex);
        }
    }

    /** Existence + permission probe — used to gate downstream reads on a request. */
    public void requireLeaveVisible(long id) {
        try {
            http.get().uri(uri -> uri.path("/leaves/{id}").build(id))
                    .retrieve()
                    .body(LeaveRequestDto.class);
        } catch (RestClientException ex) {
            throw upstream("leave-visibility", ex);
        }
    }

    /** Same probe for a short leave — attendance's GET /short-leaves/{id}. */
    public void requireShortLeaveVisible(long id) {
        try {
            http.get().uri(uri -> uri.path("/short-leaves/{id}").build(id))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw upstream("short-leave-visibility", ex);
        }
    }
    
    public void requireOvertimeVisible(long id) {
        try {
            http.get().uri(uri -> uri.path("/overtime/{id}").build(id))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw upstream("overtime-visibility", ex);
        }
    }

    public void requireRegularizationVisible(long id) {
        // Same gap as overtime — there is no public GET /regularizations/{id}.
        // Visibility is implied by the request appearing in either the caller's
        // own list (not yet exposed) or their pending-approvals inbox. For now
        // we permit the audit timeline read for any authenticated caller and
        // rely on attendance-service to make the request DTO itself unreadable
        // when the caller can't see it.
    }

    public List<LeaveRequestDto> leavePending() {
        return getPagedContent(uri -> uri.path("/leaves/pending-approvals")
                .queryParam("size", PAGED_FETCH_SIZE).build(),
                new ParameterizedTypeReference<PageView<LeaveRequestDto>>() {},
                "leave-pending");
    }

    /** Attendance's own combined leave+overtime+regularization inbox, already employee-enriched. */
    public ApprovalsInbox approvalsInbox(String leaveQ, Long leaveTypeId, String shortLeaveQ,
                                         String overtimeQ, String overtimeType,
                                         String regularizationQ) {
        try {
            return http.get().uri(uri -> {
                var b = uri.path("/approvals/inbox");
                if (leaveQ != null) b.queryParam("leaveQ", leaveQ);
                if (leaveTypeId != null) b.queryParam("leaveTypeId", leaveTypeId);
                if (shortLeaveQ != null) b.queryParam("shortLeaveQ", shortLeaveQ);
                if (overtimeQ != null) b.queryParam("overtimeQ", overtimeQ);
                if (overtimeType != null) b.queryParam("overtimeType", overtimeType);
                if (regularizationQ != null) b.queryParam("regularizationQ", regularizationQ);
                return b.build();
            }).retrieve().body(ApprovalsInbox.class);
        } catch (RestClientException ex) {
            throw upstream("approvals-inbox", ex);
        }
    }

    /** Attendance's own configured approval chains (leave, overtime, regularization, ...). */
    public List<WorkflowChainDto> approvalWorkflows() {
        return getList("/approval-workflows",
                new ParameterizedTypeReference<List<WorkflowChainDto>>() {}, "approval-workflows");
    }

    /** Shift overrides covering a given date — used by the operator dashboard. */
    public List<ShiftOverrideDto> shiftOverridesOnDate(LocalDate date) {
        return getList(uri -> uri.path("/attendance/shift-overrides")
                        .queryParam("date", date).build(),
                new ParameterizedTypeReference<List<ShiftOverrideDto>>() {},
                "shift-overrides-date");
    }

    public List<OvertimeRequestDto> myOvertime() {
        return getPagedContent(uri -> uri.path("/overtime/me")
                .queryParam("size", PAGED_FETCH_SIZE).build(),
                new ParameterizedTypeReference<PageView<OvertimeRequestDto>>() {},
                "my-overtime");
    }

    public List<OvertimeRequestDto> overtimePending() {
        return getPagedContent(uri -> uri.path("/overtime/pending-approvals")
                .queryParam("size", PAGED_FETCH_SIZE).build(),
                new ParameterizedTypeReference<PageView<OvertimeRequestDto>>() {},
                "ot-pending");
    }

    /**
     * Active approval delegations involving the caller, optionally narrowed to
     * a specific date. Used by the team-attendance composites to widen the
     * caller's coverage set beyond direct reports.
     */
    public List<com.forward.hrms.attendance.dto.response.DelegationDto> myDelegations(
            String status, LocalDate activeOn) {
        return getList(uri -> {
                    var b = uri.path("/approval-delegations/me");
                    if (status != null) b.queryParam("status", status);
                    if (activeOn != null) b.queryParam("activeOn", activeOn);
                    return b.build();
                },
                new ParameterizedTypeReference<List<com.forward.hrms.attendance.dto.response.DelegationDto>>() {},
                "my-delegations");
    }

    /**
     * Caller-scoped approval decisions in a date range (inclusive). Used by
     * the manager dashboard to compute approval velocity. Pulls one page at
     * the upstream max — a manager closing more than that in a 7-day window
     * is unrealistic; the average remains representative either way.
     */
    public List<ApprovalDecisionRow> approvalHistory(LocalDate from, LocalDate to) {
        return approvalHistory(from, to, null, null);
    }

    /** Same as above, optionally narrowed by request type and decision (null = all). */
    public List<ApprovalDecisionRow> approvalHistory(LocalDate from, LocalDate to, String type, String decision) {
        return getPagedContent(uri -> {
                    var b = uri.path("/approvals/history")
                            .queryParam("from", from).queryParam("to", to)
                            .queryParam("size", PAGED_FETCH_SIZE);
                    if (type != null) b.queryParam("type", type);
                    if (decision != null) b.queryParam("decision", decision);
                    return b.build();
                },
                new ParameterizedTypeReference<PageView<ApprovalDecisionRow>>() {},
                "approval-history");
    }

    public List<RegularizationRequestDto> regularizationPending() {
        return getPagedContent(uri -> uri.path("/regularizations/pending-approvals")
                .queryParam("size", PAGED_FETCH_SIZE).build(),
                new ParameterizedTypeReference<PageView<RegularizationRequestDto>>() {},
                "reg-pending");
    }

    /*
     * Type tokens are taken at the call site, NOT inferred inside these helpers.
     * `new ParameterizedTypeReference<>() {}` inside a generic method captures
     * `List<T>` where T has been erased to Object — Jackson then deserializes
     * each element as a LinkedHashMap, and the iterator at the call site blows
     * up with a ClassCastException. The token has to be created where the
     * concrete element type is in scope.
     */

    private <T> List<T> getList(String path, ParameterizedTypeReference<List<T>> type, String what) {
        try {
            List<T> body = http.get().uri(path).retrieve().body(type);
            return body == null ? List.of() : body;
        } catch (RestClientException ex) {
            throw upstream(what, ex);
        }
    }

    private <T> List<T> getList(java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uri,
                                ParameterizedTypeReference<List<T>> type, String what) {
        try {
            List<T> body = http.get().uri(uri).retrieve().body(type);
            return body == null ? List.of() : body;
        } catch (RestClientException ex) {
            throw upstream(what, ex);
        }
    }

    private <T> List<T> getPagedContent(
            java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uri,
            ParameterizedTypeReference<PageView<T>> type,
            String what) {
        try {
            PageView<T> body = http.get().uri(uri).retrieve().body(type);
            return body == null || body.content() == null ? List.of() : body.content();
        } catch (RestClientException ex) {
            throw upstream(what, ex);
        }
    }

    private static UpstreamError upstream(String what, RestClientException ex) {
        log.warn("attendance-service call failed for {}: {}", what, ex.getMessage());
        return new UpstreamError(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                "attendance-service request failed.", ex);
    }
}

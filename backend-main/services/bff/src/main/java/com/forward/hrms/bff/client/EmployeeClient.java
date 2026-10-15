package com.forward.hrms.bff.client;

import com.forward.hrms.employee.dto.response.AggregateDto;
import com.forward.hrms.employee.dto.response.DisciplinaryActionDto;
import com.forward.hrms.employee.dto.response.EmployeeHistoryDto;
import com.forward.hrms.employee.dto.response.FactoryToursInbox;
import com.forward.hrms.employee.dto.response.RequisitionDto;
import com.forward.hrms.employee.dto.response.EmployeeIdentitySummary;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.EmploymentContractDto;
import com.forward.hrms.employee.dto.response.ExitDto;
import com.forward.hrms.bff.client.dto.PageView;
import com.forward.hrms.employee.dto.response.SuspensionDto;
import com.forward.hrms.employee.dto.response.TravelRequestResponse;
import com.forward.hrms.employee.dto.response.DisciplinaryActionListResponse;
import com.forward.hrms.bff.config.DownstreamProperties;
import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.employee.dto.response.SectionDto;
import com.forward.hrms.employee.dto.response.DesignationDto;
import com.forward.hrms.employee.dto.response.GradeDto;
import com.forward.hrms.employee.dto.response.ProvinceDto;
import com.forward.hrms.employee.dto.response.CityDto;
import com.forward.hrms.employee.dto.response.QualificationDto;
import com.forward.hrms.employee.dto.response.SkillDto;
import com.forward.hrms.attendance.dto.response.ApprovalDecisionRow;
import com.forward.hrms.attendance.dto.response.WorkflowChainDto;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UpstreamError;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Employee-service slice the BFF needs. One thin method per downstream
 * call; caller-side composition (parallel fan-out, partial degradation)
 * lives in the service layer.
 */
@Slf4j
@Component
public class EmployeeClient {

    private final RestClient http;

    public EmployeeClient(DownstreamProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.employee().baseUrl()).build();
    }

    // ----- core employee reads -----

    public EmployeeDto getEmployee(long id) {
        return get(uri -> uri.path("/employees/{id}").build(id),
                new ParameterizedTypeReference<>() {}, "get-employee");
    }

    /** {@code 404} swallowed → null, so the BFF doesn't bubble it as a 502. */
    public EmployeeDto getEmployeeOrNull(long id) {
        try {
            return http.get()
                    .uri(uri -> uri.path("/employees/{id}").build(id))
                    .retrieve()
                    .body(EmployeeDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        } catch (RestClientException ex) {
            throw upstream("get-employee", ex);
        }
    }

    public EmployeeDto me() {
        return get("/employees/me", new ParameterizedTypeReference<>() {}, "me");
    }

    /**
     * Batch lookup via {@code /employees/by-ids} (employee-service caps
     * at 200). Replaces N parallel {@code getEmployeeOrNull} fan-outs in
     * BFF (manager-lookup, history, etc.) with one round-trip per chunk
     * of 200. Missing ids are silently dropped — same semantic as the
     * single-row variant.
     */
    public Map<Long, EmployeeDto> getEmployeesByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        java.util.List<Long> distinct = ids.stream().distinct().toList();
        java.util.Map<Long, EmployeeDto> out = new java.util.LinkedHashMap<>();
        for (int i = 0; i < distinct.size(); i += 200) {
            java.util.List<Long> chunk = distinct.subList(i, Math.min(i + 200, distinct.size()));
            try {
                java.util.List<EmployeeDto> rows = http.get()
                        .uri(uri -> uri.path("/employees/by-ids")
                                .queryParam("ids", chunk.stream().map(String::valueOf)
                                        .collect(java.util.stream.Collectors.joining(",")))
                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<java.util.List<EmployeeDto>>() {});
                if (rows == null) continue;
                for (EmployeeDto v : rows) out.put(v.id(), v);
            } catch (RestClientException ex) {
                throw upstream("by-ids", ex);
            }
        }
        return out;
    }

    /**
     * Service-neutral picker hit — auth-only, no {@code employee.read}
     * required upstream. Backs the command palette's employee provider.
     */
    public com.forward.hrms.employee.dto.response.EmployeePickerResponse picker(String q, int limit) {
        try {
            com.forward.hrms.employee.dto.response.EmployeePickerResponse body = http.get()
                    .uri(uri -> {
                        var b = uri.path("/employees/picker");
                        if (q != null && !q.isBlank()) b.queryParam("q", q);
                        b.queryParam("limit", limit);
                        return b.build();
                    })
                    .retrieve()
                    .body(com.forward.hrms.employee.dto.response.EmployeePickerResponse.class);
            return body == null
                    ? new com.forward.hrms.employee.dto.response.EmployeePickerResponse(java.util.List.of())
                    : body;
        } catch (RestClientException ex) {
            throw upstream("picker", ex);
        }
    }

    public PageView<EmployeeDto> listEmployees(SearchParams p) {
        try {
            PageView<EmployeeDto> body = http.get()
                    .uri(uri -> {
                        var b = uri.path("/employees");
                        if (p.status() != null) b.queryParam("status", p.status());
                        if (p.employmentType() != null) b.queryParam("employmentType", p.employmentType());
                        if (p.departmentId() != null) b.queryParam("departmentId", p.departmentId());
                        if (p.sectionId() != null) b.queryParam("sectionId", p.sectionId());
                        if (p.managerId() != null) b.queryParam("managerId", p.managerId());
                        if (p.gradeId() != null) b.queryParam("gradeId", p.gradeId());
                        if (p.designationId() != null) b.queryParam("designationId", p.designationId());
                        if (p.hasEss() != null) b.queryParam("hasEss", p.hasEss());
                        if (p.q() != null && !p.q().isBlank()) b.queryParam("q", p.q());
                        if (p.probationDueWithin() != null) b.queryParam("probationDueWithin", p.probationDueWithin());
                        if (p.probationOverdue() != null) b.queryParam("probationOverdue", p.probationOverdue());
                        if (p.page() != null) b.queryParam("page", p.page());
                        if (p.size() != null) b.queryParam("size", p.size());
                        if (p.sort() != null && !p.sort().isBlank()) b.queryParam("sort", p.sort());
                        return b.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null
                    ? new PageView<>(List.of(), 0, p.size() == null ? 20 : p.size(), 0L, 0, null)
                    : body;
        } catch (RestClientException ex) {
            throw upstream("list-employees", ex);
        }
    }

    /** Identity-only resolver keyed by user id. No `employee.read` required upstream. */
    public List<EmployeeIdentitySummary> identitiesByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        String csv = userIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        return getList(uri -> uri.path("/employees/by-user-ids").queryParam("userIds", csv).build(),
                new ParameterizedTypeReference<>() {}, "identities-by-user-ids");
    }

    public List<EmployeeDto> directReports(long managerId) {
        try {
            PageView<EmployeeDto> body = http.get()
                    .uri(uri -> uri.path("/employees/{id}/team").queryParam("size", 200).build(managerId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? List.of() : body.contentOrEmpty();
        } catch (RestClientException ex) {
            throw upstream("team", ex);
        }
    }

    /** Single-row probe for stats. Returns 0 when nothing matches. */
    public long countEmployees(SearchParams p) {
        return listEmployees(p.withSize(1)).totalElements();
    }

    // ----- per-employee side reads -----

    public List<EmploymentContractDto> contracts(long id) {
        return getList(uri -> uri.path("/employees/{id}/contracts").build(id),
                new ParameterizedTypeReference<>() {}, "contracts");
    }

    public List<SuspensionDto> suspensions(long id) {
        return getList(uri -> uri.path("/employees/{id}/suspensions").build(id),
                new ParameterizedTypeReference<>() {}, "suspensions");
    }

    public ExitDto exitOrNull(long id) {
        try {
            return http.get()
                    .uri(uri -> uri.path("/employees/{id}/exit").build(id))
                    .retrieve()
                    .body(ExitDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        } catch (RestClientException ex) {
            throw upstream("exit", ex);
        }
    }

    public DisciplinaryActionListResponse disciplinaryFor(long id) {
        return get(uri -> uri.path("/employees/{id}/disciplinary-actions").build(id),
                new ParameterizedTypeReference<>() {}, "disciplinary-for");
    }

    public PageView<DisciplinaryActionDto> disciplinaryByStatus(String status, int size) {
        try {
            PageView<DisciplinaryActionDto> body = http.get()
                    .uri(uri -> {
                        var b = uri.path("/disciplinary-actions").queryParam("size", size);
                        if (status != null) b.queryParam("status", status);
                        return b.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? new PageView<>(List.of(), 0, size, 0L, 0, null) : body;
        } catch (RestClientException ex) {
            throw upstream("disciplinary-by-status", ex);
        }
    }

    public PageView<EmployeeHistoryDto> history(long id, int size) {
        try {
            PageView<EmployeeHistoryDto> body = http.get()
                    .uri(uri -> uri.path("/employees/{id}/history")
                            .queryParam("page", 0).queryParam("size", size).build(id))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? new PageView<>(List.of(), 0, size, 0L, 0, null) : body;
        } catch (RestClientException ex) {
            throw upstream("history", ex);
        }
    }

    // ----- pending-approval inboxes -----

    /**
     * Caller-scoped requisition inbox, one large page — the composite fans
     * these out and merges, no further pagination round-trip.
     */
    public PageView<RequisitionDto> requisitionsPending(int size, String q, String type, Long departmentId) {
        try {
            PageView<RequisitionDto> body = http.get()
                    .uri(uri -> {
                        var b = uri.path("/requisitions/pending-approvals").queryParam("size", size);
                        if (q != null) b.queryParam("q", q);
                        if (type != null) b.queryParam("type", type);
                        if (departmentId != null) b.queryParam("departmentId", departmentId);
                        return b.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? new PageView<>(List.of(), 0, size, 0L, 0, null) : body;
        } catch (RestClientException ex) {
            throw upstream("requisitions-pending", ex);
        }
    }

    public FactoryToursInbox factoryToursPending() {
        return get("/factory-tours/pending-approvals",
                new ParameterizedTypeReference<>() {}, "factory-tours-pending");
    }

    /**
     * Stage-aware Travel Request inbox (TRANSPORT / DD-Head, no HR stage),
     * same downstream endpoint the Travel module's own screen uses. Every
     * filter is optional; {@code from}/{@code to} window on the trip dates.
     */
    public PageView<TravelRequestResponse> travelRequestsPending(int size, String q, String travelTypeCode,
                                                                 LocalDate from, LocalDate to) {
        try {
            PageView<TravelRequestResponse> body = http.get()
                    .uri(uri -> {
                        var b = uri.path("/travel/requests/pending-approvals").queryParam("size", size);
                        if (q != null) b.queryParam("q", q);
                        if (travelTypeCode != null) b.queryParam("travelTypeCode", travelTypeCode);
                        if (from != null) b.queryParam("from", from);
                        if (to != null) b.queryParam("to", to);
                        return b.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? new PageView<>(List.of(), 0, size, 0L, 0, null) : body;
        } catch (RestClientException ex) {
            throw upstream("travel-requests-pending", ex);
        }
    }

    /** Employee's own configured approval chains (requisitions, factory tours). */
    public List<WorkflowChainDto> approvalWorkflows() {
        return getList("/approval-workflows",
                new ParameterizedTypeReference<>() {}, "approval-workflows");
    }

    /**
     * Caller-scoped approval decisions in a date range (inclusive). Mirrors
     * {@code AttendanceClient.approvalHistory} — pulls one page at the
     * upstream max, used by the cross-service approval-history composite.
     */
    public List<ApprovalDecisionRow> approvalHistory(LocalDate from, LocalDate to, String type, String decision) {
        try {
            PageView<ApprovalDecisionRow> body = http.get()
                    .uri(uri -> {
                        var b = uri.path("/approvals/history")
                                .queryParam("from", from).queryParam("to", to)
                                .queryParam("size", 200);
                        if (type != null) b.queryParam("type", type);
                        if (decision != null) b.queryParam("decision", decision);
                        return b.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? List.of() : body.contentOrEmpty();
        } catch (RestClientException ex) {
            throw upstream("approval-history", ex);
        }
    }

    // ----- ESS reads (caller's own data) -----

    public List<EmploymentContractDto> myContracts() {
        return getList("/employees/me/contracts", new ParameterizedTypeReference<>() {}, "me-contracts");
    }

    public List<EmployeeDto> myTeam() {
        try {
            PageView<EmployeeDto> body = http.get()
                    .uri(uri -> uri.path("/employees/me/team").queryParam("size", 200).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? List.of() : body.contentOrEmpty();
        } catch (RestClientException ex) {
            throw upstream("me-team", ex);
        }
    }

    /** ESS-side manager peek — open to any authenticated employee, returns null when no manager is assigned. */
    public EmployeeDto myManagerOrNull() {
        try {
            return http.get()
                    .uri("/employees/me/manager")
                    .retrieve()
                    .body(EmployeeDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        } catch (RestClientException ex) {
            throw upstream("me-manager", ex);
        }
    }

    // ----- catalogs (onboarding wizard, label resolution) -----
    // 5-min Caffeine cache (BffCacheConfig) — these are read on every
    // directory page render and rarely change. Catalog edit endpoints
    // are HR-admin only; staleness window of 5 min is acceptable.

    @org.springframework.cache.annotation.Cacheable(value = com.forward.hrms.bff.config.BffCacheConfig.CACHE_CATALOGS, key = "#root.methodName")
    public List<DepartmentDto> departments() {
        return getList("/departments", new ParameterizedTypeReference<>() {}, "departments");
    }

    @org.springframework.cache.annotation.Cacheable(value = com.forward.hrms.bff.config.BffCacheConfig.CACHE_CATALOGS, key = "#root.methodName")
    public List<SectionDto> sections() {
        return getList("/sections", new ParameterizedTypeReference<>() {}, "sections");
    }

    @org.springframework.cache.annotation.Cacheable(value = com.forward.hrms.bff.config.BffCacheConfig.CACHE_CATALOGS, key = "#root.methodName")
    public List<DesignationDto> designations() {
        return getList("/designations", new ParameterizedTypeReference<>() {}, "designations");
    }

    @org.springframework.cache.annotation.Cacheable(value = com.forward.hrms.bff.config.BffCacheConfig.CACHE_CATALOGS, key = "#root.methodName")
    public List<GradeDto> grades() {
        return getList("/grades", new ParameterizedTypeReference<>() {}, "grades");
    }

    @org.springframework.cache.annotation.Cacheable(value = com.forward.hrms.bff.config.BffCacheConfig.CACHE_CATALOGS, key = "#root.methodName")
    public List<ProvinceDto> provinces() {
        return getList("/provinces", new ParameterizedTypeReference<>() {}, "provinces");
    }

    @org.springframework.cache.annotation.Cacheable(value = com.forward.hrms.bff.config.BffCacheConfig.CACHE_CATALOGS, key = "#root.methodName")
    public List<CityDto> cities() {
        return getList("/cities", new ParameterizedTypeReference<>() {}, "cities");
    }

    @org.springframework.cache.annotation.Cacheable(value = com.forward.hrms.bff.config.BffCacheConfig.CACHE_CATALOGS, key = "#root.methodName")
    public List<QualificationDto> qualifications() {
        return getList("/qualifications", new ParameterizedTypeReference<>() {}, "qualifications");
    }

    @org.springframework.cache.annotation.Cacheable(value = com.forward.hrms.bff.config.BffCacheConfig.CACHE_CATALOGS, key = "#root.methodName")
    public List<SkillDto> skills() {
        return getList("/skills", new ParameterizedTypeReference<>() {}, "skills");
    }

    // ----- aggregates -----

    public AggregateDto aggregate(String groupBy) {
        return get(uri -> uri.path("/employees/aggregate").queryParam("groupBy", groupBy).build(),
                new ParameterizedTypeReference<>() {}, "aggregate");
    }

    /** Slice of employee-service's {@code HeadcountTrendDto}. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record HeadcountTrendView(int months, List<HeadcountMonthView> series) {

        @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
        public record HeadcountMonthView(String month, long joiners, long leavers, long endHeadcount) {}
    }

    public HeadcountTrendView headcountTrend(int months) {
        return get(uri -> uri.path("/employees/headcount-trend").queryParam("months", months).build(),
                new ParameterizedTypeReference<>() {}, "headcount-trend");
    }

    /** Convenience: count of certs expiring within `days`. */
    public long countExpiringCertifications(int days) {
        try {
            PageView<Object> body = http.get()
                    .uri(uri -> uri.path("/certifications")
                            .queryParam("expiringWithin", days)
                            .queryParam("page", 0).queryParam("size", 1).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? 0L : body.totalElements();
        } catch (RestClientException ex) {
            throw upstream("count-expiring-certs", ex);
        }
    }

    public long countExpiredCertifications() {
        try {
            PageView<Object> body = http.get()
                    .uri(uri -> uri.path("/certifications")
                            .queryParam("expiringWithin", 0)
                            .queryParam("expired", true)
                            .queryParam("page", 0).queryParam("size", 1).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? 0L : body.totalElements();
        } catch (RestClientException ex) {
            throw upstream("count-expired-certs", ex);
        }
    }

    // ----- request shape -----

    public record SearchParams(
            String status,
            String employmentType,
            Long departmentId,
            Long sectionId,
            Long managerId,
            Long gradeId,
            Long designationId,
            Boolean hasEss,
            String q,
            Integer probationDueWithin,
            Boolean probationOverdue,
            Integer page,
            Integer size,
            String sort
    ) {
        public static SearchParams empty() {
            return new SearchParams(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        public SearchParams withSize(int size) {
            return new SearchParams(status, employmentType, departmentId, sectionId, managerId,
                    gradeId, designationId, hasEss, q, probationDueWithin, probationOverdue,
                    page, size, sort);
        }

        public SearchParams withStatus(String s) {
            return new SearchParams(s, employmentType, departmentId, sectionId, managerId,
                    gradeId, designationId, hasEss, q, probationDueWithin, probationOverdue,
                    page, size, sort);
        }

        public SearchParams withProbationDueWithin(int n) {
            return new SearchParams(status, employmentType, departmentId, sectionId, managerId,
                    gradeId, designationId, hasEss, q, n, probationOverdue,
                    page, size, sort);
        }

        public SearchParams withProbationOverdue(boolean b) {
            return new SearchParams(status, employmentType, departmentId, sectionId, managerId,
                    gradeId, designationId, hasEss, q, probationDueWithin, b,
                    page, size, sort);
        }

        public SearchParams withHasEss(boolean b) {
            return new SearchParams(status, employmentType, departmentId, sectionId, managerId,
                    gradeId, designationId, b, q, probationDueWithin, probationOverdue,
                    page, size, sort);
        }

        public SearchParams withQ(String s) {
            return new SearchParams(status, employmentType, departmentId, sectionId, managerId,
                    gradeId, designationId, hasEss, s, probationDueWithin, probationOverdue,
                    page, size, sort);
        }
    }

    // ----- low-level helpers -----

    private <T> T get(String path, ParameterizedTypeReference<T> type, String what) {
        try {
            return http.get().uri(path).retrieve().body(type);
        } catch (RestClientException ex) {
            throw upstream(what, ex);
        }
    }

    private <T> T get(Function<org.springframework.web.util.UriBuilder, java.net.URI> uri,
                      ParameterizedTypeReference<T> type, String what) {
        try {
            return http.get().uri(uri).retrieve().body(type);
        } catch (RestClientException ex) {
            throw upstream(what, ex);
        }
    }

    private <T> List<T> getList(String path, ParameterizedTypeReference<List<T>> type, String what) {
        try {
            List<T> body = http.get().uri(path).retrieve().body(type);
            return body == null ? List.of() : body;
        } catch (RestClientException ex) {
            throw upstream(what, ex);
        }
    }

    private <T> List<T> getList(Function<org.springframework.web.util.UriBuilder, java.net.URI> uri,
                                ParameterizedTypeReference<List<T>> type, String what) {
        try {
            List<T> body = http.get().uri(uri).retrieve().body(type);
            return body == null ? List.of() : body;
        } catch (RestClientException ex) {
            throw upstream(what, ex);
        }
    }

    private static UpstreamError upstream(String what, RestClientException ex) {
        log.warn("employee-service call failed for {}: {}", what, ex.getMessage());
        return new UpstreamError(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                "employee-service request failed.", ex);
    }
}

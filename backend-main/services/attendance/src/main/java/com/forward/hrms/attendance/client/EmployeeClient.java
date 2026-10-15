package com.forward.hrms.attendance.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.forward.hrms.attendance.config.AttendanceDownstreamProperties;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.commons.web.security.IdentityHeaders;
import com.forward.hrms.employee.dto.response.EmployeeStatusDto;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

/**
 * Thin client over employee-service. Used to resolve the caller's
 * employee id from the JWT {@code userId}, look up direct managers /
 * department heads at request submit (for snapshot stamping on
 * {@code approval_steps.assigned_to}), and to validate department
 * membership for bulk OT.
 *
 * <p>Identity headers are forwarded by the commons-web
 * {@code RestClientCustomizer} — same convention every service uses.
 */
@Slf4j
@Component
public class EmployeeClient {

    /**
     * Batch size for {@code GET /employees/employment-statuses}. Must not
     * exceed the 300-id cap employee-service enforces (422 above it).
     */
    private static final int STATUSES_CHUNK = 300;

    private final RestClient http;

    public EmployeeClient(AttendanceDownstreamProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.employee().baseUrl()).build();
    }

    /**
     * Resolve the caller's own employee row. Both callers pass their own
     * {@code userId}; we route to {@code GET /employees/me} which is gated
     * by authentication only, not the broader {@code employee.read}
     * permission. The userId arg is kept for the call-site contract (and as
     * a sanity check) — if a future caller wants someone else's employee
     * row they should add a dedicated method.
     */
    public Optional<EmployeeView> findByUserId(long userId) {
        try {
            EmployeeView v = http.get()
                    .uri("/employees/me")
                    .retrieve()
                    .body(EmployeeView.class);
            if (v == null) return Optional.empty();
            // Defensive: /employees/me returns the row for the caller's
            // identity. If the caller's userId doesn't match the requested
            // one, treat as "not found" rather than returning the wrong row.
            if (v.userId() != null && v.userId() != userId) return Optional.empty();
            return Optional.of(v);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            throw upstream("findByUserId", ex);
        }
    }

    /**
     * Single-row lookup, routed through {@code GET /employees/by-ids}.
     *
     * <p>Deliberately <em>not</em> {@code GET /employees/{id}}: that endpoint
     * requires {@code employee.read}, which MANAGER lost in auth V0024 and
     * which ATTENDANCE_OPERATOR, TEAM_REQUEST_GENERATION and EMPLOYEE never
     * had. Every attendance caller of this method acts on behalf of someone
     * else under an {@code attendance.*} permission, so a 403 from
     * employee-service surfaced as a bare 502 UPSTREAM_EMPLOYEE_FAILED.
     * {@code /by-ids} is {@code isAuthenticated()} and returns the same
     * {@code EmployeeDto} shape, including {@code managerUserId}.
     */
    public EmployeeView getById(long id) {
        return getByIdOptional(id)
                .orElseThrow(() -> new UpstreamError(AttendanceErrorCode.EMPLOYEE_NOT_FOUND,
                        "employee not found", null));
    }

    /** {@link #getById(long)} without the throw; empty when the id is unknown upstream. */
    public Optional<EmployeeView> getByIdOptional(long id) {
        return getByIds(List.of(id)).stream().findFirst();
    }

    public Optional<EmployeeView> findByEmployeeCode(String code) {
        try {
            EmployeePage page = http.get()
                    .uri(uri -> uri.path("/employees").queryParam("q", code).queryParam("size", 5).build())
                    .retrieve()
                    .body(EmployeePage.class);
            if (page == null || page.content() == null) return Optional.empty();
            return page.content().stream().filter(e -> code.equals(e.employeeCode())).findFirst();
        } catch (RestClientException ex) {
            throw upstream("findByEmployeeCode", ex);
        }
    }

    /**
     * Resolves a department / section filter to the employee ids that match.
     * Used by the org-wide attendance listing endpoints, which keep
     * {@code attendance.*} tables free of denormalized org columns and instead
     * pre-resolve the filter against employee-service. Forward Sports' headcount
     * fits in a single page; we use {@code size=2000} as a safe ceiling. Returns
     * an empty list when the org filter matches no employees.
     */
    public List<Long> findIdsByOrg(Long departmentId, Long sectionId) {
        try {
            EmployeePage page = http.get()
                    .uri(uri -> {
                        var b = uri.path("/employees").queryParam("size", 2000);
                        if (departmentId != null) b.queryParam("departmentId", departmentId);
                        if (sectionId != null) b.queryParam("sectionId", sectionId);
                        return b.build();
                    })
                    .retrieve()
                    .body(EmployeePage.class);
            if (page == null || page.content() == null) return List.of();
            return page.content().stream().map(EmployeeView::id).toList();
        } catch (RestClientException ex) {
            throw upstream("findIdsByOrg", ex);
        }
    }

    /**
     * Bulk lookup mirroring employee-service's {@code GET /employees/by-ids}
     * (capped at 200 per call there). We chunk by 50 to keep individual
     * URLs short and to stay well below the upstream cap. Missing ids
     * are silently dropped — callers that need a strict map join the
     * response into their own map keyed by id.
     */
    public List<EmployeeView> getByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Long> all = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(ids));
        List<EmployeeView> out = new java.util.ArrayList<>(all.size());
        try {
            for (int i = 0; i < all.size(); i += 50) {
                List<Long> chunk = all.subList(i, Math.min(i + 50, all.size()));
                List<EmployeeView> body = http.get()
                        .uri(uri -> uri.path("/employees/by-ids")
                                .queryParam("ids", chunk.toArray())
                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
                if (body != null) out.addAll(body);
            }
            return out;
        } catch (RestClientException ex) {
            throw upstream("getByIds", ex);
        }
    }

    /**
     * {@code employee_id → employment_status} straight from employee-service,
     * for scheduler threads. There's no inbound request for the
     * {@code IdentityForwardingInterceptor} to copy {@code X-User-*} from, so
     * the call carries an explicit {@code system} identity — enough for the
     * {@code isAuthenticated()} gate on {@code /employees/employment-statuses}.
     * Ids missing upstream are absent from the map.
     */
    public java.util.Map<Long, String> employmentStatusesAsSystem(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return java.util.Map.of();
        List<Long> all = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(ids));
        java.util.Map<Long, String> out = new java.util.HashMap<>(all.size());
        try {
            for (int i = 0; i < all.size(); i += STATUSES_CHUNK) {
                List<Long> chunk = all.subList(i, Math.min(i + STATUSES_CHUNK, all.size()));
                List<EmployeeStatusDto> body = http.get()
                        .uri(uri -> uri.path("/employees/employment-statuses")
                                .queryParam("ids", chunk)
                                .build())
                        .headers(h -> {
                            h.set(IdentityHeaders.USER_ID, "0");
                            h.set(IdentityHeaders.USERNAME, CurrentUser.SYSTEM_USERNAME);
                        })
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
                if (body != null) body.forEach(e -> out.put(e.id(), e.employmentStatus().name()));
            }
            return out;
        } catch (RestClientException ex) {
            throw upstream("employmentStatusesAsSystem", ex);
        }
    }

    /**
     * Ids of everyone in the caller's department (the caller included), via
     * employee-service's {@code GET /employees/me/department/employees}. Like
     * {@link #myDirectReportIds()} it is scoped to the forwarded caller and
     * needs no {@code employee.read}. Pages at the upstream cap of 200 until
     * a short page comes back.
     */
    public List<Long> myDepartmentEmployeeIds() {
        final int size = 200;
        List<Long> ids = new java.util.ArrayList<>();
        try {
            for (int page = 0; ; page++) {
                final int p = page;
                EmployeePage body = http.get()
                        .uri(uri -> uri.path("/employees/me/department/employees")
                                .queryParam("page", p)
                                .queryParam("size", size)
                                .build())
                        .retrieve()
                        .body(EmployeePage.class);
                List<EmployeeView> content = body == null || body.content() == null
                        ? List.of() : body.content();
                content.forEach(e -> ids.add(e.id()));
                if (content.size() < size) return ids;
            }
        } catch (HttpClientErrorException.NotFound ex) {
            // Caller has no employee record, or no department — nobody to list.
            return List.of();
        } catch (RestClientException ex) {
            throw upstream("myDepartmentEmployeeIds", ex);
        }
    }

    /**
     * Everyone in the caller's department matching the optional
     * {@code sectionId} and {@code q} (substring of full name, employee code,
     * work email, phone or CNIC) filters, via {@code GET /employees/me/department/employees}.
     * Sorted by employee code upstream; pages at the upstream cap of 200 until
     * a short page comes back. Empty when the caller has no employee record
     * or department.
     */
    public List<EmployeeView> myDepartmentEmployees(Long sectionId, String q) {
        final int size = 200;
        List<EmployeeView> out = new java.util.ArrayList<>();
        java.util.Optional<String> qParam =
                java.util.Optional.ofNullable(q).map(String::trim).filter(x -> !x.isEmpty());
        try {
            for (int page = 0; ; page++) {
                final int p = page;
                EmployeePage body = http.get()
                        .uri(uri -> uri.path("/employees/me/department/employees")
                                .queryParamIfPresent("sectionId", java.util.Optional.ofNullable(sectionId))
                                .queryParamIfPresent("q", qParam)
                                .queryParam("page", p)
                                .queryParam("size", size)
                                .build())
                        .retrieve()
                        .body(EmployeePage.class);
                List<EmployeeView> content = body == null || body.content() == null
                        ? List.of() : body.content();
                out.addAll(content);
                if (content.size() < size) return out;
            }
        } catch (HttpClientErrorException.NotFound ex) {
            return List.of();
        } catch (RestClientException ex) {
            throw upstream("myDepartmentEmployees", ex);
        }
    }

    /**
     * Ids of the caller's direct reports, via employee-service's
     * {@code GET /employees/me/team}. That endpoint is scoped to the forwarded
     * caller and needs no permission, so it works for a MANAGER without
     * {@code employee.read} — unlike {@link #getDirectReports(long)}. Pages at
     * the upstream cap of 200 until a short page comes back.
     */
    public List<Long> myDirectReportIds() {
        final int size = 200;
        List<Long> ids = new java.util.ArrayList<>();
        try {
            for (int page = 0; ; page++) {
                final int p = page;
                EmployeePage body = http.get()
                        .uri(uri -> uri.path("/employees/me/team")
                                .queryParam("page", p)
                                .queryParam("size", size)
                                .build())
                        .retrieve()
                        .body(EmployeePage.class);
                List<EmployeeView> content = body == null || body.content() == null
                        ? List.of() : body.content();
                content.forEach(e -> ids.add(e.id()));
                if (content.size() < size) return ids;
            }
        } catch (HttpClientErrorException.NotFound ex) {
            // Caller has no employee record — nobody reports to them.
            return List.of();
        } catch (RestClientException ex) {
            throw upstream("myDirectReportIds", ex);
        }
    }

    public List<EmployeeView> getDirectReports(long managerId) {
        try {
            List<EmployeeView> body = http.get()
                    .uri("/employees/{id}/team", managerId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? List.of() : body;
        } catch (HttpClientErrorException.NotFound ex) {
            return List.of();
        } catch (RestClientException ex) {
            throw upstream("getDirectReports", ex);
        }
    }

    private static UpstreamError upstream(String op, RestClientException ex) {
        log.warn("employee-service {} failed: {}", op, ex.getMessage());
        return new UpstreamError(AttendanceErrorCode.UPSTREAM_EMPLOYEE_FAILED,
                "employee-service " + op + " failed.", ex);
    }

    /** Mirrors the subset of EmployeeDto attendance-service consumes. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmployeeView(
            long id,
            Long userId,
            String employeeCode,
            String fullName,
            Long departmentId,
            Long sectionId,
            Long managerId,
            Long managerUserId,
            String employmentStatus,
            java.time.LocalDate joiningDate
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmployeePage(List<EmployeeView> content) {
        EmployeeView firstMatching(long userId) {
            if (content == null) return null;
            return content.stream().filter(e -> e.userId() != null && e.userId() == userId).findFirst().orElse(null);
        }
    }
}

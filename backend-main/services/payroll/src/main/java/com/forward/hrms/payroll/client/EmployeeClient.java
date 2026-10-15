package com.forward.hrms.payroll.client;

import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.commons.web.security.IdentityHeaders;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.employee.dto.response.BelongingClearanceCheckDto;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.GradeDto;
import com.forward.hrms.employee.dto.response.OffCyclePaymentTypeDto;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.GradeType;
import com.forward.hrms.payroll.client.dto.employee.AnnualLeaveEligibleResponseDto;
import com.forward.hrms.payroll.client.dto.employee.EmployeeTerminationInfo;
import com.forward.hrms.payroll.client.dto.PageView;
import com.forward.hrms.payroll.config.PayrollDownstreamProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reads basic employee fields from employee-service when payroll needs
 * them (employee_code on a payslip, full_name on a PDF). Best-effort —
 * if employee-service is down compute falls back to a placeholder.
 *
 * <p>Identity headers are forwarded by the commons-web
 * {@code RestClientCustomizer}.
 */
@Slf4j
@Component
public class EmployeeClient {

    private final RestClient http;

    public EmployeeClient(PayrollDownstreamProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.employee().baseUrl()).build();
    }

    public EmployeeBrief get(long employeeId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = http.get().uri("/employees/{id}", employeeId)
                    .retrieve().body(Map.class);
            if (body == null) return EmployeeBrief.placeholder(employeeId);
            return briefFrom(body, employeeId);
        } catch (Exception ignored) {
            return EmployeeBrief.placeholder(employeeId);
        }
    }

    /**
     * Manager's *user id* (not employee id) for a MANAGER-kind approval stage
     * — {@code EmployeeDto.managerUserId} is already resolved server-side by
     * employee-service, so this is a single call, no second lookup needed.
     * Null on any failure or when the employee has no manager / the manager
     * has no ESS user (treated as "unresolvable", same as
     * {@code RequisitionManagerResolver}).
     */
    public Long managerUserId(long employeeId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = http.get().uri("/employees/{id}", employeeId)
                    .retrieve().body(Map.class);
            return body == null ? null : nullableLong(body.get("managerUserId"));
        } catch (Exception ex) {
            log.warn("managerUserId lookup failed for employee {}: {}", employeeId, ex.getMessage());
            return null;
        }
    }

    /**
     * Bulk variant — kills the per-row {@code GET /employees/{id}} N+1 in
     * payroll compute (~500 employees per run) and PDF generation.
     * Calls the {@code /employees/by-ids} batch endpoint in chunks of
     * {@value BATCH_SIZE} (matches employee-service's cap). Missing ids
     * resolve to a placeholder so payroll never fails on a stale row.
     */
    private static final int BATCH_SIZE = 200;

    public Map<Long, EmployeeBrief> getMany(Collection<Long> employeeIds) {
        return getMany(employeeIds, null);
    }

    public Map<Long, EmployeeBrief> getAllActiveEmployeesForBonusCalculation(Map<String, String> identity) {
        return getAllActiveEmployeesForCalculation(identity);
    }

    public Map<Long, EmployeeBrief> getAllActiveEmployeesForAllowanceCalculation(Map<String, String> identity) {
        return getAllActiveEmployeesForCalculation(identity);
    }

    private Map<Long, EmployeeBrief> getAllActiveEmployeesForCalculation(Map<String, String> identity) {
        Map<Long, EmployeeBrief> out = new LinkedHashMap<>();
        int page = 0;
        final int size = 100;
        while (true) {
            int currentPage = page;
            PageView<EmployeeDto> body = http.get()
                    .uri(uri -> uri.path("/employees")
                            .queryParam("status", EmploymentStatus.ACTIVE.name())
                            .queryParam("page", currentPage)
                            .queryParam("size", size)
                            .queryParam("sort", "employeeCode,asc")
                            .build())
                    .headers(h -> {
                        if (identity != null) identity.forEach(h::set);
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (body == null || body.contentOrEmpty().isEmpty()) {
                break;
            }
            for (EmployeeDto e : body.contentOrEmpty()) {
                out.put(e.id(), briefFrom(e));
            }
            page++;
        }
        return out;
    }

    /**
     * Saga-thread overload. Pass an {@code X-User-*} snapshot from the
     * kickoff caller (see {@link #currentRequestIdentity()}); without it
     * employee-service's permission check rejects the call as unauthenticated.
     */
    public Map<Long, EmployeeBrief> getMany(Collection<Long> employeeIds,
                                            Map<String, String> identity) {
        Map<Long, EmployeeBrief> out = new LinkedHashMap<>();
        if (employeeIds == null || employeeIds.isEmpty()) return out;
        List<Long> distinct = employeeIds.stream().distinct().toList();
        for (int i = 0; i < distinct.size(); i += BATCH_SIZE) {
            List<Long> chunk = distinct.subList(i, Math.min(i + BATCH_SIZE, distinct.size()));
            try {
                List<Map<String, Object>> rows = http.get()
                        .uri(uri -> uri.path("/employees/by-ids")
                                .queryParam("ids", chunk.stream().map(String::valueOf)
                                        .collect(Collectors.joining(",")))
                                .build())
                        .headers(h -> {
                            if (identity != null) identity.forEach(h::set);
                        })
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {
                        });
                if (rows == null) continue;
                for (Map<String, Object> body : rows) {
                    Object idObj = body.get("id");
                    if (!(idObj instanceof Number n)) continue;
                    long id = n.longValue();
                    out.put(id, briefFrom(body, id));
                }
            } catch (Exception ex) {
                log.warn("bulk /employees/by-ids failed for chunk size {}: {}", chunk.size(), ex.getMessage());
                // fall through — caller's getOrDefault produces placeholders
            }
        }
        for (Long id : distinct) {
            out.computeIfAbsent(id, EmployeeBrief::placeholder);
        }
        return out;
    }

    /**
     * Full {@link EmployeeDto} per id via {@code /employees/by-ids}, for
     * accounts reports that print father name, CNIC, grade, status and
     * org placement. Unlike {@link #getMany} this <b>fails closed</b>: a
     * printed financial report with placeholder names is worse than a 502.
     * Ids employee-service doesn't return are simply absent from the map.
     */
    public Map<Long, EmployeeDto> getDetails(Collection<Long> employeeIds) {
        Map<Long, EmployeeDto> out = new LinkedHashMap<>();
        if (employeeIds == null || employeeIds.isEmpty()) return out;
        List<Long> distinct = employeeIds.stream().distinct().toList();
        for (int i = 0; i < distinct.size(); i += BATCH_SIZE) {
            List<Long> chunk = distinct.subList(i, Math.min(i + BATCH_SIZE, distinct.size()));
            List<EmployeeDto> rows;
            try {
                rows = http.get()
                        .uri(uri -> uri.path("/employees/by-ids")
                                .queryParam("ids", chunk.stream().map(String::valueOf)
                                        .collect(Collectors.joining(",")))
                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<EmployeeDto>>() {
                        });
            } catch (Exception ex) {
                throw new UpstreamError(PayrollErrorCode.ACCOUNTS_REPORT_EMPLOYEE_LOOKUP_FAILED,
                        "employee lookup failed: " + ex.getMessage(), ex);
            }
            if (rows != null) rows.forEach(e -> out.put(e.id(), e));
        }
        return out;
    }

    /**
     * Snapshots {@code X-User-*} headers from the active request — call
     * from a request-bound thread to capture identity for later replay
     * by a saga step. Returns an empty map outside a request.
     */
    public static Map<String, String> currentRequestIdentity() {
        Map<String, String> result = new LinkedHashMap<>();
        Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .ifPresent(req -> {
                    for (String name : IdentityHeaders.ALL) {
                        String value = req.getHeader(name);
                        if (value != null && !value.isBlank()) result.put(name, value);
                    }
                });
        return result;
    }

    /**
     * Fetches the employee's {@code joiningDate} via {@code GET /employees/{id}}.
     * Used by the payroll-settings PATCH upsert path when the row hasn't
     * been seeded yet by the {@code employee.created} consumer. Returns
     * {@code null} if employee-service is down, the employee doesn't exist,
     * or the field is missing.
     */
    public LocalDate fetchJoiningDate(long employeeId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = http.get().uri("/employees/{id}", employeeId)
                    .retrieve().body(Map.class);
            if (body == null) return null;
            Object v = body.get("joiningDate");
            return v == null ? null : LocalDate.parse(v.toString());
        } catch (Exception ex) {
            log.debug("could not resolve joiningDate for employee {}: {}", employeeId, ex.getMessage());
            return null;
        }
    }

    /**
     * Resolves the caller's {@code employees.id} via {@code GET /employees/me}
     * using the inbound {@code X-User-*} identity (forwarded automatically by
     * the commons-web interceptor). Returns {@code null} on any failure —
     * caller has no employee linked, employee-service down, etc. Used to
     * gate self-serve payslip reads.
     */
    public Long meEmployeeIdOrNull() {
        return me().employeeId();
    }

    /**
     * The caller's own {@code employees.id} and {@code managerUserId} in
     * one {@code GET /employees/me} call — same self-serve, permission-free
     * endpoint the leave/OT/regularization approval flows resolve their
     * MANAGER stage from (see attendance's {@code CallerContext} /
     * {@code AttendanceManagerResolver}: they cache one {@code /employees/me}
     * response per request and read {@code managerUserId} straight off it,
     * rather than the {@code employee.read}-gated {@code GET /employees/{id}}
     * that {@link #managerUserId(long)} uses). Both fields null on any
     * failure — no employee linked, employee-service down, or no manager set.
     */
    public Me me() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = http.get().uri("/employees/me")
                    .retrieve().body(Map.class);
            if (body == null) return new Me(null, null);
            return new Me(nullableLong(body.get("id")), nullableLong(body.get("managerUserId")));
        } catch (Exception ex) {
            log.debug("could not resolve caller via /employees/me: {}", ex.getMessage());
            return new Me(null, null);
        }
    }

    public record Me(Long employeeId, Long managerUserId) {}

    /**
     * Everyone in the caller's department (the caller included), via
     * {@code GET /employees/me/department/employees} — open to any
     * authenticated employee, so no {@code employee.read}. Returns an empty
     * set on any failure, so the caller then covers nobody.
     */
    public java.util.Set<Long> myDepartmentEmployeeIds() {
        java.util.Set<Long> ids = new java.util.LinkedHashSet<>();
        final int size = 200;
        try {
            for (int page = 0; ; page++) {
                final int p = page;
                PageView<EmployeeDto> body = http.get()
                        .uri(uri -> uri.path("/employees/me/department/employees")
                                .queryParam("page", p)
                                .queryParam("size", size)
                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {
                        });
                List<EmployeeDto> content = body == null ? List.of() : body.contentOrEmpty();
                content.forEach(e -> ids.add(e.id()));
                if (content.size() < size) return ids;
            }
        } catch (Exception ex) {
            log.debug("could not resolve caller department via /employees/me/department/employees: {}",
                    ex.getMessage());
            return java.util.Set.of();
        }
    }

    /**
     * Resolves the calling manager's direct-report employee ids via
     * {@code GET /employees/me/team} (open to any authenticated employee —
     * no {@code employee.read} needed, which managers don't have). Used to
     * scope a manager's {@code payroll.read.team} payslip reads to their
     * own reports. Returns an empty set on any failure (no employee linked,
     * employee-service down) — the caller then covers nobody. Page size 200
     * matches the upstream cap; a manager with more than 200 direct reports
     * is not a real Forward Sports shape.
     */
    public java.util.Set<Long> myDirectReportIds() {
        try {
            PageView<EmployeeDto> body = http.get()
                    .uri(uri -> uri.path("/employees/me/team").queryParam("size", 200).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (body == null) return java.util.Set.of();
            return body.contentOrEmpty().stream()
                    .map(EmployeeDto::id)
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        } catch (Exception ex) {
            log.debug("could not resolve caller direct reports via /employees/me/team: {}", ex.getMessage());
            return java.util.Set.of();
        }
    }


    private static EmployeeBrief briefFrom(Map<String, Object> body, long employeeId) {
        String code = String.valueOf(body.getOrDefault("employeeCode", "EMP-" + employeeId));
        String name = String.valueOf(body.getOrDefault("fullName", "Employee " + employeeId));
        String cnic = nullableString(body.get("cnic"));
        String ntn = nullableString(body.get("ntn"));
        String eobi = nullableString(body.get("eobiNumber"));
        Long gradeId = nullableLong(body.get("gradeId"));
        Long designationId = nullableLong(body.get("designationId"));
        String employmentStatus = nullableString(body.get("employmentStatus"));
        LocalDate joiningDate = body.get("joiningDate") == null ? null : LocalDate.parse(body.get("joiningDate").toString());
        String fatherName = nullableString(body.get("fatherName"));
        return new EmployeeBrief(employeeId, code, name, cnic, ntn, eobi, gradeId, designationId,
                EmploymentStatus.valueOf(employmentStatus), joiningDate, fatherName);
    }

    private static EmployeeBrief briefFrom(EmployeeDto employee) {
        return new EmployeeBrief(
                employee.id(),
                employee.employeeCode(),
                employee.fullName(),
                employee.cnic(),
                employee.ntn(),
                employee.eobiNumber(),
                employee.gradeId(),
                employee.designationId(),
                employee.employmentStatus(),
                employee.joiningDate(),
                employee.fatherName());
    }

    private static String nullableString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Long nullableLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        return null;
    }

    /**
     * Loads the grade catalog once and returns
     * {@code gradeId → gradeType}. The catalog is small (dozens of rows)
     * so we hit {@code GET /grades} once per payroll run and look up
     * per-employee gradeType in memory. Returns an empty map on failure
     * — callers should treat a missing grade as LABOUR (the default).
     */
    public Map<Long, GradeType> gradeTypesById(Map<String, String> identity) {
        try {
            List<Map<String, Object>> rows = http.get()
                    .uri("/grades")
                    .headers(h -> {
                        if (identity != null) identity.forEach(h::set);
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (rows == null) return Map.of();
            Map<Long, GradeType> out = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                Long id = nullableLong(row.get("id"));
                Object type = row.get("gradeType");
                if (id == null || type == null) continue;
                try {
                    out.put(id, GradeType.valueOf(String.valueOf(type)));
                } catch (IllegalArgumentException ignored) {
                    // Unknown enum value — skip; lookup falls back to LABOUR.
                }
            }
            return out;
        } catch (Exception ex) {
            log.warn("gradeTypesById failed: {}", ex.getMessage());
            return Map.of();
        }
    }

    public List<GradeDto> getAllGrades(Map<String, String> identity){
        try {
            return http.get()
                    .uri("/grades")
                    .headers(h -> {
                        if (identity != null) identity.forEach(h::set);
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<GradeDto>>() {
                    });
        } catch (Exception ex) {
            log.warn("getAllGrades failed: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * The HR-maintained off-cycle payment type master, live from
     * employee-service.
     *
     * <p>Unlike the other catalog reads on this client, this one <b>fails
     * closed</b>: it is a write-path validator, and returning an empty
     * list on an outage would turn "employee-service is unreachable" into
     * "no type is valid" or, worse, silently accept an unvalidated type
     * onto a financial record. Transport and non-2xx failures become
     * {@link UpstreamError} (502) rather than escaping as an opaque 500 —
     * same treatment as AttendanceClient's snapshot reads.
     *
     * <p>Not read through {@code CatalogCache} on purpose — its catalog
     * TTL is an hour with manual invalidation only, so a type HR
     * deactivated 59 minutes ago would still validate. Off-cycle payments
     * are recorded a handful of times a day; a live call is the right
     * cost for a financial write.
     *
     * <p>The master exposes only a list endpoint (no {@code GET /{id}}),
     * hence the fetch-and-scan in
     * {@code OffCyclePaymentService.requireActiveType}.
     */
    public List<OffCyclePaymentTypeDto> activeOffCyclePaymentTypes(Map<String, String> identity) {
        List<OffCyclePaymentTypeDto> rows;
        try {
            rows = http.get()
                    .uri(uri -> uri.path("/employees/off-cycle-payment-types")
                            .queryParam("active", true)
                            .build())
                    .headers(h -> {
                        if (identity != null) identity.forEach(h::set);
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<OffCyclePaymentTypeDto>>() {});
        } catch (Exception ex) {
            throw new UpstreamError(PayrollErrorCode.OFF_CYCLE_TYPE_LOOKUP_FAILED,
                    "off-cycle payment type master lookup failed: " + ex.getMessage(), ex);
        }
        if (rows == null) {
            // A 2xx with no body is still "we could not read the master",
            // not "the master is empty" — treating it as empty would 422
            // every valid type and blame HR for an upstream fault.
            throw new UpstreamError(PayrollErrorCode.OFF_CYCLE_TYPE_LOOKUP_FAILED,
                    "off-cycle payment type master returned no body", null);
        }
        return rows;
    }

    public record EmployeeBrief(long employeeId, String employeeCode, String fullName,
                                String cnic, String ntn, String eobiNumber, Long gradeId, Long designationId,
                                EmploymentStatus employmentStatus, LocalDate joiningDate, String fatherName) {
        public static EmployeeBrief placeholder(long id) {
            return new EmployeeBrief(id, "EMP-" + id, "Employee " + id, null, null, null, null, null, null, null, null);
        }
    }

    // new methods to call employee service to get the information of Employee.
    public EmployeeTerminationInfo terminationInfoOld(long employeeId) {

        try {

            @SuppressWarnings("unchecked")
            Map<String, Object> body = http.get()
                    .uri("/employees/{id}/final-termination-settlement", employeeId)
                    .retrieve()
                    .body(Map.class);

            if (body == null) {
                return EmployeeTerminationInfo.placeholder(employeeId);
            }

            return terminationInfoFrom(body, employeeId);

        } catch (Exception ex) {

            log.error("Unable to fetch employee termination information for employee {} : {}",
                    employeeId,
                    ex.getMessage());

            return EmployeeTerminationInfo.placeholder(employeeId);
        }
    }

    // Calls employee-service to fetch information required by
    // Final Termination Settlement preview.
    public EmployeeTerminationInfo terminationInfo(long employeeId) {

        try {
            EmployeeTerminationInfo response = http.get()
                    .uri("/employees/{id}/final-termination-settlement", employeeId)
                    .retrieve()
                    .body(EmployeeTerminationInfo.class);
            log.info("Response: {}", response);

            return response != null
                    ? response
                    : EmployeeTerminationInfo.placeholder(employeeId);

        } catch (Exception ex) {
            log.warn("Unable to fetch employee termination information for employee {} : {}",
                    employeeId,
                    ex.getMessage()
            );

            return EmployeeTerminationInfo.placeholder(employeeId);
        }
    }

    // helper method relevant to it.
    private static EmployeeTerminationInfo terminationInfoFrom(
            Map<String, Object> body,
            long employeeId) {

        String employeeCode = nullableString(body.get("employeeCode"));

        String employeeName = nullableString(body.get("employeeName"));

        String department = nullableString(body.get("department"));

        String section = nullableString(body.get("section"));

        String designation = nullableString(body.get("designation"));

        String grade = nullableString(body.get("grade"));

        GradeType gradeType = null;

        Object value = body.get("gradeType");

        LocalDate joiningDate = body.get("joiningDate") != null ? LocalDate.parse(body.get("joiningDate").toString()) : null;

        if (value != null) {
            try {
                gradeType = GradeType.valueOf(value.toString());
            } catch (IllegalArgumentException ignored) {
            }
        }

        return new EmployeeTerminationInfo(

                employeeId,

                employeeCode,

                employeeName,

                department,

                section,

                designation,

                grade,

                gradeType,

                joiningDate

        );
    }

    /**
     * Company belongings still charged to the employee, and whether they
     * block a termination clearance. employee-service owns the rule (which
     * activation / clearance statuses count); payroll only renders the
     * verdict and refuses on it.
     *
     * <p>Deliberately NOT best-effort, unlike {@link #get(long)} and
     * {@link #terminationInfo(long)}. Those degrade to a placeholder name on
     * a PDF; this one decides whether an employee walks out still holding a
     * company motorcycle. "Could not reach employee-service" must never be
     * read as "holds nothing", so a failure throws {@link UpstreamError}
     * ({@code BELONGING_CLEARANCE_CHECK_FAILED}, 502) and HR retries — same
     * stance as {@code FAIR_PRICE_SHOP_CHECK_FAILED} and
     * {@code ATTENDANCE_LOCK_CHECK_FAILED}.
     *
     * <p>Not cached: HR clears an asset and retries the approval seconds
     * later, so a stale hit would be the bug. One indexed query per call on
     * the employee-service side.
     */
    public BelongingClearanceCheckDto belongingClearance(long employeeId) {
        try {
            BelongingClearanceCheckDto body = http.get()
                    .uri("/belonging-issuance/employee/{id}/clearance-check", employeeId)
                    .retrieve()
                    .body(BelongingClearanceCheckDto.class);

            if (body == null) {
                throw new UpstreamError(PayrollErrorCode.BELONGING_CLEARANCE_CHECK_FAILED,
                        "Belonging clearance check for employee " + employeeId
                                + " returned an empty body.", null);
            }
            return body;

        } catch (UpstreamError rethrow) {
            throw rethrow;
        } catch (Exception ex) {
            throw new UpstreamError(PayrollErrorCode.BELONGING_CLEARANCE_CHECK_FAILED,
                    "Belonging clearance check failed for employee %d: %s"
                            .formatted(employeeId, ex.getMessage()), ex);
        }
    }

    public List<AnnualLeaveEligibleResponseDto> annualLeaveEligibleEmployee(int year, int month){
        try{
            return http.post().uri("employees/annual-leave/eligible?year={year}&month={month}", year, month)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<AnnualLeaveEligibleResponseDto>>() {
                    });
        } catch (Exception e) {
            log.debug("Unable to fetch annual leave eligible employees for year {} and month {} : {}", year, month, e.getMessage());
            return null;
        }
    }
}

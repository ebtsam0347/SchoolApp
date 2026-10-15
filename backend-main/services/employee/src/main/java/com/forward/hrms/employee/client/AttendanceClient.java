package com.forward.hrms.employee.client;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.forward.hrms.attendance.dto.response.ShiftDto;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.employee.config.EmployeeDownstreamProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AttendanceClient {

    private final RestClient http;

    public AttendanceClient(EmployeeDownstreamProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.attendance().baseUrl()).build();
    }

    public void assignShift(long employeeId, AssignShiftRequest req,
                            Map<String, String> identity, String idempotencyKey) {
        try {
            http.post()
                    .uri("/shifts/employees/{id}", employeeId)
                    .headers(h -> applyHeaders(h, identity, idempotencyKey))
                    .body(req)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("attendance-service assignShift failed for employeeId={} shiftId={}: {}",
                    employeeId, req.shiftId(), ex.getMessage());
            throw upstream("assignShift", ex);
        }
    }

    public List<ShiftDto> listShifts() {
        try {
            ShiftDto[] rows = http.get()
                    .uri("/shifts")
                    .retrieve()
                    .body(ShiftDto[].class);
            return rows == null ? List.of() : List.of(rows);
        } catch (RestClientException ex) {
            log.warn("attendance-service listShifts failed: {}", ex.getMessage());
            throw upstream("listShifts", ex);
        }
    }

    /**
     * Name of the shift covering today, best effort: any failure (including
     * 403 for a caller without {@code attendance.read}) is logged and empty,
     * because the shift is informational on the documents that print it.
     * Request thread only — identity is forwarded by the commons-web interceptor.
     */
    public Optional<String> currentShiftName(long employeeId) {
        try {
            EmployeeShift[] rows = http.get()
                    .uri("/shifts/employees/{id}", employeeId)
                    .retrieve()
                    .body(EmployeeShift[].class);
            if (rows == null) return Optional.empty();
            LocalDate today = LocalDate.now();
            for (EmployeeShift s : rows) {
                boolean started = s.effectiveFrom() == null || !s.effectiveFrom().isAfter(today);
                boolean open = s.effectiveTo() == null || !s.effectiveTo().isBefore(today);
                if (started && open) return Optional.ofNullable(s.shiftName());
            }
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("attendance-service currentShiftName failed for employeeId={}: {}", employeeId, ex.getMessage());
            return Optional.empty();
        }
    }

    private static void applyHeaders(HttpHeaders h, Map<String, String> identity, String idempotencyKey) {
        h.setContentType(MediaType.APPLICATION_JSON);
        if (identity != null) identity.forEach(h::set);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            h.set("Idempotency-Key", idempotencyKey);
        }
    }

    private static UpstreamError upstream(String op, RestClientException ex) {
        return new UpstreamError(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                "attendance-service " + op + " failed.", ex);
    }

    /** Saga steps inspect the cause to choose RETRY (5xx / IO) vs FAILED (4xx). */
    public static Optional<HttpStatusCode> statusOf(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof RestClientResponseException rcre) return Optional.of(rcre.getStatusCode());
            if (cur instanceof HttpClientErrorException hce) return Optional.of(hce.getStatusCode());
            cur = cur.getCause();
        }
        return Optional.empty();
    }

    /** Mirrors attendance-service's AssignShiftRequest. */
    /** Mirrors attendance-service's EmployeeShiftDto (fields this client reads). */
    public record EmployeeShift(String shiftName, LocalDate effectiveFrom, LocalDate effectiveTo) {}

    public record AssignShiftRequest(Long shiftId, LocalDate effectiveFrom, LocalDate effectiveTo) {}
}

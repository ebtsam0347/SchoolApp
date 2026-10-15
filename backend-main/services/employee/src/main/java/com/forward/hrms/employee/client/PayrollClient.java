package com.forward.hrms.employee.client;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.employee.config.EmployeeDownstreamProperties;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryComponentDto;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryDto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PayrollClient {

    private final RestClient http;

    public PayrollClient(EmployeeDownstreamProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.payroll().baseUrl()).build();
    }

    public void reviseBasicSalary(long employeeId, ReviseBasicSalaryRequest req,
                                  Map<String, String> identity, String idempotencyKey) {
        try {
            http.post()
                    .uri("/salary/employees/{id}", employeeId)
                    .headers(h -> applyHeaders(h, identity, idempotencyKey))
                    .body(req)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.error("payroll-service reviseBasicSalary failed for employeeId={}: ",
                    employeeId, ex);
            throw upstream("reviseBasicSalary", ex);
        }
    }

    public void addSalaryComponent(long employeeId, AddSalaryLineRequest req,
                                   Map<String, String> identity, String idempotencyKey) {
        try {
            http.post()
                    .uri("/employees/{id}/salary-components", employeeId)
                    .headers(h -> applyHeaders(h, identity, idempotencyKey))
                    .body(req)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("payroll-service addSalaryComponent failed for employeeId={} componentId={}: {}",
                    employeeId, req.componentId(), ex.getMessage());
            throw upstream("addSalaryComponent", ex);
        }
    }

    public void putBankDetails(long employeeId, UpsertBankDetailsRequest req,
                               Map<String, String> identity, String idempotencyKey) {
        try {
            http.put()
                    .uri("/employees/{id}/bank-details", employeeId)
                    .headers(h -> applyHeaders(h, identity, idempotencyKey))
                    .body(req)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("payroll-service putBankDetails failed for employeeId={}: {}",
                    employeeId, ex.getMessage());
            throw upstream("putBankDetails", ex);
        }
    }

    /**
     * Current basic salary. Request thread only: the caller's identity is
     * forwarded by the commons-web interceptor, so payroll enforces
     * {@code payroll.read} against the real user. Empty when the employee
     * has no salary on file.
     *
     * @throws ForbiddenError when payroll refuses the caller (403)
     * @throws DependencyUnavailableError when payroll is unreachable or errors
     */
    public Optional<EmployeeSalaryDto> currentSalary(long employeeId) {
        try {
            return Optional.ofNullable(http.get()
                    .uri("/salary/employees/{id}", employeeId)
                    .retrieve()
                    .body(EmployeeSalaryDto.class));
        } catch (RestClientException ex) {
            if (is(ex, HttpStatus.NOT_FOUND)) return Optional.empty();
            throw readFailure("currentSalary", employeeId, ex);
        }
    }

    /** Current effective salary component lines (earnings and deductions). Same error contract as {@link #currentSalary}. */
    public List<EmployeeSalaryComponentDto> currentComponents(long employeeId) {
        try {
            List<EmployeeSalaryComponentDto> lines = http.get()
                    .uri("/employees/{id}/salary-components", employeeId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<EmployeeSalaryComponentDto>>() {});
            return lines == null ? List.of() : lines;
        } catch (RestClientException ex) {
            if (is(ex, HttpStatus.NOT_FOUND)) return List.of();
            throw readFailure("currentComponents", employeeId, ex);
        }
    }

    private static RuntimeException readFailure(String op, long employeeId, RestClientException ex) {
        if (is(ex, HttpStatus.FORBIDDEN)) {
            return new ForbiddenError("Salary details require the payroll.read permission.");
        }
        log.warn("payroll-service {} failed for employeeId={}: {}", op, employeeId, ex.toString());
        return new DependencyUnavailableError("Payroll service is unavailable; salary details could not be loaded.", ex);
    }

    private static boolean is(RestClientException ex, HttpStatus status) {
        return ex instanceof RestClientResponseException r && r.getStatusCode().value() == status.value();
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
                "payroll-service " + op + " failed.", ex);
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

    /** Mirrors payroll-service's ReviseBasicSalaryRequest. */
    public record ReviseBasicSalaryRequest(Long basic, LocalDate effectiveFrom, String revisionReason) {}

    /** Mirrors payroll-service's AddSalaryLineRequest. */
    public record AddSalaryLineRequest(Long componentId, Long amount, LocalDate effectiveFrom, String notes) {}

    /** Mirrors payroll-service's UpsertBankDetailsRequest. */
    public record UpsertBankDetailsRequest(Long bankId, String branch, String accountTitle,
                                           String iban, String accountNumber) {}
}

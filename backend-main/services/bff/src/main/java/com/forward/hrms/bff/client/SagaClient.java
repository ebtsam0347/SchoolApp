package com.forward.hrms.bff.client;

import com.forward.hrms.bff.client.dto.PageView;
import com.forward.hrms.bff.config.DownstreamProperties;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UpstreamError;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Saga rows live in each writing service's own schema, so the BFF reads
 * them through both employee-service and payroll-service. This client
 * holds two RestClient instances — one per upstream — and exposes a
 * shared {@link SagaView} shape so the dashboard can merge results
 * without caring which service the row came from.
 */
@Slf4j
@Component
public class SagaClient {

    private final RestClient employeeHttp;
    private final RestClient payrollHttp;

    public SagaClient(DownstreamProperties props, RestClient.Builder builder) {
        this.employeeHttp = builder.clone().baseUrl(props.employee().baseUrl()).build();
        this.payrollHttp  = builder.clone().baseUrl(props.payroll().baseUrl()).build();
    }

    public PageView<SagaView> employeeSagas(String status, int size) {
        return list(employeeHttp, "/sagas", status, size, "employee-sagas");
    }

    public PageView<SagaView> payrollSagas(String status, int size) {
        return list(payrollHttp, "/payroll/sagas", status, size, "payroll-sagas");
    }

    /** Saga health rollup from employee-service (D-S4). */
    public SagaHealthView employeeSagasHealth() {
        return health(employeeHttp, "/sagas/health", "employee-sagas-health");
    }

    /** Saga health rollup from payroll-service (D-S4). */
    public SagaHealthView payrollSagasHealth() {
        return health(payrollHttp, "/payroll/sagas/health", "payroll-sagas-health");
    }

    private SagaHealthView health(RestClient http, String path, String what) {
        try {
            SagaHealthView body = http.get().uri(path).retrieve().body(SagaHealthView.class);
            return body == null ? SagaHealthView.empty() : body;
        } catch (RestClientException ex) {
            throw upstream(what, ex);
        }
    }

    private PageView<SagaView> list(RestClient http, String path, String status, int size, String what) {
        try {
            PageView<SagaView> body = http.get()
                    .uri(uri -> {
                        var b = uri.path(path).queryParam("size", size);
                        if (status != null) b.queryParam("status", status);
                        return b.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? new PageView<>(List.of(), 0, size, 0L, 0, null) : body;
        } catch (RestClientException ex) {
            throw upstream(what, ex);
        }
    }

    private static UpstreamError upstream(String what, RestClientException ex) {
        log.warn("saga client failed for {}: {}", what, ex.getMessage());
        return new UpstreamError(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                "saga read failed.", ex);
    }

    /**
     * Shared shape across employee + payroll {@code SagaResponse} — both
     * services emit the same fields, so we mirror them once here. Adds
     * {@code service} so the FE can deep-link into the right page.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SagaView(
            long id,
            String sagaName,
            String correlationKey,
            String status,
            String currentStep,
            int stepIndex,
            int attemptCount,
            LocalDateTime nextAttemptAt,
            String lastError,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String createdBy
    ) {}

    /** Mirror of {@code commons-saga} {@code SagaHealthSnapshot}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SagaHealthView(int running, int failed24h, List<PerName> perName) {

        public static SagaHealthView empty() {
            return new SagaHealthView(0, 0, List.of());
        }

        public List<PerName> perNameOrEmpty() {
            return perName == null ? List.of() : perName;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record PerName(String name, String status, List<Integer> failureRate24h) {}
    }
}

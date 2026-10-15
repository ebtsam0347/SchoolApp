package com.forward.hrms.bff.client;

import com.forward.hrms.bff.client.dto.PageView;
import com.forward.hrms.bff.config.DownstreamProperties;
import com.forward.hrms.payroll.dto.response.PayslipDto;
import com.forward.hrms.payroll.dto.response.PdfUrlResponse;
import com.forward.hrms.payroll.dto.response.PayrollRunDto;
import com.forward.hrms.payroll.dto.response.PayrollRunTotalsDto;
import com.forward.hrms.payroll.dto.response.IncrementBatchDto;
import com.forward.hrms.payroll.dto.response.BonusEventDto;
import com.forward.hrms.payroll.dto.response.OffCyclePaymentDto;
import com.forward.hrms.payroll.enums.OffCyclePaymentSource;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryDto;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryComponentDto;
import com.forward.hrms.payroll.dto.response.SalaryComponentDto;
import com.forward.hrms.payroll.dto.response.EmployeePayrollSettingsDto;
import com.forward.hrms.payroll.dto.response.EmployeeBankDetailsDto;
import com.forward.hrms.payroll.dto.response.GratuityDto;
import com.forward.hrms.payroll.dto.response.PermanentAdvanceInboxRow;
import com.forward.hrms.payroll.dto.response.ShortTermAdvanceInboxRow;
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
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Payroll-service slice the BFF needs. One thin method per downstream
 * call; composition (parallel fan-out, employee resolution) lives in
 * the service layer.
 */
@Slf4j
@Component
public class PayrollClient {

    private final RestClient http;

    public PayrollClient(DownstreamProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.payroll().baseUrl()).build();
    }

    // ----- pending-approval inboxes -----

    public PageView<PermanentAdvanceInboxRow> permanentAdvancesPending(int size, String q) {
        return getPaged(uri -> {
                    var b = uri.path("/permanent-advances/pending-approvals").queryParam("size", size);
                    if (q != null) b.queryParam("q", q);
                    return b.build();
                },
                new ParameterizedTypeReference<>() {}, "permanent-advances-pending");
    }

    public PageView<ShortTermAdvanceInboxRow> shortTermAdvancesPending(int size, String q) {
        return getPaged(uri -> {
                    var b = uri.path("/short-term-advance-batches/pending-approvals").queryParam("size", size);
                    if (q != null) b.queryParam("q", q);
                    return b.build();
                },
                new ParameterizedTypeReference<>() {}, "short-term-advances-pending");
    }

    /** Payroll's own configured approval chains (permanent/short-term advances). */
    public List<WorkflowChainDto> approvalWorkflows() {
        return getList(uri -> uri.path("/approval-workflows").build(),
                new ParameterizedTypeReference<>() {}, "approval-workflows");
    }

    /**
     * Caller-scoped approval decisions in a date range (inclusive). Mirrors
     * {@code AttendanceClient.approvalHistory} — pulls one page at the
     * upstream max, used by the cross-service approval-history composite.
     */
    public List<ApprovalDecisionRow> approvalHistory(LocalDate from, LocalDate to, String type, String decision) {
        return getPaged(uri -> {
                    var b = uri.path("/approvals/history")
                            .queryParam("from", from).queryParam("to", to)
                            .queryParam("size", 200);
                    if (type != null) b.queryParam("type", type);
                    if (decision != null) b.queryParam("decision", decision);
                    return b.build();
                },
                new ParameterizedTypeReference<PageView<ApprovalDecisionRow>>() {},
                "approval-history").contentOrEmpty();
    }

    // ----- payslips -----

    public PayslipDto payslip(long id) {
        return get(uri -> uri.path("/payslips/{id}").build(id),
                new ParameterizedTypeReference<>() {}, "payslip");
    }

    public PageView<PayslipDto> payslipsForEmployeeYear(long employeeId, int year,
                                                                     Integer page, Integer size) {
        return getPaged(uri -> {
            var b = uri.path("/payslips/employees/{id}").queryParam("year", year);
            if (page != null) b.queryParam("page", page);
            if (size != null) b.queryParam("size", size);
            return b.build(employeeId);
        }, new ParameterizedTypeReference<>() {}, "payslips-for-employee-year");
    }

    public PageView<PayslipDto> payslipsByStatus(String status,
                                                               Integer page, Integer size) {
        return getPaged(uri -> {
            var b = uri.path("/payslips").queryParam("status", status);
            if (page != null) b.queryParam("page", page);
            if (size != null) b.queryParam("size", size);
            return b.build();
        }, new ParameterizedTypeReference<>() {}, "payslips-by-status");
    }

    /** {@code 404} returns null — no PDF generated yet. */
    public PdfUrlResponse pdfUrlOrNull(long payslipId) {
        try {
            return http.get()
                    .uri(uri -> uri.path("/payslips/{id}/pdf-url").build(payslipId))
                    .retrieve()
                    .body(PdfUrlResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        } catch (RestClientException ex) {
            throw upstream("pdf-url", ex);
        }
    }

    // ----- runs -----

    public PayrollRunDto run(long id) {
        return get(uri -> uri.path("/payroll/runs/{id}").build(id),
                new ParameterizedTypeReference<>() {}, "run");
    }

    /** Lightweight slice of payroll-service's {@code FiscalYearDto}. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record FiscalYearView(String code, LocalDate startDate, LocalDate endDate, String status) {}

    /** Returns null when the upstream returns 404 (no current fiscal year configured). */
    public FiscalYearView currentFiscalYearOrNull() {
        try {
            return http.get()
                    .uri("/fiscal-years/current")
                    .retrieve()
                    .body(FiscalYearView.class);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        } catch (RestClientException ex) {
            throw upstream("fiscal-year-current", ex);
        }
    }

    public List<PayrollRunDto> runsForFiscalYear(String fiscalYearCode) {
        return getList(uri -> uri.path("/payroll/runs").queryParam("fiscalYear", fiscalYearCode).build(),
                new ParameterizedTypeReference<>() {}, "runs-for-fy");
    }

    /**
     * Pre-aggregated totals for a single run, optionally filtered by the
     * same {@code status} / {@code q} predicates as {@link #runPayslips}.
     * Lets the BFF show a filter-aware rollup without paging through
     * every row in the run.
     */
    public PayrollRunTotalsDto runTotals(long runId, String status, String q) {
        return get(uri -> {
            var b = uri.path("/payroll/runs/{id}/totals");
            if (status != null) b.queryParam("status", status);
            if (q != null && !q.isBlank()) b.queryParam("q", q);
            return b.build(runId);
        }, new ParameterizedTypeReference<>() {}, "run-totals");
    }

    public PageView<PayslipDto> runPayslips(long runId, String status, String q,
                                                          Integer page, Integer size,
                                                          Collection<String> sort) {
        return getPaged(uri -> {
            var b = uri.path("/payroll/runs/{id}/payslips");
            if (status != null) b.queryParam("status", status);
            if (q != null && !q.isBlank()) b.queryParam("q", q);
            if (page != null) b.queryParam("page", page);
            if (size != null) b.queryParam("size", size);
            if (sort != null) sort.forEach(s -> b.queryParam("sort", s));
            return b.build(runId);
        }, new ParameterizedTypeReference<>() {}, "run-payslips");
    }

    // ----- increments -----

    public IncrementBatchDto incrementBatch(long id) {
        return get(uri -> uri.path("/salary/increment-batches/{id}").build(id),
                new ParameterizedTypeReference<>() {}, "increment-batch");
    }

    // ----- bonuses -----

    public BonusEventDto bonusEvent(long id) {
        return get(uri -> uri.path("/bonuses/events/{id}").build(id),
                new ParameterizedTypeReference<>() {}, "bonus-event");
    }

    // ----- off-cycle payments -----

    public PageView<OffCyclePaymentDto> offCyclePayments(Long employeeId,
                                                                      LocalDate from, LocalDate to,
                                                                      OffCyclePaymentSource source,
                                                                      Integer page, Integer size,
                                                                      Collection<String> sort) {
        return getPaged(uri -> {
            var b = uri.path("/off-cycle-payments");
            if (employeeId != null) b.queryParam("employeeId", employeeId);
            if (from != null) b.queryParam("from", from);
            if (to != null) b.queryParam("to", to);
            if (source != null) b.queryParam("source", source.name());
            if (page != null) b.queryParam("page", page);
            if (size != null) b.queryParam("size", size);
            if (sort != null) sort.forEach(s -> b.queryParam("sort", s));
            return b.build();
        }, new ParameterizedTypeReference<>() {}, "off-cycle-payments");
    }

    public List<OffCyclePaymentDto> offCyclePaymentsForEmployee(long employeeId,
                                                                              LocalDate from,
                                                                              LocalDate to) {
        return getList(uri -> {
            var b = uri.path("/employees/{id}/off-cycle-payments");
            if (from != null) b.queryParam("from", from);
            if (to != null) b.queryParam("to", to);
            return b.build(employeeId);
        }, new ParameterizedTypeReference<>() {}, "employee-off-cycle-payments");
    }

    // ----- salary tab reads -----

    public EmployeeSalaryDto currentSalaryOrNull(long employeeId) {
        try {
            return http.get()
                    .uri(uri -> uri.path("/salary/employees/{id}").build(employeeId))
                    .retrieve()
                    .body(EmployeeSalaryDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        } catch (RestClientException ex) {
            throw upstream("current-salary", ex);
        }
    }

    public List<EmployeeSalaryDto> salaryHistory(long employeeId) {
        return getList(uri -> uri.path("/salary/employees/{id}/history").build(employeeId),
                new ParameterizedTypeReference<>() {}, "salary-history");
    }

    public List<EmployeeSalaryComponentDto> salaryComponentsForEmployee(long employeeId) {
        return getList(uri -> uri.path("/employees/{id}/salary-components").build(employeeId),
                new ParameterizedTypeReference<>() {}, "employee-salary-components");
    }

    public List<SalaryComponentDto> componentCatalog(boolean activeOnly) {
        return getList(uri -> uri.path("/salary/components")
                        .queryParam("active", activeOnly).build(),
                new ParameterizedTypeReference<>() {}, "component-catalog");
    }

    public EmployeePayrollSettingsDto payrollSettingsOrNull(long employeeId) {
        try {
            return http.get()
                    .uri(uri -> uri.path("/employees/{id}/payroll-settings").build(employeeId))
                    .retrieve()
                    .body(EmployeePayrollSettingsDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        } catch (RestClientException ex) {
            throw upstream("payroll-settings", ex);
        }
    }

    public EmployeeBankDetailsDto bankDetailsOrNull(long employeeId) {
        try {
            return http.get()
                    .uri(uri -> uri.path("/employees/{id}/bank-details").build(employeeId))
                    .retrieve()
                    .body(EmployeeBankDetailsDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        } catch (RestClientException ex) {
            throw upstream("bank-details", ex);
        }
    }

    public GratuityDto gratuity(long employeeId) {
        return get(uri -> uri.path("/gratuity/employees/{id}").build(employeeId),
                new ParameterizedTypeReference<>() {}, "gratuity");
    }

    /** {@code 404} returns null — no payroll settings, so no gratuity yet. */
    public GratuityDto gratuityOrNull(long employeeId) {
        try {
            return http.get()
                    .uri(uri -> uri.path("/gratuity/employees/{id}").build(employeeId))
                    .retrieve()
                    .body(GratuityDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        } catch (RestClientException ex) {
            throw upstream("gratuity", ex);
        }
    }

    // ----- low-level helpers -----

    private <T> T get(Function<org.springframework.web.util.UriBuilder, java.net.URI> uri,
                      ParameterizedTypeReference<T> type, String what) {
        try {
            return http.get().uri(uri).retrieve().body(type);
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

    private <T> PageView<T> getPaged(Function<org.springframework.web.util.UriBuilder, java.net.URI> uri,
                                     ParameterizedTypeReference<PageView<T>> type, String what) {
        try {
            PageView<T> body = http.get().uri(uri).retrieve().body(type);
            return body == null ? new PageView<>(List.of(), 0, 20, 0L, 0, null) : body;
        } catch (RestClientException ex) {
            throw upstream(what, ex);
        }
    }

    private static UpstreamError upstream(String what, RestClientException ex) {
        log.warn("payroll-service call failed for {}: {}", what, ex.getMessage());
        return new UpstreamError(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                "payroll-service request failed.", ex);
    }
}

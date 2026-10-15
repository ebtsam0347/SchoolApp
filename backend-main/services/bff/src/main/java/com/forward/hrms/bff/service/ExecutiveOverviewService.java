package com.forward.hrms.bff.service;

import com.forward.hrms.bff.config.DownstreamProperties;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One-call composite for the management dashboard's top-of-page
 * executive overview. Fans out to the four service-side analytics
 * endpoints in series (parallelism would buy ms at a tiny dataset
 * scale; not worth the cost) and pulls the headline metric from each.
 *
 * <p>If a downstream is unavailable the field falls back to {@code null}
 * — the dashboard renders "—" rather than 502'ing the whole card.
 */
@Slf4j
@Service
public class ExecutiveOverviewService {

    private final RestClient employee;
    private final RestClient attendance;
    private final RestClient payroll;

    public ExecutiveOverviewService(DownstreamProperties props, RestClient.Builder builder) {
        this.employee   = builder.baseUrl(props.employee().baseUrl()).build();
        this.attendance = builder.baseUrl(props.attendance().baseUrl()).build();
        this.payroll    = builder.baseUrl(props.payroll().baseUrl()).build();
    }

    public ExecutiveOverviewDto overview() {
        return new ExecutiveOverviewDto(
                fetchHeadcount(),
                fetchAttritionRollingPct(),
                fetchLatestOnTimePct(),
                fetchLatestAbsenteeismPct(),
                fetchLatestOtHours(),
                fetchLatestPayrollCost());
    }

    private Long fetchHeadcount() {
        try {
            Map<String, Object> body = employee.get()
                    .uri("/analytics/employees/headcount-trend?months=1")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (body == null) return null;
            List<Map<String, Object>> series = (List<Map<String, Object>>) body.get("series");
            if (series == null || series.isEmpty()) return null;
            Object hc = series.get(series.size() - 1).get("endHeadcount");
            return hc instanceof Number n ? n.longValue() : null;
        } catch (Exception ex) {
            log.debug("headcount fetch failed: {}", ex.toString());
            return null;
        }
    }

    private BigDecimal fetchAttritionRollingPct() {
        try {
            Map<String, Object> body = employee.get()
                    .uri("/analytics/employees/attrition?months=12")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (body == null) return null;
            return asBigDecimal(body.get("rollingTwelveMonthPct"));
        } catch (Exception ex) {
            log.debug("attrition fetch failed: {}", ex.toString());
            return null;
        }
    }

    private BigDecimal fetchLatestOnTimePct() {
        return latestPct("/analytics/attendance/on-time-pct?months=1", attendance);
    }

    private BigDecimal fetchLatestAbsenteeismPct() {
        return latestPct("/analytics/attendance/absenteeism-pct?months=1", attendance);
    }

    private BigDecimal fetchLatestOtHours() {
        try {
            Map<String, Object> body = attendance.get()
                    .uri("/analytics/attendance/ot-trend?months=1")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (body == null) return null;
            List<Map<String, Object>> series = (List<Map<String, Object>>) body.get("series");
            if (series == null || series.isEmpty()) return null;
            return asBigDecimal(series.get(series.size() - 1).get("otHours"));
        } catch (Exception ex) {
            log.debug("OT trend fetch failed: {}", ex.toString());
            return null;
        }
    }

    private BigDecimal fetchLatestPayrollCost() {
        try {
            Map<String, Object> body = payroll.get()
                    .uri("/analytics/payroll/cost-trend?months=1")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (body == null) return null;
            List<Map<String, Object>> series = (List<Map<String, Object>>) body.get("series");
            if (series == null || series.isEmpty()) return null;
            return asBigDecimal(series.get(series.size() - 1).get("totalCost"));
        } catch (Exception ex) {
            log.debug("payroll cost fetch failed: {}", ex.toString());
            return null;
        }
    }

    private BigDecimal latestPct(String path, RestClient client) {
        try {
            Map<String, Object> body = client.get()
                    .uri(path)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (body == null) return null;
            List<Map<String, Object>> series = (List<Map<String, Object>>) body.get("series");
            if (series == null || series.isEmpty()) return null;
            return asBigDecimal(series.get(series.size() - 1).get("pct"));
        } catch (Exception ex) {
            log.debug("pct fetch {} failed: {}", path, ex.toString());
            return null;
        }
    }

    private static BigDecimal asBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    public record ExecutiveOverviewDto(
            Long headcount,
            BigDecimal attritionRolling12mPct,
            BigDecimal latestMonthOnTimePct,
            BigDecimal latestMonthAbsenteeismPct,
            BigDecimal latestMonthOtHours,
            BigDecimal latestMonthPayrollCost) {}
}

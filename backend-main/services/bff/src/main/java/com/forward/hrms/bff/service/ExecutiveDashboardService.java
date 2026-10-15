package com.forward.hrms.bff.service;

import com.forward.hrms.bff.config.DownstreamProperties;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Cross-service data gatherer for the printable executive dashboard
 * PDF. Fans out to the same per-service analytics endpoints the FE
 * dashboard cards consume, then folds the JSON into row-shaped maps
 * Jasper can render directly.
 *
 * <p>Same fail-soft contract as {@link ExecutiveOverviewService}: any
 * downstream that 4xx/5xx's its slice contributes empty rows / nulls
 * rather than aborting the whole render. The PDF still prints with the
 * remaining sections — readers see "—" where data was unavailable.
 */
@Slf4j
@Service
public class ExecutiveDashboardService {

    private static final int TREND_MONTHS = 12;
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");

    private final RestClient employee;
    private final RestClient attendance;
    private final RestClient payroll;

    public ExecutiveDashboardService(DownstreamProperties props, RestClient.Builder builder) {
        this.employee   = builder.baseUrl(props.employee().baseUrl()).build();
        this.attendance = builder.baseUrl(props.attendance().baseUrl()).build();
        this.payroll    = builder.baseUrl(props.payroll().baseUrl()).build();
    }

    public ExecutiveDashboardData gather() {
        Map<String, Object> hcTrend       = getJson(employee,   "/analytics/employees/headcount-trend?months=" + TREND_MONTHS);
        Map<String, Object> attrition     = getJson(employee,   "/analytics/employees/attrition?months=" + TREND_MONTHS);
        Map<String, Object> onTime        = getJson(attendance, "/analytics/attendance/on-time-pct?months=" + TREND_MONTHS);
        Map<String, Object> absenteeism   = getJson(attendance, "/analytics/attendance/absenteeism-pct?months=" + TREND_MONTHS);
        Map<String, Object> otTrend       = getJson(attendance, "/analytics/attendance/ot-trend?months=" + TREND_MONTHS);
        Map<String, Object> costTrend     = getJson(payroll,    "/analytics/payroll/cost-trend?months=" + TREND_MONTHS);
        Map<String, Object> distribution  = getJson(payroll,    "/analytics/payroll/salary-distribution");

        Headline headline = new Headline(
                latestEndHeadcount(hcTrend),
                rollingTwelveMonthPct(attrition),
                latestPct(onTime),
                latestPct(absenteeism),
                latestField(otTrend, "otHours"),
                latestField(costTrend, "totalCost"));

        return new ExecutiveDashboardData(
                LocalDate.now(),
                headline,
                buildHeadcountRows(hcTrend),
                buildAttritionRows(attrition),
                buildOnTimeRows(onTime, absenteeism),
                buildCostRows(costTrend),
                buildDistributionRows(distribution));
    }

    private List<Map<String, Object>> buildHeadcountRows(Map<String, Object> hcTrend) {
        List<Map<String, Object>> series = seriesOf(hcTrend);
        List<Map<String, Object>> rows = new ArrayList<>();
        int serial = 1;
        for (Map<String, Object> m : series) {
            rows.add(row(
                    "serial",       serial++,
                    "month",        formatMonth(m),
                    "joiners",      asInt(m.get("joiners")),
                    "leavers",      asInt(m.get("leavers")),
                    "endHeadcount", asInt(m.get("endHeadcount"))));
        }
        return rows;
    }

    private List<Map<String, Object>> buildAttritionRows(Map<String, Object> attrition) {
        List<Map<String, Object>> series = seriesOf(attrition);
        List<Map<String, Object>> rows = new ArrayList<>();
        int serial = 1;
        for (Map<String, Object> m : series) {
            rows.add(row(
                    "serial",   serial++,
                    "month",    formatMonth(m),
                    "leavers",  asInt(m.get("leavers")),
                    "ratePct",  formatPct(asBigDecimal(m.get("ratePct")))));
        }
        return rows;
    }

    private List<Map<String, Object>> buildOnTimeRows(Map<String, Object> onTime, Map<String, Object> absenteeism) {
        List<Map<String, Object>> onTimeSeries = seriesOf(onTime);
        List<Map<String, Object>> absSeries    = seriesOf(absenteeism);
        Map<String, BigDecimal> absByMonth = new HashMap<>();
        for (Map<String, Object> m : absSeries) {
            absByMonth.put(monthKey(m), asBigDecimal(m.get("pct")));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int serial = 1;
        for (Map<String, Object> m : onTimeSeries) {
            String key = monthKey(m);
            rows.add(row(
                    "serial",         serial++,
                    "month",          formatMonth(m),
                    "onTimePct",      formatPct(asBigDecimal(m.get("pct"))),
                    "absenteeismPct", formatPct(absByMonth.get(key))));
        }
        return rows;
    }

    private List<Map<String, Object>> buildCostRows(Map<String, Object> costTrend) {
        List<Map<String, Object>> series = seriesOf(costTrend);
        List<Map<String, Object>> rows = new ArrayList<>();
        int serial = 1;
        for (Map<String, Object> m : series) {
            rows.add(row(
                    "serial",       serial++,
                    "month",        formatMonth(m),
                    "gross",        formatMoney(asBigDecimal(m.get("gross"))),
                    "totalCost",    formatMoney(asBigDecimal(m.get("totalCost"))),
                    "costPerHead",  formatMoney(asBigDecimal(m.get("costPerHead")))));
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildDistributionRows(Map<String, Object> distribution) {
        if (distribution == null) return List.of();
        Map<String, Object> p = distribution.get("percentiles") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : distribution;
        List<Map<String, Object>> rows = new ArrayList<>();
        addDistributionRow(rows, "Active employees", asInt(p.get("count")));
        addDistributionRow(rows, "Min basic",        formatMoney(asBigDecimal(p.get("min"))));
        addDistributionRow(rows, "P25 basic",        formatMoney(asBigDecimal(p.get("p25"))));
        addDistributionRow(rows, "Median basic",     formatMoney(asBigDecimal(p.get("median"))));
        addDistributionRow(rows, "P75 basic",        formatMoney(asBigDecimal(p.get("p75"))));
        addDistributionRow(rows, "Max basic",        formatMoney(asBigDecimal(p.get("max"))));
        return rows;
    }

    private static void addDistributionRow(List<Map<String, Object>> rows, String label, Object value) {
        rows.add(row("metric", label, "value", value == null ? "—" : value.toString()));
    }

    private Map<String, Object> getJson(RestClient client, String uri) {
        try {
            return client.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            log.debug("dashboard fetch {} failed: {}", uri, ex.toString());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> seriesOf(Map<String, Object> body) {
        if (body == null) return List.of();
        Object s = body.get("series");
        return s instanceof List<?> ? (List<Map<String, Object>>) s : List.of();
    }

    private static String monthKey(Map<String, Object> row) {
        Object v = row.get("month");
        return v == null ? "" : v.toString();
    }

    private static String formatMonth(Map<String, Object> row) {
        Object v = row.get("month");
        if (v == null) return "—";
        try {
            return MONTH_LABEL.format(LocalDate.parse(v.toString() + "-01"));
        } catch (RuntimeException ex) {
            return v.toString();
        }
    }

    private static Long latestEndHeadcount(Map<String, Object> hcTrend) {
        List<Map<String, Object>> series = seriesOf(hcTrend);
        if (series.isEmpty()) return null;
        Object hc = series.get(series.size() - 1).get("endHeadcount");
        return hc instanceof Number n ? n.longValue() : null;
    }

    private static BigDecimal rollingTwelveMonthPct(Map<String, Object> attrition) {
        if (attrition == null) return null;
        return asBigDecimal(attrition.get("rollingTwelveMonthPct"));
    }

    private static BigDecimal latestPct(Map<String, Object> body) {
        List<Map<String, Object>> series = seriesOf(body);
        if (series.isEmpty()) return null;
        return asBigDecimal(series.get(series.size() - 1).get("pct"));
    }

    private static BigDecimal latestField(Map<String, Object> body, String field) {
        List<Map<String, Object>> series = seriesOf(body);
        if (series.isEmpty()) return null;
        return asBigDecimal(series.get(series.size() - 1).get(field));
    }

    static BigDecimal asBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    static String formatMoney(BigDecimal v) {
        if (v == null) return "—";
        return v.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    static String formatPct(BigDecimal v) {
        if (v == null) return "—";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static Map<String, Object> row(Object... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("expected key/value pairs");
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(Objects.toString(kv[i]), kv[i + 1]);
        }
        return m;
    }

    public record ExecutiveDashboardData(
            LocalDate generatedOn,
            Headline headline,
            List<Map<String, Object>> headcountRows,
            List<Map<String, Object>> attritionRows,
            List<Map<String, Object>> punctualityRows,
            List<Map<String, Object>> costRows,
            List<Map<String, Object>> distributionRows) {

        public ExecutiveDashboardData {
            headcountRows    = headcountRows    == null ? List.of() : Collections.unmodifiableList(headcountRows);
            attritionRows    = attritionRows    == null ? List.of() : Collections.unmodifiableList(attritionRows);
            punctualityRows  = punctualityRows  == null ? List.of() : Collections.unmodifiableList(punctualityRows);
            costRows         = costRows         == null ? List.of() : Collections.unmodifiableList(costRows);
            distributionRows = distributionRows == null ? List.of() : Collections.unmodifiableList(distributionRows);
        }
    }

    public record Headline(
            Long headcount,
            BigDecimal attritionRolling12mPct,
            BigDecimal latestMonthOnTimePct,
            BigDecimal latestMonthAbsenteeismPct,
            BigDecimal latestMonthOtHours,
            BigDecimal latestMonthPayrollCost) {}
}

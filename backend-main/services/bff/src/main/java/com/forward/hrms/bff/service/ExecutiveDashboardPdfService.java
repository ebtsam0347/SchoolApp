package com.forward.hrms.bff.service;

import com.forward.hrms.commons.reports.JasperRenderer;

import lombok.RequiredArgsConstructor;

import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders {@link ExecutiveDashboardService.ExecutiveDashboardData} into
 * a single-page printable PDF via the {@code executive-dashboard.jrxml}
 * template. Pure renderer — the gather step lives upstream so the
 * executive-overview JSON endpoint and this PDF stay aligned.
 */
@Service
@RequiredArgsConstructor
public class ExecutiveDashboardPdfService {

    private static final String COMPANY_NAME = "The Spirit of Islam School";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ExecutiveDashboardService gatherer;
    private final JasperRenderer jasper;

    public byte[] render() {
        ExecutiveDashboardService.ExecutiveDashboardData data = gatherer.gather();

        Map<String, Object> params = new HashMap<>();
        params.put("companyName",     COMPANY_NAME);
        params.put("generatedOn",     STAMP.format(data.generatedOn()));
        params.put("kpiHeadcount",    formatHeadcount(data.headline().headcount()));
        params.put("kpiAttrition",    formatPct(data.headline().attritionRolling12mPct()));
        params.put("kpiOnTime",       formatPct(data.headline().latestMonthOnTimePct()));
        params.put("kpiAbsenteeism",  formatPct(data.headline().latestMonthAbsenteeismPct()));
        params.put("kpiOtHours",      formatHours(data.headline().latestMonthOtHours()));
        params.put("kpiPayrollCost",  formatMoney(data.headline().latestMonthPayrollCost()));

        params.put("headcountRows",    nullIfEmpty(data.headcountRows()));
        params.put("attritionRows",    nullIfEmpty(data.attritionRows()));
        params.put("punctualityRows",  nullIfEmpty(data.punctualityRows()));
        params.put("costRows",         nullIfEmpty(data.costRows()));
        params.put("distributionRows", nullIfEmpty(data.distributionRows()));

        return jasper.renderPdf("/jasper/executive-dashboard.jrxml",
                params, new JRMapCollectionDataSource(List.of(Map.of("_", "_"))));
    }

    /**
     * Jasper's list component handles empty subdatasets (renders nothing).
     * We still pass an empty list so the parameter type stays correct;
     * leaving null would NPE inside the dataSourceExpression's
     * {@code new JRMapCollectionDataSource($P{...})}.
     */
    private static List<Map<String, Object>> nullIfEmpty(List<Map<String, Object>> rows) {
        return rows == null ? List.of() : rows;
    }

    private static String formatHeadcount(Long v) {
        return v == null ? "—" : v.toString();
    }

    private static String formatHours(BigDecimal v) {
        if (v == null) return "—";
        return v.setScale(1, RoundingMode.HALF_UP).toPlainString() + " h";
    }

    private static String formatPct(BigDecimal v) {
        if (v == null) return "—";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String formatMoney(BigDecimal v) {
        if (v == null) return "—";
        return "Rs " + v.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}

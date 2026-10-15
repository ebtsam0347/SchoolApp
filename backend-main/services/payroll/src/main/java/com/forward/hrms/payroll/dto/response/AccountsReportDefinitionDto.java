package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.AccountsReport;
import com.forward.hrms.payroll.enums.AccountsReportGroup;
import com.forward.hrms.payroll.enums.AccountsReportOutput;
import com.forward.hrms.payroll.enums.AccountsReportParam;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(name = "AccountsReportDefinition",
        description = "Registry entry: drives the FE filter panel and table for one accounts report.")
public record AccountsReportDefinitionDto(
        @Schema(description = "Legacy report id.", example = "C2") String id,
        @Schema(description = "Report title.", example = "Permanent Advance Balance") String title,
        @Schema(description = "Menu group.") AccountsReportGroup group,
        @Schema(description = "Menu group label.", example = "Permanent Advance") String groupLabel,
        @Schema(description = "Viewer the FE opens.") AccountsReportOutput output,
        @Schema(description = "True when `POST /{id}/document` prints a PDF for this report.") boolean printable,
        @Schema(description = "Accepted filters; anything else is ignored.") List<AccountsReportParamDto> params,
        @Schema(description = "Columns in display order, Sr No first.") List<AccountsReportColumnDto> columns
) {

    /** {@code columns}: the report's columns without Sr No, as its data source resolves them. */
    public static AccountsReportDefinitionDto of(AccountsReport r, List<AccountsReportColumnDto> columns) {
        List<AccountsReportParamDto> params = new ArrayList<>();
        for (AccountsReportParam p : AccountsReportParam.values()) {
            if (r.accepts(p)) params.add(AccountsReportParamDto.of(p, r.required().contains(p)));
        }
        return new AccountsReportDefinitionDto(r.id(), r.title(), r.group(), r.group().label(), r.output(),
                r.hasDocument(),
                params, withSrNo(r, columns));
    }

    /** Ledgers are one employee in date order — no serial column. */
    public static List<AccountsReportColumnDto> withSrNo(AccountsReport r, List<AccountsReportColumnDto> columns) {
        if (r.output() == AccountsReportOutput.LEDGER) return columns;
        List<AccountsReportColumnDto> cols = new ArrayList<>(columns.size() + 1);
        cols.add(AccountsReportColumnDto.srNo());
        cols.addAll(columns);
        return cols;
    }
}

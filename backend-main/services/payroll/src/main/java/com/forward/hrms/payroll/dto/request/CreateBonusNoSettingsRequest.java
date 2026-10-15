package com.forward.hrms.payroll.dto.request;

import com.forward.hrms.payroll.enums.BonusNoSettingsStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "CreateBonusNoSettingsRequest")
public record CreateBonusNoSettingsRequest(
        @Schema(
                description = "Fiscal year code in the format YYYY-YY. Example: 2026-27",
                example = "2026-27"
        )
        String fiscalYearCode,

        @Schema(
                description = "Bonus number. Must be between 1 and 4.",
                example = "1"
        )
        Integer bonusNo,

        @Schema(
                description = "Percentage of salary applicable for the bonus. Defaults to 100%.",
                example = "100",
                defaultValue = "100",
                minimum = "1"
        )
        BigDecimal percentageOfSalary
) {}

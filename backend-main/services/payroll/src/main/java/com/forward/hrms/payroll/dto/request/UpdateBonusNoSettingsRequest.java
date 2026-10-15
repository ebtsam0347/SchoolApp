package com.forward.hrms.payroll.dto.request;

import com.forward.hrms.payroll.enums.BonusNoSettingsStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "UpdateBonusNoSettingsRequest")
public record UpdateBonusNoSettingsRequest(
        @Schema(
                description = "Percentage of salary applicable for the bonus. Defaults to 100%.",
                example = "100",
                defaultValue = "100",
                minimum = "1"
        )
        BigDecimal percentageOfSalary
) {}

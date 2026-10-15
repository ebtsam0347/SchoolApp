package com.forward.hrms.payroll.dto.request;

import com.forward.hrms.payroll.enums.ForcedTerminationDeductionPercentage;
import com.forward.hrms.payroll.enums.NoticePayPercentage;
import com.forward.hrms.payroll.enums.SettlementAdjustmentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateFinalSettlementRequest(

        @NotNull
        Long employeeId,

        @NotNull
        LocalDate lastWorkingDay,

        @NotNull
        SettlementAdjustmentType settlementAdjustmentType,

        NoticePayPercentage noticePayPercentage,

        ForcedTerminationDeductionPercentage forcedDeductionPercentage,

        /**
         * Monthly basic salaries granted as bonus per full fiscal year, as
         * picked by HR on the termination screen. Must be the same rate HR
         * saw in the preview, otherwise the submitted settlement's bonus
         * will not match what was verified. Absent means one basic per year.
         */
        @DecimalMin("0")
        BigDecimal bonusRate,

        String remarks

) {}

package com.forward.hrms.payroll.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CancelSettlementRequest(

        @NotBlank
        String reason

) {}
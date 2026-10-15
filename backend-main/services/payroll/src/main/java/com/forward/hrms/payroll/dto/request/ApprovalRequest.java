package com.forward.hrms.payroll.dto.request;

import jakarta.validation.constraints.Size;

public record ApprovalRequest(

        @Size(max = 500)
        String note

) {}

package com.forward.hrms.payroll.enums;

import com.forward.hrms.commons.core.error.ErrorCode;

public enum AllowanceErrorCode implements ErrorCode {
    INVALID_PERIOD,
    ALLOWANCE_RUN_NOT_FOUND,
    ALLOWANCE_RUN_DUPLICATE,
    ALLOWANCE_RUN_INVALID_STATE,
    ALLOWANCE_NOT_FOUND,
    ALLOWANCE_INVALID_STATE,
}

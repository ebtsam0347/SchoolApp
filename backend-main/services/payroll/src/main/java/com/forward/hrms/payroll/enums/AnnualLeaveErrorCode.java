package com.forward.hrms.payroll.enums;

import com.forward.hrms.commons.core.error.ErrorCode;

public enum AnnualLeaveErrorCode implements ErrorCode{
    ANNUAL_LEAVE_NOT_FOUND,
    ANNUAL_LEAVE_INVALID_STATE,
    ANNUAL_LEAVE_DUPLICATE,
    ANNUAL_LEAVE_ELIGIBILITY_NOT_FOUND,
    ANNUAL_LEAVE_ELIGIBILITY_INVALID_STATE,
    ANNUAL_LEAVE_ELIGIBILITY_DUPLICATE
}

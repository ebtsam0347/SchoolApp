package com.forward.hrms.commons.core.error;

public enum CommonErrorCode implements ErrorCode {
    VALIDATION_FAILED,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    UNPROCESSABLE,
    IDEMPOTENCY_REPLAY,
    RATE_LIMITED,
    INTERNAL_ERROR,
    DEPENDENCY_UNAVAILABLE
}

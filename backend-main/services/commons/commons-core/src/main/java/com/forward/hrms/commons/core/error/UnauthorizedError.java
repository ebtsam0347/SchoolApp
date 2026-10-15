package com.forward.hrms.commons.core.error;

public class UnauthorizedError extends BusinessError {
    public UnauthorizedError(String detail) {
        super(401, CommonErrorCode.UNAUTHENTICATED, detail);
    }
    public UnauthorizedError(ErrorCode code, String detail) {
        super(401, code, detail);
    }
}

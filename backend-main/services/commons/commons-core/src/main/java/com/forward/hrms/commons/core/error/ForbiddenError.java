package com.forward.hrms.commons.core.error;

public class ForbiddenError extends BusinessError {
    public ForbiddenError(String detail) {
        super(403, CommonErrorCode.FORBIDDEN, detail);
    }
    public ForbiddenError(ErrorCode code, String detail) {
        super(403, code, detail);
    }
}

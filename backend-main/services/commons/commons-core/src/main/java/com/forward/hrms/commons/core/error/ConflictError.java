package com.forward.hrms.commons.core.error;

import java.util.Map;

public class ConflictError extends BusinessError {
    public ConflictError(ErrorCode code, String detail) {
        super(409, code, detail);
    }
    public ConflictError(ErrorCode code, String detail, Map<String, Object> extensions) {
        super(409, code, detail, extensions);
    }
}

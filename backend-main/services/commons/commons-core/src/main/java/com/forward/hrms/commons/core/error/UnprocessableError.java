package com.forward.hrms.commons.core.error;

import java.util.Map;

public class UnprocessableError extends BusinessError {
    public UnprocessableError(ErrorCode code, String detail) {
        super(422, code, detail);
    }
    public UnprocessableError(ErrorCode code, String detail, Map<String, Object> extensions) {
        super(422, code, detail, extensions);
    }
}

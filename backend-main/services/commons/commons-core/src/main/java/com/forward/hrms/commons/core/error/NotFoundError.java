package com.forward.hrms.commons.core.error;

import java.util.Map;

public class NotFoundError extends BusinessError {
    public NotFoundError(ErrorCode code, String detail) {
        super(404, code, detail);
    }
    public NotFoundError(ErrorCode code, String detail, Map<String, Object> extensions) {
        super(404, code, detail, extensions);
    }
}

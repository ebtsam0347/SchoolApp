package com.forward.hrms.employee.client;

import com.forward.hrms.commons.core.error.BusinessError;
import com.forward.hrms.commons.core.error.CommonErrorCode;

import java.util.Map;

/**
 * 503 — a downstream service this request cannot be answered without is
 * unreachable or erroring. Unlike {@code UpstreamError} (502) this tells the
 * caller the request itself was fine and is worth retrying later.
 */
public class DependencyUnavailableError extends BusinessError {

    public DependencyUnavailableError(String detail, Throwable cause) {
        super(503, CommonErrorCode.DEPENDENCY_UNAVAILABLE, detail, Map.of(), cause);
    }
}

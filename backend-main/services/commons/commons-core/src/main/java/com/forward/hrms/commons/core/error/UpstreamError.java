package com.forward.hrms.commons.core.error;

import java.util.Map;

/**
 * 502 Bad Gateway — used when this service depends on an upstream HTTP
 * service (e.g. auth → audit) and the upstream call fails. Wrap the
 * underlying exception via {@link RuntimeException#getCause()} preserves
 * the stacktrace.
 */
public class UpstreamError extends BusinessError {

    public UpstreamError(ErrorCode code, String detail, Throwable cause) {
        super(502, code, detail, Map.of(), cause);
    }
}

package com.forward.hrms.commons.core.error;

import java.util.Map;

public abstract class BusinessError extends RuntimeException {

    private final int status;
    private final ErrorCode code;
    private final Map<String, Object> extensions;

    protected BusinessError(int status, ErrorCode code, String detail) {
        this(status, code, detail, Map.of(), null);
    }

    protected BusinessError(int status, ErrorCode code, String detail, Map<String, Object> extensions) {
        this(status, code, detail, extensions, null);
    }

    protected BusinessError(int status, ErrorCode code, String detail, Map<String, Object> extensions, Throwable cause) {
        super(detail, cause);
        this.status = status;
        this.code = code;
        this.extensions = extensions == null ? Map.of() : Map.copyOf(extensions);
    }

    public int status() { return status; }
    public ErrorCode code() { return code; }
    public Map<String, Object> extensions() { return extensions; }
}

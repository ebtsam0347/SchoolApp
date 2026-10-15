package com.forward.hrms.commons.core.error;

import java.util.List;

public class ValidationError extends BusinessError {

    private final List<Violation> violations;

    public ValidationError(String detail, List<Violation> violations) {
        super(400, CommonErrorCode.VALIDATION_FAILED, detail);
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public ValidationError(ErrorCode code, String detail, List<Violation> violations) {
        super(400, code, detail);
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public List<Violation> violations() { return violations; }
}

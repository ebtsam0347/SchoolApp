package com.forward.hrms.audit.enums;

import com.forward.hrms.commons.core.error.ErrorCode;

public enum AuditErrorCode implements ErrorCode {
    REPLAY_FAILED,
    REPLAY_TOPIC_UNKNOWN,
    REPLAY_TOKEN_REQUIRED,
    REPLAY_TOKEN_INVALID,
    EVENT_NOT_FOUND,
    /** /search window must be ≤ 1 year — closes the brownout vector. */
    SEARCH_WINDOW_TOO_WIDE,
    DEAD_LETTER_NOT_FOUND
}

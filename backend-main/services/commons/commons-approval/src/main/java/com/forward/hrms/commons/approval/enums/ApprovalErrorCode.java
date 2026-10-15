package com.forward.hrms.commons.approval.enums;

import com.forward.hrms.commons.core.error.ErrorCode;

/**
 * Default error codes used by the approval engine when the consuming
 * service does not supply service-specific codes via autoconfig.
 * Services that want their own error code enum pass it into the
 * {@link com.forward.hrms.commons.approval.autoconfig.ApprovalAutoConfiguration}
 * bean factory methods.
 */
public enum ApprovalErrorCode implements ErrorCode {
    WORKFLOW_NOT_CONFIGURED,
    WORKFLOW_STAGE_INVALID,
    NO_PENDING_STAGE,
    NOT_AUTHORIZED,
    DELEGATION_NOT_FOUND,
    DELEGATION_OVERLAP,
    DELEGATION_INVALID
}

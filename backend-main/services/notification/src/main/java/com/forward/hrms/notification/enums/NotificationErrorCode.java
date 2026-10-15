package com.forward.hrms.notification.enums;

import com.forward.hrms.commons.core.error.ErrorCode;

public enum NotificationErrorCode implements ErrorCode {
    NOTIFICATION_NOT_FOUND,
    NOTIFICATION_NOT_OWNED,
    TEMPLATE_NOT_FOUND,
    TEMPLATE_TYPE_TAKEN,
    BROADCAST_RECIPIENT_LIMIT_EXCEEDED,
    BROADCAST_NO_RECIPIENTS,
    PUSH_NOT_CONFIGURED,
    PUSH_ENDPOINT_NOT_ALLOWED,
    PUSH_KEYS_INVALID
}

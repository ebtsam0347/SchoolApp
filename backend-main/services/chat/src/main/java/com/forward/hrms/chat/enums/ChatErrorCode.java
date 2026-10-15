package com.forward.hrms.chat.enums;

import com.forward.hrms.commons.core.error.ErrorCode;

public enum ChatErrorCode implements ErrorCode {
    CONVERSATION_NOT_FOUND,
    NOT_A_MEMBER,
    NOT_AN_OWNER,
    DM_OTHER_USER_REQUIRED,
    DM_SELF_NOT_ALLOWED,
    GROUP_TITLE_REQUIRED,
    GROUP_MEMBERS_REQUIRED,
    GROUP_MEMBER_CAP_EXCEEDED,
    BROADCAST_NOT_INITIALIZED,
    MESSAGE_NOT_FOUND,
    MESSAGE_BODY_REQUIRED,
    MESSAGE_EDIT_WINDOW_EXPIRED,
    MESSAGE_NOT_OWNED,
    MEMBER_ALREADY_PRESENT,
    MEMBER_NOT_PRESENT,
    LAST_OWNER_CANNOT_LEAVE,
    INVALID_DELETE_TARGET,
    ATTACHMENT_TYPE_NOT_ALLOWED,
    ATTACHMENT_TOO_LARGE,
    ATTACHMENT_NOT_FOUND,
    ATTACHMENT_KEY_INVALID,
    /** §7.chat — markRead: lastReadMessageId doesn't belong to this conversation. */
    MESSAGE_NOT_IN_CONVERSATION,
    /** §7.chat — message body exceeds the configured cap. */
    MESSAGE_BODY_TOO_LONG,
    /** §7.chat — too many attachments on a single message. */
    ATTACHMENT_COUNT_EXCEEDED
}

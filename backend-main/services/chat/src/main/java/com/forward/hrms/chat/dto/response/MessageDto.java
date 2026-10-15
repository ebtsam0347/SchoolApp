package com.forward.hrms.chat.dto.response;

import com.forward.hrms.chat.entity.Message;
import com.forward.hrms.chat.enums.DeleteReason;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "MessageDto", description = "A message within a conversation.")
public record MessageDto(
        long id,
        long conversationId,
        long senderUserId,
        @Schema(description = "Sender's auth username, captured at send-time from the audit `created_by` " +
                "column. Stable for rendering bubble headers / mention chips without a resolver round-trip.",
                example = "ahmed.khan")
        String senderUsername,
        @Schema(description = "Message body. Empty when soft-deleted (deletedAt is set).") String body,
        @Schema(nullable = true) LocalDateTime editedAt,
        @Schema(nullable = true) LocalDateTime deletedAt,
        @Schema(nullable = true) DeleteReason deleteReason,
        LocalDateTime createdAt,
        @Schema(description = "Attachments uploaded with this message; each carries a short-lived signed download URL.")
        List<AttachmentDto> attachments,
        @Schema(description = "User ids @-mentioned by the sender at send time.")
        List<Long> mentionedUserIds
) {
    public static MessageDto of(Message m, List<AttachmentDto> attachments, List<Long> mentionedUserIds) {
        boolean deleted = m.isDeleted();
        return new MessageDto(
                m.getId(),
                m.getConversationId(),
                m.getSenderUserId(),
                m.getCreatedBy(),
                deleted ? "" : m.getBody(),
                m.getEditedAt(),
                m.getDeletedAt(),
                m.getDeleteReason(),
                m.getCreatedAt(),
                attachments == null ? List.of() : attachments,
                mentionedUserIds == null ? List.of() : mentionedUserIds);
    }

    public static MessageDto of(Message m, List<AttachmentDto> attachments) {
        return of(m, attachments, List.of());
    }

    public static MessageDto of(Message m) {
        return of(m, List.of(), List.of());
    }
}

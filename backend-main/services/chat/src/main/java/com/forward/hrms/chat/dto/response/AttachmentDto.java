package com.forward.hrms.chat.dto.response;

import com.forward.hrms.chat.entity.MessageAttachment;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AttachmentDto", description = "Stored attachment metadata + a short-lived signed download URL.")
public record AttachmentDto(
        long id,
        long messageId,
        String objectKey,
        String contentType,
        long sizeBytes,
        String originalName,
        @Schema(description = "Short-lived signed download URL.", nullable = true) String downloadUrl
) {
    public static AttachmentDto of(MessageAttachment a, String downloadUrl) {
        return new AttachmentDto(
                a.getId(),
                a.getMessageId(),
                a.getObjectKey(),
                a.getContentType(),
                a.getSizeBytes(),
                a.getOriginalName(),
                downloadUrl);
    }
}

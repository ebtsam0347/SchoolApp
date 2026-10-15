package com.forward.hrms.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "ChatUploadUrlResponse",
        description = "Pre-signed PUT URL for a chat attachment. Pass the returned objectKey back in the next send-message request.")
public record UploadUrlResponse(
        @Schema(description = "Bucket the URL targets.", example = "chat-attachments") String bucket,
        @Schema(description = "Object key the URL is signed for. Include this in the message's `attachments[]`.") String objectKey,
        @Schema(description = "Pre-signed PUT URL.") String uploadUrl,
        @Schema(description = "When the URL stops working (UTC).") LocalDateTime expiresAt
) {}

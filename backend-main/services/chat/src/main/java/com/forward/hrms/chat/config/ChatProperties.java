package com.forward.hrms.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Chat-service tunables. All have sensible defaults — override per
 * environment via {@code hrms.chat.*}.
 */
@ConfigurationProperties(prefix = "hrms.chat")
public record ChatProperties(
        int groupMemberCap,
        Duration editWindow,
        int messagePageMax,
        int messagePageDefault,
        /** §7.chat — body cap per message. Default 4 KiB chars. */
        int messageBodyMaxChars,
        /** §7.chat — max attachments per message. Default 10. */
        int messageAttachmentsMax,
        Attachments attachments
) {
    public ChatProperties {
        if (groupMemberCap <= 0) groupMemberCap = 50;
        if (editWindow == null) editWindow = Duration.ofMinutes(15);
        if (messagePageMax <= 0) messagePageMax = 100;
        if (messagePageDefault <= 0) messagePageDefault = 50;
        if (messageBodyMaxChars <= 0) messageBodyMaxChars = 4000;
        if (messageAttachmentsMax <= 0) messageAttachmentsMax = 10;
        if (attachments == null) attachments = new Attachments(null, null, null, 0, null);
    }

    /** Attachment-specific config. Object keys are scoped under {@code chat/{senderUserId}/...}. */
    public record Attachments(
            String bucket,
            Duration uploadUrlTtl,
            Duration downloadUrlTtl,
            long maxBytes,
            List<String> allowedContentTypes
    ) {
        private static final List<String> DEFAULT_ALLOWED = List.of(
                "image/png", "image/jpeg", "image/webp", "image/gif",
                "application/pdf",
                "application/msword",
                "application/vnd.ms-excel",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "text/plain",
                "text/csv");
        private static final long DEFAULT_MAX_BYTES = 25L * 1024 * 1024;  // 25 MiB

        public Attachments {
            if (bucket == null || bucket.isBlank()) bucket = "chat-attachments";
            if (uploadUrlTtl == null) uploadUrlTtl = Duration.ofMinutes(10);
            if (downloadUrlTtl == null) downloadUrlTtl = Duration.ofMinutes(10);
            if (maxBytes <= 0) maxBytes = DEFAULT_MAX_BYTES;
            if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
                allowedContentTypes = DEFAULT_ALLOWED;
            }
        }

        public Set<String> allowedSet() {
            return Set.copyOf(allowedContentTypes);
        }
    }
}

package com.forward.hrms.chat.service;

import com.forward.hrms.chat.config.ChatProperties;
import com.forward.hrms.chat.dto.request.AttachmentRef;
import com.forward.hrms.chat.dto.request.UploadUrlRequest;
import com.forward.hrms.chat.dto.response.AttachmentDto;
import com.forward.hrms.chat.dto.response.UploadUrlResponse;
import com.forward.hrms.chat.entity.Message;
import com.forward.hrms.chat.entity.MessageAttachment;
import com.forward.hrms.chat.enums.ChatErrorCode;
import com.forward.hrms.chat.repository.MessageAttachmentRepository;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.storage.ObjectStorage;

import io.minio.StatObjectResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Two-step chat attachment flow:
 *
 * <ol>
 *   <li>{@link #signUploadUrl} — service mints an object key under
 *       {@code chat/{senderUserId}/{uuid}_{safeName}}, validates the
 *       requested content-type / size, and returns a pre-signed PUT URL.</li>
 *   <li>Client PUTs to MinIO directly.</li>
 *   <li>{@link #persist} — invoked from {@link MessageService#send} once
 *       the message row exists. Stats each object (422 if MinIO doesn't
 *       have it), enforces ownership of the object key prefix, and
 *       persists {@link MessageAttachment} rows in the same transaction
 *       as the message.</li>
 * </ol>
 *
 * <p>Object keys must start with {@code chat/{senderUserId}/} so a
 * malicious client can't hand over a key from someone else's prefix.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final MessageAttachmentRepository repo;
    private final ObjectStorage storage;
    private final ChatProperties props;

    /* ------------------------------------------------------------------ */
    /* Sign upload URL                                                     */
    /* ------------------------------------------------------------------ */

    public UploadUrlResponse signUploadUrl(long senderUserId, UploadUrlRequest req) {
        validateContentType(req.contentType());
        validateSize(req.sizeBytes());

        String safe = sanitiseFilename(req.originalName());
        String key = "chat/" + senderUserId + "/" + UUID.randomUUID() + "_" + safe;
        Duration ttl = props.attachments().uploadUrlTtl();
        String url = storage.signedPutUrl(props.attachments().bucket(), key, ttl);
        return new UploadUrlResponse(props.attachments().bucket(), key, url, LocalDateTime.now().plus(ttl));
    }

    /* ------------------------------------------------------------------ */
    /* Persist (called from MessageService.send within @Transactional)     */
    /* ------------------------------------------------------------------ */

    /** Validate + stat + persist attachment rows for the just-created message. */
    public List<MessageAttachment> persist(Message message, List<AttachmentRef> refs) {
        if (refs == null || refs.isEmpty()) return List.of();

        List<MessageAttachment> out = new java.util.ArrayList<>(refs.size());
        for (AttachmentRef ref : refs) {
            validateContentType(ref.contentType());
            validateSize(ref.sizeBytes());
            ensureOwnedKeyPrefix(message.getSenderUserId(), ref.objectKey());

            Optional<StatObjectResponse> stat = storage.stat(props.attachments().bucket(), ref.objectKey());
            if (stat.isEmpty()) {
                throw new UnprocessableError(ChatErrorCode.ATTACHMENT_NOT_FOUND,
                        "object not found in storage — finish the PUT before sending");
            }
            // Trust the size MinIO reports over the client-provided one.
            long actualSize = stat.get().size();
            if (actualSize > props.attachments().maxBytes()) {
                throw new ConflictError(ChatErrorCode.ATTACHMENT_TOO_LARGE,
                        "uploaded object exceeds size cap");
            }

            MessageAttachment a = new MessageAttachment();
            a.setMessageId(message.getId());
            a.setObjectKey(ref.objectKey());
            a.setContentType(ref.contentType());
            a.setSizeBytes(actualSize);
            a.setOriginalName(ref.originalName());
            out.add(repo.save(a));
        }
        return out;
    }

    /* ------------------------------------------------------------------ */
    /* Read paths                                                          */
    /* ------------------------------------------------------------------ */

    public List<AttachmentDto> dtosFor(long messageId) {
        return repo.findAllByMessageIdOrderByIdAsc(messageId).stream()
                .map(a -> AttachmentDto.of(a, signGet(a.getObjectKey())))
                .toList();
    }

    /** Bulk loader for paged messages — one query, then group by messageId. */
    public Map<Long, List<AttachmentDto>> dtosByMessageIds(List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return Map.of();
        Map<Long, List<AttachmentDto>> grouped = new HashMap<>();
        for (MessageAttachment a : repo.findAllByMessageIdInOrderByIdAsc(messageIds)) {
            grouped.computeIfAbsent(a.getMessageId(), k -> new java.util.ArrayList<>())
                    .add(AttachmentDto.of(a, signGet(a.getObjectKey())));
        }
        return grouped;
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    private void validateContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new UnprocessableError(ChatErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED, "content-type required");
        }
        if (!props.attachments().allowedSet().contains(contentType.toLowerCase())) {
            throw new UnprocessableError(ChatErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED,
                    "content-type not allowed: " + contentType);
        }
    }

    private void validateSize(long size) {
        if (size <= 0) {
            throw new ConflictError(ChatErrorCode.ATTACHMENT_TOO_LARGE, "size must be positive");
        }
        if (size > props.attachments().maxBytes()) {
            throw new ConflictError(ChatErrorCode.ATTACHMENT_TOO_LARGE,
                    "attachment exceeds size cap of " + props.attachments().maxBytes() + " bytes");
        }
    }

    private void ensureOwnedKeyPrefix(long senderUserId, String objectKey) {
        if (objectKey == null || !objectKey.startsWith("chat/" + senderUserId + "/")) {
            throw new ConflictError(ChatErrorCode.ATTACHMENT_KEY_INVALID,
                    "objectKey does not belong to caller");
        }
    }

    private String signGet(String key) {
        try {
            return storage.signedGetUrl(props.attachments().bucket(), key, props.attachments().downloadUrlTtl());
        } catch (RuntimeException ex) {
            log.warn("Failed to sign GET URL for {}: {}", key, ex.getMessage());
            return null;
        }
    }

    private static String sanitiseFilename(String name) {
        String s = name == null ? "file" : name;
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) s = s.substring(slash + 1);
        s = s.replaceAll("[^A-Za-z0-9._-]", "_");
        if (s.isBlank()) s = "file";
        return s.length() > 128 ? s.substring(s.length() - 128) : s;
    }
}

package com.forward.hrms.employee.service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.config.DocumentProperties;
import com.forward.hrms.employee.dto.request.RecordDocumentRequest;
import com.forward.hrms.employee.dto.request.UploadUrlRequest;
import com.forward.hrms.employee.dto.response.EmployeeDocumentDto;
import com.forward.hrms.employee.dto.response.UploadUrlResponse;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeDocument;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.EmployeeDocumentRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;

import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Two-step document upload:
 *   1. POST /upload-url with {docType, filename} → service mints an object
 *      key {@code employees/{id}/{docType}/{uuid}_{safeFilename}} and
 *      returns a pre-signed PUT URL.
 *   2. Client PUTs the file directly to MinIO.
 *   3. POST /documents with {docType, title, objectKey, contentType?} →
 *      service stats the object (404 if upload didn't complete) and
 *      persists the metadata row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeDocumentService {

    private static final String PROFILE_PICTURE_DOC_TYPE_PREFIX = "profile-picture";

    private final EmployeeDocumentRepository repo;
    private final EmployeeRepository employees;
    private final ObjectStorage storage;
    private final DocumentProperties props;
    private final AuditEmitter audit;
    private final StorageUrlService urls;

    @Transactional(readOnly = true)
    public PageResponse<EmployeeDocumentDto> list(long employeeId, Pageable pageable) {
        ensureEmployee(employeeId);
        Page<EmployeeDocument> page = repo.findByEmployeeId(employeeId, pageable);
        return PageResponse.from(page,
                page.getContent().stream()
                        .map(d -> EmployeeDocumentDto.of(d, signGet(props.bucket(), d.getObjectKey())))
                        .toList());
    }

    public UploadUrlResponse signUploadUrl(long employeeId, UploadUrlRequest req) {
        ensureEmployee(employeeId);
        validateSize(req.bytes(), props.maxSizeBytes(), "document");
        return signUploadUrl(employeeId, req.docType().name().toLowerCase(),
                req.filename(), props.bucket());
    }

    /** Used by the profile-picture flow; doesn't require a DocumentType enum value. */
    public UploadUrlResponse signProfilePictureUploadUrl(long employeeId, String filename, Long bytes) {
        ensureEmployee(employeeId);
        validateSize(bytes, props.avatarMaxSizeBytes(), "profile picture");
        return signUploadUrl(employeeId, PROFILE_PICTURE_DOC_TYPE_PREFIX, filename, props.avatarBucket());
    }

    @Transactional
    public EmployeeDocumentDto record(long employeeId, RecordDocumentRequest req) {
        ensureEmployee(employeeId);
        String normalized = normalizeKey(req.objectKey());
        if (!normalized.startsWith("employees/" + employeeId + "/")) {
            throw new ConflictError(EmployeeErrorCode.DOCUMENT_NOT_FOUND,
                    "objectKey does not belong to this employee");
        }
        Optional<StatObjectResponse> stat = storage.stat(props.bucket(), req.objectKey());
        if (stat.isEmpty()) {
            throw new UnprocessableError(EmployeeErrorCode.DOCUMENT_NOT_FOUND,
                    "object not found in storage — finish the PUT before recording metadata");
        }
        long actualSize = stat.get().size();
        if (actualSize > props.maxSizeBytes()) {
            try { storage.delete(props.bucket(), req.objectKey()); }
            catch (RuntimeException ex) {
                log.warn("Failed to delete oversized object {}: {}", req.objectKey(), ex.getMessage());
            }
            throw new UnprocessableError(EmployeeErrorCode.DOCUMENT_TOO_LARGE,
                    "uploaded object exceeds " + props.maxSizeBytes() + " bytes");
        }

        EmployeeDocument d = new EmployeeDocument();
        d.setEmployeeId(employeeId);
        d.setDocType(req.docType());
        d.setTitle(req.title());
        d.setObjectKey(req.objectKey());
        d.setContentType(req.contentType() != null ? req.contentType() : stat.get().contentType());
        d.setSizeBytes(actualSize);
        repo.save(d);

        audit.emit("employee_document", String.valueOf(d.getId()), "uploaded",
                null,
                Map.of(
                        "employeeId", employeeId,
                        "docType", req.docType().name(),
                        "title", req.title(),
                        "objectKey", req.objectKey(),
                        "sizeBytes", d.getSizeBytes() == null ? 0L : d.getSizeBytes()),
                true);
        return EmployeeDocumentDto.of(d, signGet(props.bucket(), d.getObjectKey()));
    }

    @Transactional
    public void delete(long employeeId, long docId) {
        EmployeeDocument d = load(employeeId, docId);
        try {
            storage.delete(props.bucket(), d.getObjectKey());
        } catch (RuntimeException ex) {
            log.warn("Failed to delete object {}: {}", d.getObjectKey(), ex.getMessage());
        }
        repo.delete(d);
        audit.emit("employee_document", String.valueOf(docId), "deleted",
                null, Map.of("employeeId", employeeId, "objectKey", d.getObjectKey()), true);
    }

    @Transactional
    public Employee setProfilePicture(long employeeId, String objectKey) {
        Employee e = employees.findById(employeeId)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found"));
        String normalized = normalizeKey(objectKey);
        if (!normalized.startsWith("employees/" + employeeId + "/" + PROFILE_PICTURE_DOC_TYPE_PREFIX + "/")) {
            throw new ConflictError(EmployeeErrorCode.DOCUMENT_NOT_FOUND,
                    "objectKey does not belong to this employee's profile-picture path");
        }
        Optional<StatObjectResponse> stat = storage.stat(props.avatarBucket(), objectKey);
        if (stat.isEmpty()) {
            throw new UnprocessableError(EmployeeErrorCode.DOCUMENT_NOT_FOUND,
                    "object not found in storage — finish the PUT before setting the profile picture");
        }
        if (stat.get().size() > props.avatarMaxSizeBytes()) {
            try { storage.delete(props.avatarBucket(), objectKey); }
            catch (RuntimeException ex) {
                log.warn("Failed to delete oversized avatar {}: {}", objectKey, ex.getMessage());
            }
            throw new UnprocessableError(EmployeeErrorCode.DOCUMENT_TOO_LARGE,
                    "uploaded avatar exceeds " + props.avatarMaxSizeBytes() + " bytes");
        }
        // Delete the previous picture object if any.
        if (e.getProfilePictureKey() != null && !e.getProfilePictureKey().equals(objectKey)) {
            try { storage.delete(props.avatarBucket(), e.getProfilePictureKey()); }
            catch (RuntimeException ex) {
                log.warn("Failed to delete previous profile picture {}: {}",
                        e.getProfilePictureKey(), ex.getMessage());
            }
            // The signed URL for the old object lives in the memo for days —
            // drop it so nothing keeps handing out a link to a deleted object.
            urls.evictAvatar(e.getProfilePictureKey());
        }
        e.setProfilePictureKey(objectKey);
        audit.emit("employee", String.valueOf(employeeId), "profile_picture_updated",
                null, Map.of("objectKey", objectKey), false);
        return e;
    }

    private UploadUrlResponse signUploadUrl(long employeeId, String prefix, String filename, String bucket) {
        String safe = sanitiseFilename(filename);
        String key = "employees/" + employeeId + "/" + prefix + "/" + UUID.randomUUID() + "_" + safe;
        Duration ttl = props.uploadUrlTtl();
        String url = storage.signedPutUrl(bucket, key, ttl);
        return new UploadUrlResponse(bucket, key, url, LocalDateTime.now().plus(ttl));
    }

    private String signGet(String bucket, String key) {
        try {
            return storage.signedGetUrl(bucket, key, props.downloadUrlTtl());
        } catch (RuntimeException ex) {
            log.warn("Failed to sign GET URL for {}: {}", key, ex.getMessage());
            return null;
        }
    }

    public String signAvatarGet(String objectKey) {
        return urls.signAvatarGet(objectKey);
    }

    private static void validateSize(Long declared, long limit, String label) {
        if (declared != null && declared > limit) {
            throw new UnprocessableError(EmployeeErrorCode.DOCUMENT_TOO_LARGE,
                    label + " exceeds " + limit + " bytes");
        }
    }

    private static String normalizeKey(String raw) {
        if (raw == null) return "";
        String decoded;
        try {
            decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return raw;
        }
        decoded = decoded.replace('\\', '/');
        try {
            Path p = Paths.get(decoded).normalize();
            return p.toString().replace('\\', '/');
        } catch (RuntimeException ex) {
            return decoded;
        }
    }

    private EmployeeDocument load(long employeeId, long docId) {
        return EmployeeOwnedRows.requireOwned(repo, EmployeeDocument::getEmployeeId,
                employeeId, docId, EmployeeErrorCode.DOCUMENT_NOT_FOUND, "document");
    }

    private void ensureEmployee(long employeeId) {
        EmployeeOwnedRows.ensureEmployee(employees, employeeId);
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

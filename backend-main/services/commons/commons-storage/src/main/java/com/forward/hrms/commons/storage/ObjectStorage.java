package com.forward.hrms.commons.storage;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper over the MinIO client. Hides the SDK so services aren't
 * pinned to a MinIO version directly. Bucket-per-purpose; the calling
 * service knows the bucket name (or ships defaults via
 * {@code hrms.storage.ensure-buckets}).
 */
@Slf4j
@RequiredArgsConstructor
public class ObjectStorage {

    private final MinioClient client;
    private final StorageProperties props;

    public void upload(String bucket, String key, InputStream data, long size,
                       String contentType, Map<String, String> userMetadata) {
        try {
            PutObjectArgs.Builder b = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(data, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType);
            if (userMetadata != null && !userMetadata.isEmpty()) {
                b.userMetadata(userMetadata);
            }
            client.putObject(b.build());
        } catch (Exception e) {
            throw new StorageException("Failed to upload " + bucket + "/" + key, e);
        }
    }

    public String signedGetUrl(String bucket, String key, Duration ttl) {
        return signedGetUrl(bucket, key, ttl, null);
    }

    /**
     * Pre-signed GET with S3 {@code response-*} overrides folded into the
     * signature — {@code response-cache-control} is the useful one: it makes
     * MinIO return the caching header we want on an object we never set one
     * on at upload time. Only meaningful when the URL itself is stable
     * (see the avatar URL memo in employee-service); a URL whose signature
     * changes per response is a fresh cache key every time and will be
     * re-fetched no matter what the header says.
     */
    public String signedGetUrl(String bucket, String key, Duration ttl,
                               Map<String, String> responseOverrides) {
        Duration t = ttl == null ? props.defaultUrlTtl() : ttl;
        try {
            GetPresignedObjectUrlArgs.Builder b = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry((int) t.toSeconds(), TimeUnit.SECONDS);
            if (responseOverrides != null && !responseOverrides.isEmpty()) {
                b.extraQueryParams(responseOverrides);
            }
            return client.getPresignedObjectUrl(b.build());
        } catch (Exception e) {
            throw new StorageException("Failed to sign URL for " + bucket + "/" + key, e);
        }
    }

    /**
     * Pre-signed PUT URL the client uploads to directly. The service then
     * confirms the object exists ({@link #stat}) and records metadata.
     */
    public String signedPutUrl(String bucket, String key, Duration ttl) {
        Duration t = ttl == null ? props.defaultUrlTtl() : ttl;
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(key)
                    .expiry((int) t.toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to sign PUT URL for " + bucket + "/" + key, e);
        }
    }

    /**
     * Returns a {@link StatObjectResponse} when the object exists, empty
     * when MinIO replies 404. Throws {@link StorageException} on any
     * other error so callers don't have to swallow MinIO-specific types.
     */
    public Optional<StatObjectResponse> stat(String bucket, String key) {
        try {
            return Optional.of(client.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build()));
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())
                    || "NoSuchObject".equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            throw new StorageException("statObject failed for " + bucket + "/" + key, e);
        } catch (Exception e) {
            throw new StorageException("statObject failed for " + bucket + "/" + key, e);
        }
    }

    /**
     * Read an object's bytes. Returns the open stream — the caller closes it.
     * Use sparingly: for large objects, prefer a signed GET URL the client
     * follows directly. Useful for server-side composition (e.g. zipping
     * many small PDFs into one archive).
     */
    public InputStream getObject(String bucket, String key) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new StorageException("getObject failed for " + bucket + "/" + key, e);
        }
    }

    public void delete(String bucket, String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("Failed to delete " + bucket + "/" + key, e);
        }
    }

    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) { super(message, cause); }
    }
}

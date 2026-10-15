package com.forward.hrms.employee.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties("hrms.employee.documents")
public record DocumentProperties(
        String bucket,
        String avatarBucket,
        Duration uploadUrlTtl,
        Duration downloadUrlTtl,
        Duration avatarUrlTtl,
        long maxSizeBytes,
        long avatarMaxSizeBytes
) {
    /** SigV4 caps a pre-signed URL at 7 days. */
    private static final Duration MAX_PRESIGN_TTL = Duration.ofDays(7);

    public DocumentProperties {
        if (bucket == null || bucket.isBlank()) bucket = "employee-docs";
        if (avatarBucket == null || avatarBucket.isBlank()) avatarBucket = "employee-avatars";
        if (uploadUrlTtl == null) uploadUrlTtl = Duration.ofMinutes(10);
        if (downloadUrlTtl == null) downloadUrlTtl = Duration.ofMinutes(10);
        // Avatars are signed long and memoized so the URL — and therefore the
        // browser cache key — stays stable across page loads. A 10-minute TTL
        // here would mean a fresh URL per render and a full re-download of
        // every avatar on the page, every time.
        if (avatarUrlTtl == null) avatarUrlTtl = MAX_PRESIGN_TTL;
        if (avatarUrlTtl.compareTo(MAX_PRESIGN_TTL) > 0) avatarUrlTtl = MAX_PRESIGN_TTL;
        if (maxSizeBytes <= 0L) maxSizeBytes = 10L * 1024 * 1024;        // 10 MB
        if (avatarMaxSizeBytes <= 0L) avatarMaxSizeBytes = 2L * 1024 * 1024; // 2 MB
    }

    /**
     * How long a signed avatar URL may stay in the memo. Kept a day short of
     * the signature's own lifetime so a cache hit never hands out a URL that
     * expires while the page is still open.
     */
    public Duration avatarUrlCacheTtl() {
        Duration memo = avatarUrlTtl.minusDays(1);
        return memo.isPositive() ? memo : avatarUrlTtl.dividedBy(2);
    }
}

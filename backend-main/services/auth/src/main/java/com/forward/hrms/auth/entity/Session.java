package com.forward.hrms.auth.entity;

import com.forward.hrms.auth.enums.RevokeReason;
import com.forward.hrms.commons.core.jpa.BaseLongIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;

/**
 * Operational table — no audit columns. Lifecycle fields below are the
 * audit. {@code refresh_token_id} is the opaque id mirrored into Redis at
 * {@code auth:refresh:{id}}.
 */
@Entity
@Table(name = "sessions")
@Getter
@Setter
public class Session extends BaseLongIdEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Optimistic-lock version. Two concurrent /refresh calls with the same
     * inbound refresh token both load the row, both rotate the
     * refresh_token_id, and both commit — leaving two valid refresh tokens
     * for one session in Redis. {@code @Version} forces the second commit
     * to fail with OptimisticLockException so only one rotation lands.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "refresh_token_id", nullable = false, length = 64, unique = true)
    private String refreshTokenId;

    @Column(name = "device_label", length = 128)
    private String deviceLabel;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip_address", columnDefinition = "inet")
    @ColumnTransformer(write = "?::inet")
    private String ipAddress;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by", length = 64)
    private String revokedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 32)
    private RevokeReason revokedReason;

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }
}

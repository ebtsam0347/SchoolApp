package com.forward.hrms.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Bug #7 — local projection of {@code (role, user)} membership replicated
 * from auth via the {@code auth.user.role_assigned} / {@code role_revoked}
 * events. Used by {@code EventDispatcher} to fan out notifications when
 * a workflow stage targets a role with no specific user assigned.
 */
@Entity
@Table(name = "role_members")
@Getter
@Setter
@NoArgsConstructor
public class RoleMember {

    @EmbeddedId
    private Key id;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    public RoleMember(String roleCode, long userId, LocalDateTime grantedAt) {
        this.id = new Key(roleCode, userId);
        this.grantedAt = grantedAt;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {

        @Column(name = "role_code", length = 64, nullable = false)
        private String roleCode;

        @Column(name = "user_id", nullable = false)
        private Long userId;

        public Key(String roleCode, Long userId) {
            this.roleCode = roleCode;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(roleCode, k.roleCode) && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roleCode, userId);
        }
    }
}

package com.forward.hrms.notification.service;

import com.forward.hrms.notification.repository.RoleMemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bug #7 — keeps the local {@code role_members} projection in sync with
 * auth's {@code USER_ROLE_ASSIGNED} / {@code USER_ROLE_REVOKED} events
 * and serves the projection's read path. A thin wrapper around the
 * repository so consumer + dispatcher don't both reach into JPA.
 *
 * <p>Out-of-order delivery is handled by always taking the latest event
 * as truth: an upsert with a newer {@code granted_at} wins, and a
 * revoke with a {@code revoked_at} no older than the stored grant
 * removes the row. We rely on Kafka per-partition ordering — events
 * for the same user share a partition key — so the cross-event race
 * is bounded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleMembershipService {

    private final RoleMemberRepository repo;

    @Transactional
    public void onGranted(String roleCode, long userId) {
        if (roleCode == null || roleCode.isBlank()) return;
        repo.upsert(roleCode, userId, LocalDateTime.now());
        log.debug("role_members upsert: {}+{}", roleCode, userId);
    }

    @Transactional
    public void onRevoked(String roleCode, long userId) {
        if (roleCode == null || roleCode.isBlank()) return;
        int deleted = repo.deleteOne(roleCode, userId);
        log.debug("role_members delete: {}+{} (rows={})", roleCode, userId, deleted);
    }

    @Transactional(readOnly = true)
    public List<Long> usersWithRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) return List.of();
        return repo.findUserIdsByRoleCode(roleCode);
    }
}

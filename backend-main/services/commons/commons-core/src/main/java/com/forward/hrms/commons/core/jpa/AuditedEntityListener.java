package com.forward.hrms.commons.core.jpa;

import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.context.RequestContext;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.LocalDateTime;

public class AuditedEntityListener {

    @PrePersist
    void prePersist(AuditedEntity e) {
        LocalDateTime now = LocalDateTime.now();
        String user = CurrentUserHolder.usernameOrSystem();
        if (e.getCreatedAt() == null) e.setCreatedAt(now);
        e.setUpdatedAt(now);
        if (e.getCreatedBy() == null) e.setCreatedBy(user);
        e.setUpdatedBy(user);
        if (e.getIpAddress() == null) e.setIpAddress(currentIp());
    }

    @PreUpdate
    void preUpdate(AuditedEntity e) {
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(CurrentUserHolder.usernameOrSystem());
        String ip = currentIp();
        if (ip != null) e.setIpAddress(ip);
    }

    private static String currentIp() {
        return RequestContext.current().map(RequestContext::ipAddress).orElse(null);
    }
}

package com.forward.hrms.notification.repository;

import com.forward.hrms.notification.entity.NotificationTemplate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    Optional<NotificationTemplate> findByType(String type);
    boolean existsByType(String type);
    List<NotificationTemplate> findAllByOrderByTypeAsc();
}

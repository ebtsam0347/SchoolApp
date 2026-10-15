package com.forward.hrms.notification.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.notification.dto.request.CreateTemplateRequest;
import com.forward.hrms.notification.dto.request.UpdateTemplateRequest;
import com.forward.hrms.notification.dto.response.TemplateDto;
import com.forward.hrms.notification.entity.NotificationTemplate;
import com.forward.hrms.notification.enums.NotificationErrorCode;
import com.forward.hrms.notification.repository.NotificationTemplateRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final NotificationTemplateRepository repo;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<TemplateDto> list() {
        return repo.findAllByOrderByTypeAsc().stream().map(TemplateDto::of).toList();
    }

    @Transactional
    public TemplateDto create(CreateTemplateRequest req) {
        if (repo.existsByType(req.type())) {
            throw new ConflictError(NotificationErrorCode.TEMPLATE_TYPE_TAKEN, "template type already in use");
        }
        NotificationTemplate t = new NotificationTemplate();
        t.setType(req.type());
        t.setLabel(req.label());
        t.setDescription(req.description());
        t.setTitleTpl(req.titleTpl());
        t.setBodyTpl(req.bodyTpl());
        t.setLinkTpl(req.linkTpl());
        t.setCategory(req.category());
        t.setActive(true);
        t = repo.save(t);
        audit.emit("notification_template", String.valueOf(t.getId()), "created",
                null, snapshot(t), false);
        return TemplateDto.of(t);
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(
            value = com.forward.hrms.notification.config.NotificationCacheConfig.CACHE_TEMPLATES,
            key = "#result.type()")
    public TemplateDto update(long id, UpdateTemplateRequest req) {
        NotificationTemplate t = repo.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        NotificationErrorCode.TEMPLATE_NOT_FOUND, "template not found"));
        Map<String, Object> before = snapshot(t);
        if (req.label() != null) t.setLabel(req.label());
        if (req.description() != null) t.setDescription(req.description());
        if (req.titleTpl() != null && !req.titleTpl().isBlank()) t.setTitleTpl(req.titleTpl());
        if (req.bodyTpl() != null) t.setBodyTpl(req.bodyTpl());
        if (req.linkTpl() != null) t.setLinkTpl(req.linkTpl());
        if (req.category() != null) t.setCategory(req.category());
        if (req.active() != null) t.setActive(req.active());
        t = repo.save(t);
        audit.emit("notification_template", String.valueOf(t.getId()), "updated",
                before, snapshot(t), false);
        return TemplateDto.of(t);
    }

    private static Map<String, Object> snapshot(NotificationTemplate t) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", t.getType());
        m.put("label", t.getLabel());
        m.put("category", t.getCategory() == null ? null : t.getCategory().name());
        m.put("active", t.isActive());
        return m;
    }
}

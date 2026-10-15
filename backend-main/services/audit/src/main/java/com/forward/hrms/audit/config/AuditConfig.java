package com.forward.hrms.audit.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Audit-service config root. The previous {@code auditObjectMapper} bean
 * (Jackson 2 + JavaTimeModule) was removed once everything inside the
 * service moved to Jackson 3 (SB4 default — see the
 * "Jackson 3 (tools.jackson) is the default" memory). Both Kafka payload
 * parsing and HTTP response serialization now use the autowired
 * {@code tools.jackson.databind.ObjectMapper}.
 */
@Configuration
@EnableConfigurationProperties(AuditProperties.class)
public class AuditConfig {
}

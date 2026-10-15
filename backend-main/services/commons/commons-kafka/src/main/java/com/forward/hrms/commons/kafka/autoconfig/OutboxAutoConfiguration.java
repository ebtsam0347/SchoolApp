package com.forward.hrms.commons.kafka.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.kafka.outbox.OutboxProperties;
import com.forward.hrms.commons.kafka.outbox.OutboxPublisher;
import com.forward.hrms.commons.kafka.outbox.OutboxRepository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass({KafkaTemplate.class, jakarta.persistence.Entity.class})
@ConditionalOnProperty(prefix = "hrms.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OutboxProperties.class)
@EnableJpaRepositories(basePackageClasses = OutboxRepository.class)
@EnableScheduling
public class OutboxAutoConfiguration {

    /**
     * Outbox uses Jackson 2 ({@code com.fasterxml.jackson.databind}) — Spring
     * Boot 4's auto-registered {@code ObjectMapper} is Jackson 3
     * ({@code tools.jackson.databind}), a different type. Create a dedicated
     * mapper here so the outbox doesn't depend on the application's
     * Jackson 3 configuration.
     *
     * <p>{@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} is disabled
     * deliberately. The default emits {@code LocalDateTime} as a JSON
     * array {@code [y, m, d, h, m, s, nanos]} — which audit-service's
     * ingest can't parse with {@code LocalDateTime.parse(string)}, so
     * every envelope across every service was being silently rejected
     * (UNPARSEABLE_OCCURRED_AT). ISO strings round-trip cleanly through
     * the default {@code LocalDateTime.parse}.
     */
    @Bean(name = "outboxObjectMapper")
    @ConditionalOnMissingBean(name = "outboxObjectMapper")
    public ObjectMapper outboxObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    @ConditionalOnMissingBean
    public Outbox outbox(OutboxRepository repo, @Qualifier("outboxObjectMapper") ObjectMapper json) {
        return new Outbox(repo, json);
    }

    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnMissingBean
    public OutboxPublisher outboxPublisher(OutboxRepository repo,
                                           KafkaTemplate<String, String> kafka,
                                           OutboxProperties props) {
        return new OutboxPublisher(repo, kafka, props);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditEmitter auditEmitter(Outbox outbox,
                                     @Value("${spring.application.name}") String serviceName) {
        return new AuditEmitter(outbox, serviceName);
    }
}

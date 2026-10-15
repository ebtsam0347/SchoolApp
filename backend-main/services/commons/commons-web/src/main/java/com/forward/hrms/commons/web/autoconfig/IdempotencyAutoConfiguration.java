package com.forward.hrms.commons.web.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.forward.hrms.commons.web.idempotency.IdempotencyKeyFilter;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "hrms.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyAutoConfiguration {

    /**
     * Dedicated Jackson 2 mapper for the idempotency-replay payload.
     * Mirrors the {@code outboxObjectMapper} / {@code sagaObjectMapper} /
     * {@code cacheObjectMapper} pattern: SB4 auto-registers
     * {@code ObjectMapper} as Jackson 3, a different type, and services
     * that pull commons-kafka + commons-saga + commons-cache accumulate
     * multiple Jackson-2 ObjectMappers — none of them {@code @Primary} —
     * which would make an unqualified {@code ObjectMapper} parameter
     * fail with {@code NoUniqueBeanDefinitionException}.
     *
     * <p>{@code defaultCandidate = false} keeps it invisible to type-only
     * autowiring (still resolvable by name / qualifier).
     */
    @Bean(name = "idempotencyObjectMapper", defaultCandidate = false)
    @ConditionalOnMissingBean(name = "idempotencyObjectMapper")
    public ObjectMapper idempotencyObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    public FilterRegistrationBean<IdempotencyKeyFilter> idempotencyKeyFilter(
            StringRedisTemplate redis,
            @Qualifier("idempotencyObjectMapper") ObjectMapper json) {
        FilterRegistrationBean<IdempotencyKeyFilter> reg = new FilterRegistrationBean<>(new IdempotencyKeyFilter(redis, json));
        reg.setOrder(IdempotencyKeyFilter.ORDER);
        return reg;
    }
}

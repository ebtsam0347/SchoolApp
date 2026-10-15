package com.forward.hrms.commons.cache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.forward.hrms.commons.cache.CatalogCache;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.cache.client.EmployeeApiClient;
import com.forward.hrms.commons.cache.consumer.EmployeeEventsCacheUpdater;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.client.RestClient;

/**
 * Wires the shared employee + catalog cache into a service. Opted in
 * with {@code hrms.cache.enabled=true} (defaults off so audit / gateway
 * don't pay for it).
 *
 * <h2>Bean shape</h2>
 * <ul>
 *   <li>{@link EmployeeApiClient} — RestClient for the bulk-fetch miss path.</li>
 *   <li>{@link CatalogCache} — typed catalog accessors.</li>
 *   <li>{@link EmployeeDisplayCache} — the main read API.</li>
 *   <li>{@link EmployeeEventsCacheUpdater} — gated additionally on
 *       {@code KafkaListener} being on the classpath, so a cache-only
 *       service (no Kafka) can still read.</li>
 * </ul>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
        "org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration",
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
})
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "hrms.cache", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CommonsCacheProperties.class)
public class CommonsCacheAutoConfiguration {

    /**
     * Dedicated Jackson 2 mapper for the cache. Mirrors
     * {@code outboxObjectMapper} in commons-kafka — SB4's auto-registered
     * {@code ObjectMapper} is Jackson 3 ({@code tools.jackson.databind}),
     * a different type. Services that already register
     * {@code outboxObjectMapper} (or another Jackson 2 ObjectMapper) get
     * theirs via {@code @ConditionalOnMissingBean}.
     *
     * <p>{@code defaultCandidate=false} keeps the bean addressable by
     * name (and via {@code @Qualifier("cacheObjectMapper")}) but invisible
     * to unqualified type-only autowiring. Without this, services that
     * also pull {@code commons-kafka} + {@code commons-saga} accumulate
     * three Jackson-2 ObjectMappers, and any pre-existing unqualified
     * {@code ObjectMapper json} parameter (e.g. commons-web's
     * {@code IdempotencyAutoConfiguration}) starts to fail with
     * {@code NoUniqueBeanDefinitionException}.
     */
    @Bean(name = "cacheObjectMapper", defaultCandidate = false)
    @ConditionalOnMissingBean(name = "cacheObjectMapper")
    public ObjectMapper cacheObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public EmployeeApiClient cacheEmployeeApiClient(CommonsCacheProperties props,
                                                    RestClient.Builder builder) {
        return new EmployeeApiClient(props, builder);
    }

    @Bean
    public CatalogCache catalogCache(StringRedisTemplate redis,
                                     @Qualifier("cacheObjectMapper") ObjectMapper json,
                                     EmployeeApiClient client,
                                     CommonsCacheProperties props) {
        return new CatalogCache(redis, json, client, props);
    }

    @Bean
    public EmployeeDisplayCache employeeDisplayCache(StringRedisTemplate redis,
                                                     @Qualifier("cacheObjectMapper") ObjectMapper json,
                                                     EmployeeApiClient client,
                                                     CatalogCache catalogs,
                                                     CommonsCacheProperties props) {
        return new EmployeeDisplayCache(redis, json, client, catalogs, props);
    }

    @Bean
    @ConditionalOnClass(KafkaListener.class)
    public EmployeeEventsCacheUpdater employeeEventsCacheUpdater(EmployeeDisplayCache cache,
                                                                 CatalogCache catalogs,
                                                                 CommonsCacheProperties props,
                                                                 @Qualifier("cacheObjectMapper") ObjectMapper json) {
        return new EmployeeEventsCacheUpdater(cache, catalogs, props, json);
    }
}

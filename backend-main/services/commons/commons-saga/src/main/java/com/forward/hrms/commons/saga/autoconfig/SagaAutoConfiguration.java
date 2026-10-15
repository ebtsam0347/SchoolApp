package com.forward.hrms.commons.saga.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.forward.hrms.commons.saga.Saga;
import com.forward.hrms.commons.saga.SagaProperties;
import com.forward.hrms.commons.saga.SagaRegistry;
import com.forward.hrms.commons.saga.SagaRepository;
import com.forward.hrms.commons.saga.SagaRunner;
import com.forward.hrms.commons.saga.SagaSecretStore;
import com.forward.hrms.commons.saga.SagaService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

/**
 * Wires the saga substrate. Activates whenever {@code commons-saga} is on
 * the classpath; turn off per service with {@code hrms.saga.enabled=false}
 * if needed (e.g. for a service that ships without the saga schema).
 *
 * <p>Uses a dedicated Jackson 2 mapper, mirroring the outbox autoconfig:
 * SB4's auto-registered {@code ObjectMapper} is Jackson 3 (a different
 * type), so the substrate brings its own.
 */
@AutoConfiguration
@ConditionalOnClass(jakarta.persistence.Entity.class)
@ConditionalOnProperty(prefix = "hrms.saga", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SagaProperties.class)
@EnableJpaRepositories(basePackageClasses = SagaRepository.class)
@EnableScheduling
public class SagaAutoConfiguration {

    @Bean(name = "sagaObjectMapper")
    @ConditionalOnMissingBean(name = "sagaObjectMapper")
    public ObjectMapper sagaObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaRegistry sagaRegistry(List<Saga> sagas) {
        return new SagaRegistry(sagas);
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaService sagaService(SagaRepository repo,
                                   SagaRegistry registry,
                                   @Qualifier("sagaObjectMapper") ObjectMapper json,
                                   ObjectProvider<SagaSecretStore> secretStore) {
        return new SagaService(repo, registry, json, secretStore.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaRunner sagaRunner(SagaRepository repo,
                                 ObjectProvider<SagaRunner> self,
                                 SagaRegistry registry,
                                 @Qualifier("sagaObjectMapper") ObjectMapper json,
                                 SagaProperties props,
                                 ObjectProvider<SagaSecretStore> secretStore) {
        return new SagaRunner(repo, self, registry, json, props, secretStore.getIfAvailable());
    }

    /**
     * Wired only when {@code spring-data-redis} is on the classpath. Services
     * that don't need {@code startWithSecrets} can omit Redis entirely.
     *
     * <p>The {@link RedisSagaSecretStore} reference and
     * {@link org.springframework.data.redis.core.StringRedisTemplate}
     * parameter live inside this nested class so the JVM doesn't try
     * to load Redis classes when the conditional is FALSE. Putting them
     * directly on the outer class would force a {@code ClassNotFoundException}
     * at autoconfig load time on services that exclude Redis (payroll,
     * audit, etc.).
     */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
    static class RedisSecretStoreAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean(SagaSecretStore.class)
        public SagaSecretStore sagaSecretStore(
                org.springframework.data.redis.core.StringRedisTemplate redis) {
            return new com.forward.hrms.commons.saga.RedisSagaSecretStore(redis);
        }
    }
}

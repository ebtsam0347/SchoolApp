package com.forward.hrms.commons.batch;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Provides a singleton {@link BatchRunner} once a
 * {@link PlatformTransactionManager} is present (i.e. the service
 * has a JPA / JDBC tx manager). Services that don't expose a tx
 * manager (gateway, bff) silently skip the bean and never inject
 * one.
 *
 * <p>{@code afterName} is load-bearing: {@link ConditionalOnBean}
 * is order-sensitive, so without forcing this autoconfig to run
 * after the JPA / JDBC tx-manager autoconfigs the
 * {@link PlatformTransactionManager} bean isn't registered yet at
 * condition-evaluation time, the condition silently returns false,
 * and any service that injects {@link BatchRunner} fails to start.
 * String FQCNs (not {@code .class} literals) keep commons-batch off
 * a hard dependency on the JPA / JDBC modules.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
public class CommonsBatchAutoConfiguration {

    @Bean
    @ConditionalOnBean(PlatformTransactionManager.class)
    @ConditionalOnMissingBean
    public BatchRunner batchRunner(PlatformTransactionManager txManager) {
        return new BatchRunner(txManager);
    }
}

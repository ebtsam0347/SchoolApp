package com.forward.hrms.commons.testsupport.containers;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Apply at the test-class level to wire shared Testcontainers (PG, Redis,
 * Kafka, MinIO) into Spring properties. Containers are JVM singletons —
 * first test pays the start-up cost, the rest reuse.
 *
 * <pre>
 * &#64;SpringBootTest
 * &#64;WithTestcontainers
 * class MyIntegrationTest { ... }
 * </pre>
 *
 * <p>callers: 0 — will become live with the first @SpringBootTest that
 * needs a real PG / Redis / Kafka / MinIO.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(WithTestcontainers.PropertyBinder.class)
public @interface WithTestcontainers {

    class PropertyBinder {
        @DynamicPropertySource
        static void bind(DynamicPropertyRegistry r) {
            r.add("spring.datasource.url",      SharedContainers.POSTGRES::getJdbcUrl);
            r.add("spring.datasource.username", SharedContainers.POSTGRES::getUsername);
            r.add("spring.datasource.password", SharedContainers.POSTGRES::getPassword);

            r.add("spring.data.redis.host", SharedContainers.REDIS::getHost);
            r.add("spring.data.redis.port", () -> SharedContainers.REDIS.getMappedPort(6379));

            r.add("spring.kafka.bootstrap-servers", SharedContainers.KAFKA::getBootstrapServers);

            r.add("hrms.storage.endpoint",   SharedContainers.MINIO::getS3URL);
            r.add("hrms.storage.access-key", SharedContainers.MINIO::getUserName);
            r.add("hrms.storage.secret-key", SharedContainers.MINIO::getPassword);
        }
    }
}

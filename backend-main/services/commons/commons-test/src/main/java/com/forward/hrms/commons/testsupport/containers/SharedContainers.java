package com.forward.hrms.commons.testsupport.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Testcontainer instances shared across tests in a JVM. Started
 * lazily on first use; reused for every subsequent test class. Tests
 * register their containers via {@code @DynamicPropertySource} (see
 * {@link WithTestcontainers}).
 *
 * <p>callers: 0 — paired with {@link WithTestcontainers}; goes live with
 * it.
 */
public final class SharedContainers {

    private SharedContainers() {}

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("fshrms")
            .withUsername("postgres")
            .withPassword("postgres")
            .withReuse(true);

    public static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    public static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka:4.0.0")
                    .asCompatibleSubstituteFor("confluentinc/cp-kafka"))
            .withReuse(true);

    public static final MinIOContainer MINIO = new MinIOContainer(
            DockerImageName.parse("minio/minio:latest"))
            .withUserName("minioadmin")
            .withPassword("minioadmin")
            .withReuse(true);

    static {
        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
        MINIO.start();
    }
}

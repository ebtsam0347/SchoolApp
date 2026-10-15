package com.forward.hrms.commons.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(MinioClient.class)
@ConditionalOnProperty(prefix = "hrms.storage", name = "endpoint")
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MinioClient minioClient(StorageProperties props) {
        return MinioClient.builder()
                .endpoint(props.endpoint())
                .credentials(props.accessKey(), props.secretKey())
                .region(props.region())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectStorage objectStorage(MinioClient client, StorageProperties props) {
        return new ObjectStorage(client, props);
    }

    @Bean
    public BucketEnsurer bucketEnsurer(MinioClient client, StorageProperties props) {
        return new BucketEnsurer(client, props);
    }

    @Slf4j
    @RequiredArgsConstructor
    static class BucketEnsurer {
        private final MinioClient client;
        private final StorageProperties props;

        @PostConstruct
        void ensure() {
            for (String bucket : props.ensureBuckets()) {
                try {
                    boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                    if (!exists) {
                        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                        log.info("Created MinIO bucket {}", bucket);
                    }
                } catch (Exception e) {
                    log.warn("Could not ensure bucket {}: {}", bucket, e.getMessage());
                }
            }
        }
    }
}

package com.forward.hrms.commons.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("hrms.storage")
public record StorageProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String region,
        List<String> ensureBuckets,
        Duration defaultUrlTtl
) {
    public StorageProperties {
        if (region == null || region.isBlank()) region = "us-east-1";
        if (ensureBuckets == null) ensureBuckets = List.of();
        if (defaultUrlTtl == null) defaultUrlTtl = Duration.ofMinutes(10);
    }
}

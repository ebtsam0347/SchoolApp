package com.forward.hrms.commons.web.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hrms.openapi")
public record OpenApiProperties(
        String title,
        String description,
        String version,
        String contactName,
        String contactEmail,
        String licenseName,
        String licenseUrl,
        String serverUrl,
        String serverDescription,
        boolean enabled,
        Security security
) {

    public OpenApiProperties {
        if (version == null || version.isBlank()) version = "0.0.1-SNAPSHOT";
        if (licenseName == null || licenseName.isBlank()) licenseName = "Proprietary — The Spirit of Islam School";
        if (security == null) security = new Security(true, "Authorization", "bearer", "JWT");
    }

    public record Security(
            boolean enabled,
            String name,
            String scheme,
            String bearerFormat
    ) {
        public Security {
            if (name == null || name.isBlank()) name = "Authorization";
            if (scheme == null || scheme.isBlank()) scheme = "bearer";
            if (bearerFormat == null || bearerFormat.isBlank()) bearerFormat = "JWT";
        }
    }
}

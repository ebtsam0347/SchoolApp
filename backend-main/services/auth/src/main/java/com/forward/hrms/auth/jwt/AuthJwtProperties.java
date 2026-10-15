package com.forward.hrms.auth.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;


@ConfigurationProperties("hrms.auth.jwt")
public record AuthJwtProperties(
        String issuer,
        String audience,
        String privateKey,
        Resource privateKeyLocation,
        String publicKey,
        Resource publicKeyLocation,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
    public AuthJwtProperties {
        if (issuer == null || issuer.isBlank()) issuer = "fshrms-auth";
        if (audience == null || audience.isBlank()) audience = "fshrms";
        if (accessTokenTtl == null) accessTokenTtl = Duration.ofMinutes(15);
        if (refreshTokenTtl == null) refreshTokenTtl = Duration.ofDays(7);
    }
}

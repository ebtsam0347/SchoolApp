package com.forward.hrms.commons.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * JWT verification config. Services either inline the PEM-encoded RSA
 * public key in {@code hrms.jwt.public-key} or point at a resource via
 * {@code hrms.jwt.public-key-location}.
 */
@ConfigurationProperties("hrms.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        String publicKey,
        Resource publicKeyLocation
) {}

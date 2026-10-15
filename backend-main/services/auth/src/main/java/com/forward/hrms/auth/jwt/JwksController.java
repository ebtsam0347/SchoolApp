package com.forward.hrms.auth.jwt;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "JWKS", description = "Public RSA key in JWK Set format. Used by other services to verify JWTs.")
@RestController
@RequestMapping("/auth/.well-known")
@RequiredArgsConstructor
@SecurityRequirements // public — no bearer required
public class JwksController {

    private final AuthKeys keys;

    @Operation(summary = "JSON Web Key Set",
            description = "Returns the current RS256 signing key. Other services cache and rotate from here.")
    @GetMapping("/jwks.json")
    public Map<String, Object> jwks() {
        RSAPublicKey k = keys.publicKey();
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keys.keyId());
        jwk.put("n", base64UrlUnsigned(k.getModulus().toByteArray()));
        jwk.put("e", base64UrlUnsigned(k.getPublicExponent().toByteArray()));
        return Map.of("keys", List.of(jwk));
    }

    private static String base64UrlUnsigned(byte[] bytes) {
        int start = 0;
        if (bytes.length > 1 && bytes[0] == 0) start = 1;
        byte[] trimmed = new byte[bytes.length - start];
        System.arraycopy(bytes, start, trimmed, 0, trimmed.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(trimmed);
    }
}

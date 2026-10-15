package com.forward.hrms.auth.jwt;

import com.forward.hrms.auth.entity.User;

import io.jsonwebtoken.Jwts;

import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class JwtIssuer {

    private final AuthKeys keys;
    private final AuthJwtProperties props;

    public Issued issueAccessToken(User user, long sessionId) {
        return issue(user, props.accessTokenTtl(), sessionId, false);
    }

    /**
     * Long-lived service-account token. No session row is created; the
     * token carries {@code sid: 0} and {@code tokenType: "service"} so it
     * is distinguishable from a login JWT in audit/diagnostics. The
     * resource-server JWT filter validates signature + exp + claims only
     * (see commons-web JwtAuthenticationFilter), so it accepts these
     * exactly like a login token. Rotation is admin-driven: re-issue,
     * re-seal, restart.
     */
    public Issued issueServiceToken(User user, Duration ttl) {
        return issue(user, ttl, 0L, true);
    }

    private Issued issue(User user, Duration ttl, long sessionId, boolean serviceAccount) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        String jti = UUID.randomUUID().toString();

        List<String> roles = user.getRoles().stream()
                .map(r -> r.getCode())
                .sorted()
                .toList();
        Set<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode())
                .collect(Collectors.toUnmodifiableSet());

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("username", user.getUsername());
        claims.put("name", user.getFullName());
        claims.put("roles", roles);
        claims.put("permissions", permissions);
        claims.put("sid", sessionId);
        if (serviceAccount) claims.put("tokenType", "service");
        if (!serviceAccount && user.isMustChangePw()) claims.put("pwChangeRequired", true);

        String token = Jwts.builder()
                .header().keyId(keys.keyId()).and()
                .issuer(props.issuer())
                .audience().add(props.audience()).and()
                .subject(String.valueOf(user.getId()))
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(claims)
                .signWith(keys.privateKey(), Jwts.SIG.RS256)
                .compact();
        return new Issued(token, exp, jti);
    }

    public record Issued(String token, Instant expiresAt, String jti) {}
}

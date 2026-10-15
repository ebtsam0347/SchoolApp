package com.forward.hrms.commons.web.autoconfig;

import com.forward.hrms.commons.web.security.JwtAuthenticationFilter;
import com.forward.hrms.commons.web.security.JwtProperties;
import com.forward.hrms.commons.web.security.RequirePermissionAspect;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.util.Arrays;
import java.util.Set;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Wires JWT validation for services that DO parse tokens themselves.
 * In the FSHRMS topology that is exactly one service: the api-gateway.
 * Every other service runs in header-identity mode and trusts the
 * {@code X-User-*} headers the gateway injects (see
 * {@link HeaderIdentityAutoConfiguration}).
 *
 * <p>Activate with {@code hrms.security.identity-source=jwt} — only the
 * gateway sets this.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(JwtParser.class)
@EnableConfigurationProperties(JwtProperties.class)
@ConditionalOnProperty(prefix = "hrms.security", name = "identity-source", havingValue = "jwt")
public class JwtSecurityAutoConfiguration {

    private static final Set<String> DEV_PROFILES = Set.of("dev", "local", "test", "default");

    @Bean
    @ConditionalOnMissingBean
    public JwtParser jwtParser(JwtProperties props, Environment env) throws Exception {
        rejectBundledDevKeyOutsideDev(props, env);
        String pem = readPem(props);
        if (pem == null || pem.isBlank()) {
            // Outside dev profiles, having no key configured is also a fatal
            // misconfiguration — the gateway would silently accept any
            // unsigned token. Inside dev profiles we keep the legacy warning.
            if (!isDevProfile(env)) {
                throw new IllegalStateException(
                        "hrms.jwt.public-key / public-key-location is not set; refusing to start "
                                + "with JWT validation disabled outside dev profiles. Set "
                                + "HRMS_JWT_PUBLIC_KEY_LOCATION or hrms.jwt.public-key.");
            }
            log.warn("hrms.jwt.public-key / public-key-location not set — JWT validation disabled.");
            return Jwts.parser().build(); // useless until services configure a key
        }
        PublicKey key = parsePublicKey(pem);
        // Auth signs tokens with a `kid` header (see JwtIssuer). JJWT 0.12 requires
        // a keyLocator when the JWS carries a kid; configuring only verifyWith(key)
        // fails with "unable to locate signature verification key". The locator
        // returns the same key for every kid because we run a single dev keypair —
        // when we add key rotation in UAT/prod, swap this for a kid → Key map.
        Locator<java.security.Key> locator = header -> key;
        var b = Jwts.parser().keyLocator(locator);
        if (props.issuer() != null) b.requireIssuer(props.issuer());
        if (props.audience() != null) b.requireAudience(props.audience());
        return b.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtParser parser) {
        return new JwtAuthenticationFilter(parser);
    }

    /**
     * Disable Spring Boot's outer servlet registration. The filter MUST
     * run inside the Spring Security chain (added via
     * {@code HttpSecurity.addFilterAfter} in the gateway's
     * {@code SecurityConfig}); registering it at the servlet level too
     * would cause the chain's {@code SecurityContextHolderFilter} to
     * overwrite the context the filter just populated, leaving the
     * downstream filters with null Authentication.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthFilterRegistration(JwtAuthenticationFilter f) {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean
    public RequirePermissionAspect requirePermissionAspect() {
        return new RequirePermissionAspect();
    }

    private static String readPem(JwtProperties props) throws Exception {
        if (props.publicKey() != null && !props.publicKey().isBlank()) return props.publicKey();
        Resource r = props.publicKeyLocation();
        if (r != null && r.exists()) {
            return new String(StreamUtils.copyToByteArray(r.getInputStream()), StandardCharsets.UTF_8);
        }
        return null;
    }

    /**
     * Refuse to boot in non-dev profiles when the only PEM available is
     * the bundled {@code classpath:dev-keys/...} resource. The repo ships
     * a dev keypair so local boot just works; if it leaks into UAT/prod
     * via an unset {@code HRMS_JWT_PUBLIC_KEY_LOCATION}, anyone with the
     * repo can mint tokens. Fail-fast at startup instead.
     */
    private static void rejectBundledDevKeyOutsideDev(JwtProperties props, Environment env) {
        if (props.publicKey() != null && !props.publicKey().isBlank()) return;
        Resource r = props.publicKeyLocation();
        if (r == null) return;
        String desc = r.getDescription();
        if (desc == null) return;
        if (!desc.contains("dev-keys/")) return;
        if (isDevProfile(env)) return;
        throw new IllegalStateException(
                "hrms.jwt.public-key-location resolves to the bundled dev key (" + desc
                        + ") but no dev/local/test profile is active. Set "
                        + "HRMS_JWT_PUBLIC_KEY_LOCATION or hrms.jwt.public-key to the "
                        + "deployment's signing key.");
    }

    private static boolean isDevProfile(Environment env) {
        String[] active = env.getActiveProfiles();
        if (active.length == 0) return true; // Spring's "default" profile when nothing set
        return Arrays.stream(active).anyMatch(DEV_PROFILES::contains);
    }

    private static PublicKey parsePublicKey(String pem) throws Exception {
        String body = pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s+", "");
        byte[] bytes = Base64.getDecoder().decode(body);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
    }
}

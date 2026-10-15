package com.forward.hrms.gateway.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.forward.hrms.commons.web.security.JwtAuthenticationFilter;
import com.forward.hrms.gateway.ratelimit.AuthRateLimitFilter;
import com.forward.hrms.gateway.ratelimit.PerUserRateLimitFilter;
import com.forward.hrms.gateway.ratelimit.RateLimitProperties;
import com.forward.hrms.gateway.ratelimit.SearchRateLimitFilter;
import com.forward.hrms.gateway.ratelimit.SearchRateLimitProperties;
import com.forward.hrms.gateway.security.ClientInfoForwardingFilter;
import com.forward.hrms.gateway.security.IdentityHeaderInjectionFilter;
import com.forward.hrms.gateway.security.IdentityHeaderStripFilter;


@Configuration
@EnableConfigurationProperties({CorsProperties.class, SearchRateLimitProperties.class, RateLimitProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           @Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
                                           ClientInfoForwardingFilter forward,
                                           IdentityHeaderStripFilter strip,
                                           JwtAuthenticationFilter jwt,
                                           IdentityHeaderInjectionFilter inject,
                                           AuthRateLimitFilter authLimit,
                                           SearchRateLimitFilter searchLimit,
                                           PerUserRateLimitFilter perUserLimit,
                                           org.springframework.core.env.Environment env) throws Exception {

        boolean swaggerPublic = env.acceptsProfiles(
                org.springframework.core.env.Profiles.of("dev", "local", "default"));
        http
                .csrf(c -> c.disable())
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers(HttpMethod.POST, "/auth/login", "/auth/refresh", "/auth/logout").permitAll()
                            .requestMatchers("/auth/.well-known/**").permitAll()
                            .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll();
                    if (swaggerPublic) {
                        a.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui.html", "/swagger-ui/**", "/swagger-resources/**", "/webjars/**").permitAll();
                    } else {
                        a.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui.html", "/swagger-ui/**", "/swagger-resources/**", "/webjars/**").authenticated();
                    }
                    a.anyRequest().authenticated();
                })

                .addFilterBefore(forward, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(strip, ClientInfoForwardingFilter.class)
                .addFilterAfter(authLimit, IdentityHeaderStripFilter.class)
                .addFilterAfter(jwt, AuthRateLimitFilter.class)
                .addFilterAfter(inject, JwtAuthenticationFilter.class)
                .addFilterAfter(perUserLimit, IdentityHeaderInjectionFilter.class)
                .addFilterAfter(searchLimit, PerUserRateLimitFilter.class);
        return http.build();
    }

    /**
     * CORS allowlist driven by {@link CorsProperties}. Spring Security's
     * {@code .cors(c -> c.configurationSource(...))} wires this in;
     * preflight {@code OPTIONS} requests are auto-permitted.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.allowedOrigins());
        if (!props.allowedOriginPatterns().isEmpty()) {
            cfg.setAllowedOriginPatterns(props.allowedOriginPatterns());
        }
        cfg.setAllowedMethods(props.allowedMethods());
        cfg.setAllowedHeaders(props.allowedHeaders());
        cfg.setExposedHeaders(props.exposedHeaders());
        cfg.setAllowCredentials(props.allowCredentials());
        cfg.setMaxAge(props.maxAgeSeconds());

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

    @Bean
    public ClientInfoForwardingFilter clientInfoForwardingFilter() {
        return new ClientInfoForwardingFilter();
    }

    @Bean
    public IdentityHeaderStripFilter identityHeaderStripFilter() {
        return new IdentityHeaderStripFilter();
    }

    @Bean
    public IdentityHeaderInjectionFilter identityHeaderInjectionFilter() {
        return new IdentityHeaderInjectionFilter();
    }

    @Bean
    public SearchRateLimitFilter searchRateLimitFilter(SearchRateLimitProperties props) {
        return new SearchRateLimitFilter(props);
    }

    @Bean
    public AuthRateLimitFilter authRateLimitFilter(RateLimitProperties props) {
        return new AuthRateLimitFilter(props);
    }

    @Bean
    public PerUserRateLimitFilter perUserRateLimitFilter(RateLimitProperties props) {
        return new PerUserRateLimitFilter(props);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(JwtAuthenticationFilter f) {
        return disabled(f);
    }

    @Bean
    public FilterRegistrationBean<IdentityHeaderStripFilter> identityHeaderStripFilterRegistration(IdentityHeaderStripFilter f) {
        return disabled(f);
    }

    @Bean
    public FilterRegistrationBean<IdentityHeaderInjectionFilter> identityHeaderInjectionFilterRegistration(IdentityHeaderInjectionFilter f) {
        return disabled(f);
    }

    @Bean
    public FilterRegistrationBean<ClientInfoForwardingFilter> clientInfoForwardingFilterRegistration(ClientInfoForwardingFilter f) {
        return disabled(f);
    }

    @Bean
    public FilterRegistrationBean<SearchRateLimitFilter> searchRateLimitFilterRegistration(SearchRateLimitFilter f) {
        return disabled(f);
    }

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration(AuthRateLimitFilter f) {
        return disabled(f);
    }

    @Bean
    public FilterRegistrationBean<PerUserRateLimitFilter> perUserRateLimitFilterRegistration(PerUserRateLimitFilter f) {
        return disabled(f);
    }

    private static <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disabled(T f) {
        FilterRegistrationBean<T> reg = new FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }
}

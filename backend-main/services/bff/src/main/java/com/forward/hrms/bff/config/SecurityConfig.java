package com.forward.hrms.bff.config;

import com.forward.hrms.commons.web.security.HeaderIdentityFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * BFF runs in header-identity mode behind the gateway. Identity comes
 * from {@code X-User-*} headers the gateway injects after validating the
 * caller's JWT — the same wiring every other downstream service uses.
 *
 * <p>The filter has to live inside Spring Security's chain — the
 * stateless {@code SecurityContextHolderFilter} would otherwise wipe the
 * context the filter just populated. Mirrors {@code auth/SecurityConfig}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, HeaderIdentityFilter identity) throws Exception {
        http
                .csrf(c -> c.disable())
                .cors(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui.html", "/swagger-ui/**", "/swagger-resources/**", "/webjars/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(identity, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

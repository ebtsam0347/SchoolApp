package com.forward.hrms.auth.config;

import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.forward.hrms.auth.jwt.AuthJwtProperties;
import com.forward.hrms.auth.jwt.AuthKeys;
import com.forward.hrms.auth.jwt.JwtIssuer;


@Configuration
@EnableConfigurationProperties({AuthJwtProperties.class, AuthDownstreamProperties.class})
public class AuthCryptoConfig {

    @Bean
    public AuthKeys authKeys(AuthJwtProperties props) {
        return new AuthKeys(props);
    }

    @Bean
    public JwtIssuer jwtIssuer(AuthKeys keys, AuthJwtProperties props) {
        return new JwtIssuer(keys, props);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder(
                "bcrypt", Map.of("bcrypt", bcrypt));
        delegating.setDefaultPasswordEncoderForMatches(bcrypt);
        return delegating;
    }
}

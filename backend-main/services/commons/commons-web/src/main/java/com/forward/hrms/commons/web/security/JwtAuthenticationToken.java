package com.forward.hrms.commons.web.security;

import com.forward.hrms.commons.core.context.CurrentUser;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final CurrentUser principal;

    public JwtAuthenticationToken(CurrentUser principal) {
        super(principal.roles().stream()
                .<org.springframework.security.core.GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList());
        this.principal = principal;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() { return ""; }

    @Override
    public CurrentUser getPrincipal() { return principal; }

    public List<String> permissions() { return List.copyOf(principal.permissions()); }
}

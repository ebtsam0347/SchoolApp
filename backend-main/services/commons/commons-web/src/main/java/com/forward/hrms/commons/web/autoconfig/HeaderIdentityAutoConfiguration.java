package com.forward.hrms.commons.web.autoconfig;

import com.forward.hrms.commons.web.security.HeaderIdentityFilter;
import com.forward.hrms.commons.web.security.RequirePermissionAspect;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the {@code X-User-*} header → {@code CurrentUser} filter for
 * downstream services that sit behind the gateway. The gateway itself
 * uses {@link JwtSecurityAutoConfiguration} instead.
 *
 * <p>Active by default (every service is "downstream"). Switch to JWT
 * mode with {@code hrms.security.identity-source=jwt} on the gateway.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "hrms.security", name = "identity-source",
        havingValue = "header", matchIfMissing = true)
public class HeaderIdentityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HeaderIdentityFilter headerIdentityFilter() {
        return new HeaderIdentityFilter();
    }

    /**
     * Each service's SecurityConfig adds the filter inside the security
     * chain (before {@code UsernamePasswordAuthenticationFilter}). Disable
     * Spring Boot's outer servlet registration so the filter doesn't run
     * twice — and the second run wouldn't be inside the security chain,
     * so the stateless {@code SecurityContextHolderFilter} would wipe it.
     */
    @Bean
    public FilterRegistrationBean<HeaderIdentityFilter> headerIdentityFilterRegistration(HeaderIdentityFilter f) {
        FilterRegistrationBean<HeaderIdentityFilter> reg = new FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean
    public RequirePermissionAspect requirePermissionAspect() {
        return new RequirePermissionAspect();
    }
}

package com.forward.hrms.gateway.config;

import com.forward.hrms.gateway.logging.GatewayRouteLoggingFilter;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayLoggingConfig {

    @Bean
    public FilterRegistrationBean<GatewayRouteLoggingFilter> gatewayRouteLoggingFilter() {
        FilterRegistrationBean<GatewayRouteLoggingFilter> reg =
                new FilterRegistrationBean<>(new GatewayRouteLoggingFilter());
        reg.setOrder(GatewayRouteLoggingFilter.ORDER);
        return reg;
    }
}

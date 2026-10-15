package com.forward.hrms.payroll.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PayrollProperties.class, PayrollDownstreamProperties.class,
        FairPriceShopProperties.class})
public class PayrollConfig {
}

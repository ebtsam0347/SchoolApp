package com.forward.hrms.commons.core.autoconfig;

import com.fasterxml.jackson.databind.Module;
import com.forward.hrms.commons.core.jackson.MoneyJacksonModule;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@AutoConfiguration
public class CoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Module moneyJacksonModule() {
        return new MoneyJacksonModule();
    }
}

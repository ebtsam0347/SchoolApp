package com.forward.hrms.commons.reports;

import net.sf.jasperreports.engine.JasperReport;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(JasperReport.class)
public class CommonsReportsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JasperRenderer jasperRenderer() {
        return new JasperRenderer();
    }
}

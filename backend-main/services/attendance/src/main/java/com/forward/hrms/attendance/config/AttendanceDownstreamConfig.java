package com.forward.hrms.attendance.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link AttendanceDownstreamProperties} so its {@code @Validated}
 * @URL constraints fire at startup.
 */
@Configuration
@EnableConfigurationProperties(AttendanceDownstreamProperties.class)
public class AttendanceDownstreamConfig {}

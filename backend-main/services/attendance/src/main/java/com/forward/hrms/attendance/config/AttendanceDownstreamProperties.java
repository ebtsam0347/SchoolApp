package com.forward.hrms.attendance.config;

import com.forward.hrms.commons.web.client.DownstreamHttpProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Downstream service URLs attendance talks to. Embeds the shared
 * {@link DownstreamHttpProperties} shape so each base URL is
 * {@code @URL @NotBlank}-validated at startup.
 */
@ConfigurationProperties("hrms")
@Validated
public record AttendanceDownstreamProperties(
        @NotNull @Valid DownstreamHttpProperties employee
) {}

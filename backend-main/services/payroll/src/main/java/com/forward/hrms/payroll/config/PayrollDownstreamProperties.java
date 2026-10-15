package com.forward.hrms.payroll.config;

import com.forward.hrms.commons.web.client.DownstreamHttpProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Downstream service URLs payroll-service talks to. Embeds the shared
 * {@link DownstreamHttpProperties} shape so each base URL is
 * {@code @URL @NotBlank}-validated at startup.
 */
@ConfigurationProperties("hrms")
@Validated
public record PayrollDownstreamProperties(
        @NotNull @Valid DownstreamHttpProperties employee,
        @NotNull @Valid DownstreamHttpProperties attendance
) {}


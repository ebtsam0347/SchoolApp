package com.forward.hrms.bff.config;

import com.forward.hrms.commons.web.client.DownstreamHttpProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Base URLs for the downstream services the BFF fans out to. Each entry
 * embeds the shared {@link DownstreamHttpProperties} shape so its
 * {@code @URL @NotBlank} constraints fire at startup.
 */
@ConfigurationProperties(prefix = "hrms.bff.downstream")
@Validated
public record DownstreamProperties(
        @NotNull @Valid DownstreamHttpProperties auth,
        @NotNull @Valid DownstreamHttpProperties audit,
        @NotNull @Valid DownstreamHttpProperties employee,
        @NotNull @Valid DownstreamHttpProperties attendance,
        @NotNull @Valid DownstreamHttpProperties payroll,
        @NotNull @Valid DownstreamHttpProperties chat
) {}

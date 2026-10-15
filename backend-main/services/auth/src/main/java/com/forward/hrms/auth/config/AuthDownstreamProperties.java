package com.forward.hrms.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.forward.hrms.commons.web.client.DownstreamHttpProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;


@ConfigurationProperties("hrms")
@Validated
public record AuthDownstreamProperties(
        @NotNull @Valid DownstreamHttpProperties audit
) {}

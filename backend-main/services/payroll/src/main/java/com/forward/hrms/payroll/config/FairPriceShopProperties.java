package com.forward.hrms.payroll.config;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Read-only connection to the Fair Price Shop SQL Server. Payroll reads
 * {@code dbo.View_FairPrice_Out} only — never the shop's tables.
 * Username / password may be blank locally; the first query then fails
 * with {@code FAIR_PRICE_SHOP_CHECK_FAILED} instead of blocking startup.
 */
@ConfigurationProperties("hrms.fair-price-shop")
@Validated
public record FairPriceShopProperties(
        @NotBlank String jdbcUrl,
        String username,
        String password
) {}

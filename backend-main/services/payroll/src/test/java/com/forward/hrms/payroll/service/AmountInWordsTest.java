package com.forward.hrms.payroll.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmountInWordsTest {

    @Test
    void usesLakhAndCrore() {
        assertThat(AmountInWords.of(new BigDecimal("103500.00")))
                .isEqualTo("Rupees One Lakh Three Thousand Five Hundred Only");
        assertThat(AmountInWords.of(new BigDecimal("12345678.00")))
                .isEqualTo("Rupees One Crore Twenty Three Lakh Forty Five Thousand Six Hundred Seventy Eight Only");
    }

    @Test
    void spellsPaisaAndZero() {
        assertThat(AmountInWords.of(new BigDecimal("50000.50")))
                .isEqualTo("Rupees Fifty Thousand and Fifty Paisa Only");
        assertThat(AmountInWords.of(BigDecimal.ZERO)).isEqualTo("Rupees Zero Only");
        assertThat(AmountInWords.of(null)).isEqualTo("-");
    }
}

package com.forward.hrms.payroll.client;

import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.payroll.config.FairPriceShopProperties;
import com.forward.hrms.payroll.enums.PayrollErrorCode;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Correctness test for Audit Finding 3 (fixed). {@link FairPriceShopClient}
 * throws {@link UpstreamError} when the shop's SQL Server can't be read
 * instead of returning {@link BigDecimal#ZERO} — indistinguishable from
 * "employee genuinely owes nothing" — so a payroll run's compute step
 * fails for the saga to retry and settlement creation (one transaction)
 * rolls back for HR to retry, rather than silently dropping the debt.
 */
class FairPriceShopClientPolicyVerificationTest {

    // Port 1 on loopback: reserved, nothing listens there — an immediate
    // connection refusal, not a slow timeout.
    private static final FairPriceShopProperties UNREACHABLE = new FairPriceShopProperties(
            "jdbc:sqlserver://127.0.0.1:1;databaseName=HRMS;encrypt=false;loginTimeout=2", "u", "p");

    @Test
    void amountsForMonth_unreachableServer_throwsUpstreamError_notEmptyMap() {
        FairPriceShopClient client = new FairPriceShopClient(UNREACHABLE);
        try {
            assertThatThrownBy(() -> client.amountsForMonth(YearMonth.of(2026, 1)))
                    .isInstanceOf(UpstreamError.class)
                    .extracting(ex -> ((UpstreamError) ex).code())
                    .isEqualTo(PayrollErrorCode.FAIR_PRICE_SHOP_CHECK_FAILED);
        } finally {
            client.destroy();
        }
    }

    @Test
    void outstandingAmount_unreachableServer_throwsUpstreamError_notSilentZero() {
        FairPriceShopClient client = new FairPriceShopClient(UNREACHABLE);
        try {
            assertThatThrownBy(() -> client.outstandingAmount("13411", LocalDate.of(2026, 1, 20)))
                    .isInstanceOf(UpstreamError.class)
                    .extracting(ex -> ((UpstreamError) ex).code())
                    .isEqualTo(PayrollErrorCode.FAIR_PRICE_SHOP_CHECK_FAILED);
        } finally {
            client.destroy();
        }
    }

    @Test
    void outstandingAmount_blankEmployeeCode_returnsZero_withoutQuerying() {
        FairPriceShopClient client = new FairPriceShopClient(UNREACHABLE);
        try {
            assertThat(client.outstandingAmount("", LocalDate.of(2026, 1, 20)))
                    .isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(client.outstandingAmount(null, LocalDate.of(2026, 1, 20)))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        } finally {
            client.destroy();
        }
    }
}
